package industries.hannah.pixelblaze

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import java.io.InputStream
import java.io.SequenceInputStream
import java.lang.reflect.Type
import java.time.Instant
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayDeque
import kotlin.collections.set
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.toJavaDuration

internal typealias SaveAfterID = UUID

class WebsocketPixelblaze internal constructor(
    private val address: String,
    private val port: Int,
    private val watchers: ConcurrentMap<Inbound<InboundMessage>, ConcurrentLinkedQueue<Watcher<InboundMessage>>>,
    private val textParsers: PriorityBlockingQueue<TextParser<*>>,
    private val binaryParsers: ConcurrentMap<InboundBinary<*>, SerialParser<*>>,
    private val connectionWatcher: (ConnectionEvent, String?, Throwable?) -> Unit,
    private val config: PixelblazeConfig,
    private val gson: Gson,
    private val httpClient: HttpClient,
    private val errorLog: (String?, Throwable?) -> Unit,
    private val infoLog: (String) -> Unit,
    private val debugLog: (String) -> Unit,
    ioLoopDispatcher: CoroutineDispatcher,
    saveAfterDispatcher: CoroutineDispatcher,
    cronDispatcher: CoroutineDispatcher
) : Pixelblaze {
    private val outboundQueue: BlockingQueue<OutboundMessageWrapper<*>> =
        ArrayBlockingQueue(config.outboundQueueDepth)
    private val saveAfterJobs: ConcurrentMap<SaveAfterID, Job> = ConcurrentHashMap()
    private val cronMessages: ConcurrentMap<ScheduledMessageId, Job> = ConcurrentHashMap()

    private val ioLoopDispatcher = CoroutineScope(ioLoopDispatcher)
    private val saveAfterDispatcher = CoroutineScope(saveAfterDispatcher)
    private val cronDispatcher = CoroutineScope(cronDispatcher)

    private val binaryFrames: ArrayDeque<InputStream> = ArrayDeque(config.inboundBufferQueueDepth)
    private var activeMessageType: InboundBinary<*>? = null

    internal data class TextParser<Message : InboundMessage>(
        val id: ParserID,
        val priority: Int,
        val type: InboundText<Message>,
        val parseFn: (Gson, String) -> Message?
    )

    internal data class SerialParser<Message : InboundMessage>(
        val id: ParserID,
        val type: InboundBinary<Message>,
        val parseFn: (InputStream) -> Message?
    )

    internal data class OutboundMessageWrapper<Message>(
        val type: Outbound<Message>,
        val message: Message
    )

    internal data class ScheduledMessage<Message>(
        val type: Outbound<Message>,
        val messageGenerator: () -> Message,
        val scheduleId: ScheduledMessageId,
        val runAt: Instant,
        val interval: Duration
    )

    internal data class Watcher<Message : InboundMessage>(
        val id: WatcherID,
        val handlerFn: (Message) -> Unit,
        val coroutineScope: CoroutineScope?
    )

    private val smallTaskCoroutines = CoroutineScope(Dispatchers.Default)
    private val connectionHandler: Job = CoroutineScope(Dispatchers.IO).async {
        var retry = 1u
        while (isActive) {
            try {
                if (watchConnection(this)) {
                    retry = 1u
                } else {
                    delay(config.sleepStrategyOnDisconnect(retry))
                    retry++
                }
            } catch (t: Throwable) {
                connectionWatcher(
                    ConnectionEvent.WatchThreadException,
                    "Unexpected error from connection handler, reconnecting",
                    t
                )
                delay(config.sleepStrategyOnDisconnect(retry))
            }
        }
    }

    override fun <Out, Wrapper : OutboundMessage<*, Out>> issueOutbound(
        type: Outbound<Wrapper>,
        msg: Wrapper
    ): Boolean {
        return outboundQueue.offer(OutboundMessageWrapper(type, msg))
    }

    override suspend fun <Out, Wrapper : OutboundMessage<*, Out>, Resp : InboundMessage> issueOutboundAndWait(
        outboundType: Outbound<Wrapper>,
        msg: Wrapper,
        inboundType: Inbound<Resp>,
        maxWait: Duration
    ): Resp? {
        val guard = AtomicBoolean(false)

        return suspendCoroutineWithTimeout(maxWait) { continuation ->
            var id: WatcherID? = null
            val watchFn: (Resp) -> Unit = { resp: Resp ->
                if (!guard.getAndSet(true)) { //Check if this watcher has been invoked, bail if it has
                    continuation.resume(resp)
                    removeWatcher(id!!)
                }
            }

            id = addWatcher(inboundType, watchFn)
            issueOutbound(outboundType, msg)
        }
    }

    private suspend inline fun <T> suspendCoroutineWithTimeout(
        timeout: Duration,
        crossinline block: (Continuation<T>) -> Unit
    ) = withTimeout(timeout) {
        suspendCancellableCoroutine(block = block)
    }

    override fun <Out, Wrapper : OutboundMessage<*, Out>> repeatOutbound(
        type: Outbound<Wrapper>,
        msgGenerator: () -> Wrapper,
        interval: Duration,
        initialDelay: Duration
    ): ScheduledMessageId {
        val scheduleId = UUID.randomUUID()

        cronMessages[scheduleId] = smallTaskCoroutines.async {
            delay(initialDelay)
            while (isActive) {
                issueOutbound(type, msgGenerator())
                delay(interval)
            }
        }

        return scheduleId
    }

    override fun cancelRepeatedOutbound(id: ScheduledMessageId): Boolean {
        cronMessages.remove(id)?.cancel() ?: return false
        return true
    }

    override fun <T, Out, Wrapper : OutboundMessage<*, Out>> saveAfter(
        type: Outbound<Wrapper>,
        wrapperBuilder: (T, Boolean) -> Wrapper,
        saveAfter: Duration
    ): SendChannel<T> {
        val jobId = UUID.randomUUID()
        val channel = Channel<T>(config.saveAfterWriteBufferSize)
        saveAfterJobs[jobId] = smallTaskCoroutines.async {
            var last: T? = null
            var lastSaved: T? = null
            var lastSaveAt: Instant? = null

            try {
                while (isActive && !channel.isClosedForReceive) {
                    withTimeoutOrNull(saveAfter) {
                        val receiveResult = channel.receiveCatching()
                        if (receiveResult.isSuccess) {
                            receiveResult.getOrThrow()
                        } else {
                            // If the channel was closed or the read timed out, either way we want to dispatch
                            // a save request with the last value, returning null here should do that
                            null
                        }
                    }?.run {
                        last = this

                        //If it's been longer than saveAfter, save mid-stream
                        val save = if (lastSaveAt != null
                            && Instant.now().isAfter(
                                lastSaveAt!!.plus(saveAfter.toJavaDuration())
                            )
                        ) {
                            lastSaveAt = Instant.now()
                            lastSaved = this
                            true
                        } else {
                            false
                        }

                        val message = wrapperBuilder(this, save)
                        if (!issueOutbound(type, message)) {
                            errorLog("Enqueue of non-save request failed for saveAfter()", null)
                        }
                    } ?: run {
                        if (last != null && last != lastSaved) {
                            lastSaved = last
                            lastSaveAt = Instant.now()
                            val message = wrapperBuilder(last!!, true)
                            if (!issueOutbound(type, message)) {
                                errorLog("Enqueue of save request failed for saveAfter()", null)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                channel.close(t)
                errorLog("Exception on save after thread, closing channel and bailing", t)
            }

            saveAfterJobs.remove(jobId)
        }

        return channel
    }

    override fun <ParsedType : InboundMessage> addWatcher(
        type: Inbound<ParsedType>,
        handler: (ParsedType) -> Unit,
        coroutineScope: CoroutineScope?
    ): WatcherID {
        val watcherID = UUID.randomUUID()
        watchers.putIfAbsent(type as Inbound<InboundMessage>, ConcurrentLinkedQueue())
        watchers[type]!!.add(Watcher(watcherID, handler as (InboundMessage) -> Unit, coroutineScope))
        return watcherID
    }

    override fun removeWatcher(id: WatcherID): Boolean {
        return watchers.any { (_, list) -> list.removeIf { watcher -> watcher.id == id } }
    }

    override fun <ParsedType : InboundMessage> addTextParser(
        msgType: InboundText<ParsedType>,
        parserFn: (Gson, String) -> ParsedType?,
        priority: Int
    ): ParserID {
        val id = UUID.randomUUID()
        textParsers.add(TextParser(id, priority, msgType, parserFn))
        return id
    }

    override fun <ParsedType : InboundMessage> setBinaryParser(
        msgType: InboundBinary<ParsedType>,
        parserFn: (InputStream) -> ParsedType?
    ): ParserID {
        val id = UUID.randomUUID()
        binaryParsers[msgType] = SerialParser(id, msgType, parserFn)
        return id
    }

    override fun removeParser(id: ParserID): Boolean {
        var found = false
        binaryParsers.filterValues { parser -> parser.id == id }.forEach { parser ->
            found = true
            binaryParsers.remove(parser.key)
        }

        found = found || textParsers.removeIf { parser -> parser.id == id }

        return found
    }

    private suspend fun watchConnection(scope: CoroutineScope): Boolean {
        var unclosed = false
        httpClient.webSocket(method = HttpMethod.Get, host = address, port = port, path = "/") {
            while (scope.isActive) {
                var workDone = false
                val receiveResult: ChannelResult<Frame> = incoming.tryReceive()
                if (receiveResult.isClosed) {
                    if (unclosed) {
                        connectionWatcher(
                            ConnectionEvent.ClientDisconnect,
                            "Client disconnected, will attempt to reconnect",
                            null
                        )
                    }
                    errorLog("Connection closed, will retry", null)
                    break
                } else if (receiveResult.isSuccess) {
                    if (!unclosed) {
                        connectionWatcher(
                            ConnectionEvent.Connected,
                            "Client received first message on new connection",
                            null
                        )
                    }

                    readFrame(receiveResult.getOrThrow())?.run {
                        watchers[this.first]?.forEach { watcher ->
                            try {
                                if (watcher.coroutineScope == null) {
                                    watcher.handlerFn(this.second)
                                } else {
                                    val msg = this.second
                                    watcher.coroutineScope.launch {
                                        watcher.handlerFn(msg)
                                    }
                                }
                            } catch (t: Throwable) {
                                val msg = "Exception in watcher. Type ${this.first.javaClass}, id: ${watcher.id}"
                                connectionWatcher(ConnectionEvent.WatcherFailed, msg, t)
                            }
                        }
                    }

                    workDone = true //True even if we discarded the read, don't want to sleep
                    unclosed = true
                } else {
                    // else receiveResult.isFailure => nothing to read but mark conn as healthy
                    unclosed = true
                }

                outboundQueue.poll()?.run {
                    sendOutboundMessage(this as OutboundMessageWrapper<OutboundMessage<Any, *>>, outgoing)
                    workDone = true
                }

                if (!workDone) {
                    delay(config.sleepOnNothingToDo)
                }
            }
        }

        return unclosed
    }

    private fun readFrame(frame: Frame): Pair<Inbound<*>, InboundMessage>? {
        return when (frame) {
            is Frame.Text -> {
                for (parser in textParsers) {
                    val parsed = parser.parseFn(gson, frame.readText())
                    if (parsed != null) {
                        return Pair(parser.type, parsed)
                    }
                }
                null
            }

            is Frame.Binary -> {
                readBinaryFrame(frame)?.run {
                    val message = this
                    binaryParsers[message.first]?.run {
                        val parser = this
                        parser.parseFn(message.second)?.run {
                            Pair(parser.type, this)
                        }
                    }
                }
            }

            else -> null
        }
    }

    private fun readBinaryFrame(frame: Frame.Binary): Pair<InboundBinary<*>, InputStream>? {
        if (frame.data.isEmpty()) {
            return null
        } else {
            val typeFlag = frame.data[0]
            if (typeFlag >= 0) {
                if (typeFlag == InboundPreviewFrame.binaryFlag) { //Preview frames are never split
                    return if (binaryParsers.containsKey(InboundPreviewFrame)) {
                        Pair(InboundPreviewFrame, frame.readBytes().inputStream(1, frame.data.size))
                    } else {
                        null
                    }
                } else {
                    if (!binaryParsers.containsKey(InboundRawBinary<InboundMessage>(typeFlag))) {
                        return null
                    }

                    if (frame.data.size >= 2) {
                        return when (FramePosition.fromByte(frame.data[1])) {
                            FramePosition.First -> {
                                if (activeMessageType != null) {
                                    errorLog("Got new First frame, but we were already reading one", null)
                                    binaryFrames.clear()
                                }
                                activeMessageType = InboundRawBinary<InboundMessage>(typeFlag)
                                binaryFrames.add(frame.readBytes().inputStream(2, frame.data.size))
                                null
                            }

                            FramePosition.Middle -> {
                                if (activeMessageType?.binaryFlag != typeFlag) {
                                    binaryFrames.clear()
                                    activeMessageType = null
                                } else {
                                    binaryFrames.add(frame.readBytes().inputStream(2, frame.data.size))
                                }
                                null
                            }

                            FramePosition.Last -> {
                                if (activeMessageType?.binaryFlag == typeFlag) {
                                    val stream = frame.readBytes().inputStream(2, frame.data.size)
                                    val concatStream = SequenceInputStream(
                                        binaryFrames.fold(ByteArray(0).inputStream() as InputStream) { acc, streamPortion ->
                                            SequenceInputStream(
                                                acc,
                                                streamPortion
                                            )
                                        },
                                        stream
                                    )
                                    activeMessageType = null
                                    binaryFrames.clear()
                                    Pair(InboundRawBinary<InboundMessage>(typeFlag), concatStream)
                                } else {
                                    binaryFrames.clear()
                                    null
                                }
                            }

                            FramePosition.Only -> {
                                Pair(
                                    InboundRawBinary<InboundMessage>(typeFlag),
                                    frame.readBytes().inputStream(2, frame.data.size)
                                )
                            }

                            null -> null
                        }
                    } else {
                        return null
                    }
                }
            } else {
                return null
            }
        }
    }

    private suspend fun sendOutboundMessage(
        message: OutboundMessageWrapper<OutboundMessage<Any, *>>,
        outgoing: SendChannel<Frame>
    ) {
        val context: Any = when (message.type) {
            is OutboundText<*> -> gson
            is OutboundBinary<*> -> BinarySerializationContext(
                config.outboundFrameSize,
                message.type.binaryFlag,
                message.type.canBeSplit
            )
        }

        val frames = message.message.toFrames(context) ?: return
        for (frame in frames) {
            outgoing.send(frame)
        }
    }

    override fun close() {
        runBlocking {
            saveAfterJobs.values.forEach { job -> job.cancel() }
            cronMessages.values.forEach { job -> job.cancel() }

            connectionHandler.cancel()
            connectionHandler.join()
        }
    }

    class Builder(
    ) {
        private val watchers: ConcurrentMap<Inbound<InboundMessage>, ConcurrentLinkedQueue<Watcher<InboundMessage>>> =
            ConcurrentHashMap()
        private val textParsers: PriorityBlockingQueue<TextParser<*>> =
            PriorityBlockingQueue(16) { a, b -> a.priority - b.priority }
        private val binaryParsers: ConcurrentMap<InboundBinary<*>, SerialParser<*>> = ConcurrentHashMap()
        private val gsonBuilder: GsonBuilder = GsonBuilder()

        private var ioLoopDispatcher: CoroutineDispatcher = Dispatchers.IO
        private var saveAfterDispatcher: CoroutineDispatcher = Dispatchers.Default
        private var cronDispatcher: CoroutineDispatcher = Dispatchers.Default

        private var httpClient: HttpClient? = null
        private var port: Int? = null
        private var config: PixelblazeConfig? = null

        private var address: String? = null
        private var connectionWatcher: (ConnectionEvent, String?, Throwable?) -> Unit = { _, _, _ -> }
        private var errorLog: (String?, Throwable?) -> Unit = { _, _ -> }
        private var infoLog: (String) -> Unit = { _ -> }
        private var debugLog: (String) -> Unit = { _ -> }

        fun setPixelblazeIp(pixelblazeIp: String): Builder {
            this.address = pixelblazeIp
            return this
        }

        fun <ParsedType : InboundMessage> addWatcher(
            type: Inbound<ParsedType>,
            handler: (ParsedType) -> Unit
        ): Pair<WatcherID, Builder> = addWatcher(type, handler, null)

        fun <ParsedType : InboundMessage> addWatcher(
            type: Inbound<ParsedType>,
            handler: (ParsedType) -> Unit,
            coroutineScope: CoroutineScope?
        ): Pair<WatcherID, Builder> {
            val watcherID = UUID.randomUUID()
            watchers.putIfAbsent(type as Inbound<InboundMessage>, ConcurrentLinkedQueue())
            watchers[type]!!.add(Watcher(watcherID, handler as (InboundMessage) -> Unit, coroutineScope))
            return Pair(watcherID, this)
        }

        fun <Message : InboundMessage> setBinaryParser(
            type: InboundBinary<Message>,
            parseFn: (InputStream) -> Message?
        ): Pair<ParserID, Builder> {
            val parserID = UUID.randomUUID()
            binaryParsers[type] = SerialParser(parserID, type, parseFn)
            return Pair(parserID, this)
        }

        fun <Message : InboundMessage> addTextParser(
            priority: Int,
            type: InboundText<Message>,
            parseFn: (Gson, String) -> Message?
        ): Pair<ParserID, Builder> {
            val parserID = UUID.randomUUID()
            textParsers.add(TextParser(parserID, priority, type, parseFn))
            return Pair(parserID, this)
        }

        fun addGsonAdapter(type: Type, adapter: Any): Builder {
            gsonBuilder.registerTypeAdapter(type, adapter)
            return this
        }

        fun setHttpClient(httpClient: HttpClient): Builder {
            this.httpClient = httpClient
            return this
        }

        fun setPort(port: Int): Builder {
            this.port = port
            return this
        }

        fun setConfig(config: PixelblazeConfig): Builder {
            this.config = config
            return this
        }

        fun setConnectionWatcher(connectionWatcher: (ConnectionEvent, String?, Throwable?) -> Unit): Builder {
            this.connectionWatcher = connectionWatcher
            return this
        }

        fun setErrorLog(errorLog: (String?, Throwable?) -> Unit): Builder {
            this.errorLog = errorLog
            return this
        }

        fun setInfoLog(infoLog: (String) -> Unit): Builder {
            this.infoLog = infoLog
            return this
        }

        fun setDebugLog(debugLog: (String) -> Unit): Builder {
            this.debugLog = debugLog
            return this
        }

        fun setIoLoopDispatcher(dispatcher: CoroutineDispatcher): Builder {
            ioLoopDispatcher = dispatcher
            return this
        }

        fun setSaveAfterDispatcher(dispatcher: CoroutineDispatcher): Builder {
            saveAfterDispatcher = dispatcher
            return this
        }

        fun setRepeatedOutboundDispatcher(dispatcher: CoroutineDispatcher): Builder {
            cronDispatcher = dispatcher
            return this
        }

        fun addDefaultParsers(except: Set<Inbound<*>> = setOf()): Builder {
            return this.run {
                if (except.contains(InboundPreviewImage)) this
                else this.setBinaryParser(
                    InboundPreviewImage,
                    PreviewImage::fromBinary
                ).second
            }.run {
                if (except.contains(InboundPreviewFrame)) this
                else this.setBinaryParser(
                    InboundPreviewFrame,
                    PreviewFrame::fromBinary
                ).second
            }.run {
                if (except.contains(InboundProgramList)) this
                else this.setBinaryParser(
                    InboundProgramList,
                    ProgramList::fromBinary
                ).second
            }.run {
                if (except.contains(InboundExpanderChannels)) this
                else this.setBinaryParser(
                    InboundExpanderChannels,
                    ExpanderChannels::fromBinary
                ).second
            }.run {
                if (except.contains(InboundStats)) this
                else this.addTextParser(
                    1000,
                    InboundStats,
                    Stats::fromText
                ).second
            }.run {
                if (except.contains(InboundSequencerState)) this
                else this.addTextParser(
                    2000,
                    InboundSequencerState,
                    SequencerState::fromText
                ).second
            }.run {
                if (except.contains(InboundSettings)) this
                else this.addTextParser(
                    3000,
                    InboundSettings,
                    Settings::fromText
                ).second
            }.run {
                if (except.contains(InboundPeers)) this
                else this.addTextParser(
                    4000,
                    InboundPeers,
                    Peers::fromText
                ).second
            }.run {
                if (except.contains(InboundPlayist)) this
                else this.addTextParser(
                    5000,
                    InboundPlayist,
                    Playlist::fromText
                ).second
            }.run {
                if (except.contains(InboundPlaylistUpdate)) this
                else this.addTextParser(
                    6000,
                    InboundPlaylistUpdate,
                    PlaylistUpdate::fromText
                ).second
            }.run {
                if (except.contains(InboundAck)) this
                else this.addTextParser(
                    7000,
                    InboundAck,
                    Ack::fromText
                ).second
            }
        }

        fun build(): WebsocketPixelblaze {
            val httpClient = httpClient ?: HttpClient {
                install(WebSockets)
            }

            return WebsocketPixelblaze(
                watchers = watchers,
                textParsers = textParsers,
                binaryParsers = binaryParsers,
                gson = gsonBuilder.create(),
                httpClient = httpClient,
                address = address ?: throw RuntimeException("No pixelblaze IP specified!"),
                port = port ?: throw RuntimeException("No port specified"),
                config = config ?: throw RuntimeException("No config specified"),
                connectionWatcher = connectionWatcher,
                errorLog = errorLog,
                infoLog = infoLog,
                debugLog = debugLog,
                ioLoopDispatcher = ioLoopDispatcher,
                saveAfterDispatcher = saveAfterDispatcher,
                cronDispatcher = cronDispatcher
            )
        }
    }

    companion object {

        fun defaultBuilder(): Builder {
            return bareBuilder()
                .setPixelblazeIp("192.168.4.1")
                .setPort(81)
                .setConfig(PixelblazeConfig.default())
                .addDefaultParsers()
        }

        fun bareBuilder(): Builder {
            return Builder()
        }

    }
}