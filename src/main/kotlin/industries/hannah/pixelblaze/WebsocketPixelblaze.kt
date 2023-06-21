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
    private val binaryParsers: ConcurrentMap<Byte, SerialParser<*>>,
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
    private val outboundQueue: BlockingQueue<OutboundMessage<*, *>> =
        ArrayBlockingQueue(config.outboundQueueDepth)
    private val saveAfterJobs: ConcurrentMap<SaveAfterID, Job> = ConcurrentHashMap()
    private val cronMessages: ConcurrentMap<ScheduledMessageId, Job> = ConcurrentHashMap()

    private val ioLoopScope = CoroutineScope(ioLoopDispatcher)
    private val saveAfterScope = CoroutineScope(saveAfterDispatcher)
    private val cronScope = CoroutineScope(cronDispatcher)

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

    private val connectionHandler: Job = ioLoopScope.async {
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
        msg: Wrapper
    ): Boolean {
        return outboundQueue.offer(msg)
    }

    override suspend fun <Out, Wrapper : OutboundMessage<*, Out>, Resp : InboundMessage> issueOutboundAndWait(
        msg: Wrapper,
        inboundType: Inbound<Resp>,
        maxWait: Duration,
        isMine: (Resp) -> Boolean
    ): Resp? {
        val guard = AtomicBoolean(false)

        return suspendCoroutineWithTimeout(maxWait) { continuation ->
            var id: WatcherID? = null
            val watchFn: (Resp) -> Unit = { resp: Resp ->
                //Check if this resp matches our request and if the watcher has been invoked, bail if it has
                if (isMine(resp) && !guard.getAndSet(true)) {
                    continuation.resume(resp)
                    removeWatcher(id!!)
                }
            }

            id = addWatcher(inboundType, watchFn)
            issueOutbound(msg)
        }
    }

    private suspend inline fun <T> suspendCoroutineWithTimeout(
        timeout: Duration,
        crossinline block: (Continuation<T>) -> Unit
    ) = withTimeout(timeout) {
        suspendCancellableCoroutine(block = block)
    }

    override fun <Out, Wrapper : OutboundMessage<*, Out>> repeatOutbound(
        msgGenerator: () -> Wrapper,
        interval: Duration,
        initialDelay: Duration
    ): ScheduledMessageId {
        val scheduleId = UUID.randomUUID()

        cronMessages[scheduleId] = cronScope.async {
            delay(initialDelay)
            while (isActive) {
                if (!issueOutbound(msgGenerator())) {
                    connectionWatcher(
                        ConnectionEvent.ScheduledMessageEnqueueFailure,
                        "Queue was full when scheduled message submission was attempted",
                        null
                    )
                }
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
        saveAfter: Duration,
        wrapperBuilder: (T, Boolean) -> Wrapper
    ): SendChannel<T> {
        val jobId = UUID.randomUUID()
        val channel = Channel<T>(config.saveAfterWriteBufferSize)
        saveAfterJobs[jobId] = saveAfterScope.async {
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
                        val isDuplicate = this == last
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

                        if (!isDuplicate || save) {
                            val message = wrapperBuilder(this, save)
                            if (!issueOutbound(message)) {
                                errorLog("Enqueue of non-save request failed for saveAfter()", null)
                            }
                        }
                    } ?: run {
                        if (last != null && last != lastSaved) {
                            lastSaved = last
                            lastSaveAt = Instant.now()
                            val message = wrapperBuilder(last!!, true)
                            if (!issueOutbound(message)) {
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
        handler: (ParsedType) -> Unit
    ): WatcherID = addWatcherHelper(type, null, handler)

    override fun <ParsedType : InboundMessage> addWatcher(
        type: Inbound<ParsedType>,
        coroutineScope: CoroutineScope,
        handler: (ParsedType) -> Unit
    ): WatcherID = addWatcherHelper(type, coroutineScope, handler)

    private fun <ParsedType : InboundMessage> addWatcherHelper(
        type: Inbound<ParsedType>,
        coroutineScope: CoroutineScope?,
        handler: (ParsedType) -> Unit
    ): WatcherID {
        val watcherID = UUID.randomUUID()
        watchers.putIfAbsent(type as Inbound<InboundMessage>, ConcurrentLinkedQueue())
        watchers[type]!!.add(Watcher(watcherID, handler as (InboundMessage) -> Unit, coroutineScope))
        return watcherID
    }

    override fun removeWatcher(id: WatcherID): Boolean {
        return watchers.any { (_, list) -> list.removeIf { watcher -> watcher.id == id } }
    }

    override fun removeWatchersForType(type: Inbound<*>): List<WatcherID> =
        watchers.remove(type)?.map { it.id } ?: listOf()

    override fun <ParsedType : InboundMessage> addTextParser(
        priority: Int,
        msgType: InboundText<ParsedType>,
        parserFn: (Gson, String) -> ParsedType?
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
        binaryParsers[msgType.binaryFlag] = SerialParser(id, msgType, parserFn)
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

    override fun removeTextParsersForType(type: InboundText<*>): List<ParserID> {
        val removed = mutableListOf<ParserID>()
        textParsers.removeIf {
            if (it.type == type) {
                removed.add(it.id)
                true
            } else {
                false
            }
        }

        return removed
    }

    override fun removeBinaryParserForType(type: InboundBinary<*>): ParserID? =
        binaryParsers.remove(type.binaryFlag)?.id

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
                    sendOutboundMessage(this as OutboundMessage<Any, *>, outgoing)
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
                    if (watchers[parser.type as Inbound<*>]?.isNotEmpty() == true) {
                        val parsed = try {
                            parser.parseFn(gson, frame.readText())
                        } catch (t: Throwable) {
                            connectionWatcher(
                                ConnectionEvent.ParseFailed,
                                "Parse error for type ${parser.type.extractedType}",
                                t
                            )
                            null
                        }?.let { Pair(parser.type, it) }
                    }
                }
                null
            }

            is Frame.Binary -> {
                readBinaryFrame(frame)?.run {
                    val message = this
                    if (watchers[message.first as Inbound<*>]?.isNotEmpty() == true) {
                        binaryParsers[message.first.binaryFlag]?.run {
                            val parser = this
                            try {
                                parser.parseFn(message.second)?.run {
                                    Pair(parser.type, this)
                                }
                            } catch (t: Throwable) {
                                connectionWatcher(
                                    ConnectionEvent.ParseFailed,
                                    "Parse error for type flag ${message.first.binaryFlag}",
                                    t
                                )
                                null
                            }
                        }
                    } else {
                        null
                    }
                }
            }

            else -> null
        }
    }

    internal fun readBinaryFrame(frame: Frame.Binary): Pair<InboundBinary<*>, InputStream>? {
        if (frame.data.isEmpty()) {
            return null
        } else {
            val typeFlag = frame.data[0]
            if (typeFlag >= 0) {
                if (typeFlag == InboundPreviewFrame.binaryFlag) { //Preview frames are never split
                    return if (binaryParsers.containsKey(InboundPreviewFrame.binaryFlag)) {
                        Pair(InboundPreviewFrame, frame.readBytes().inputStream(1, frame.data.size))
                    } else {
                        null
                    }
                } else {
                    if (!binaryParsers.containsKey(typeFlag)) {
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
        message: OutboundMessage<Any, *>,
        outgoing: SendChannel<Frame>
    ) {
        val context: Any = when (message.type) {
            is OutboundText<*> -> gson
            is OutboundBinary<*> -> {
                val binaryType = message.type as OutboundBinary<*>
                BinarySerializationContext(
                    config.outboundFrameSize,
                    binaryType.binaryFlag,
                    binaryType.canBeSplit
                )
            }
        }

        val frames = message.toFrames(context) ?: return
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
        private val binaryParsers: ConcurrentMap<Byte, SerialParser<*>> = ConcurrentHashMap()
        private val gsonBuilder: GsonBuilder = GsonBuilder()

        private var ioLoopDispatcher: CoroutineDispatcher? = null
        private var saveAfterDispatcher: CoroutineDispatcher? = null
        private var cronDispatcher: CoroutineDispatcher? = null

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
        ): Pair<WatcherID, Builder> {
            val watcherID = UUID.randomUUID()
            watchers.putIfAbsent(type as Inbound<InboundMessage>, ConcurrentLinkedQueue())
            watchers[type]!!.add(Watcher(watcherID, handler as (InboundMessage) -> Unit, null))
            return Pair(watcherID, this)
        }

        fun <ParsedType : InboundMessage> addWatcher(
            type: Inbound<ParsedType>,
            coroutineScope: CoroutineScope,
            handler: (ParsedType) -> Unit
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
            binaryParsers[type.binaryFlag] = SerialParser(parserID, type, parseFn)
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

        fun addGsonAdapters(adapters: Map<Type, Any>): Builder {
            adapters.forEach { (type, adapter) -> gsonBuilder.registerTypeAdapter(type, adapter) }
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
                if (except.contains(InboundAllPrograms)) this
                else this.setBinaryParser(
                    InboundAllPrograms,
                    AllPrograms::fromBinary
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
                if (except.contains(InboundPlaylist)) this
                else this.addTextParser(
                    5000,
                    InboundPlaylist,
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
            return WebsocketPixelblaze(
                watchers = watchers,
                textParsers = textParsers,
                binaryParsers = binaryParsers,
                gson = gsonBuilder.create(),
                httpClient = httpClient ?: throw RuntimeException("No HTTP client specified"),
                address = address ?: throw RuntimeException("No pixelblaze IP specified!"),
                port = port ?: throw RuntimeException("No port specified"),
                config = config ?: throw RuntimeException("No config specified"),
                connectionWatcher = connectionWatcher,
                errorLog = errorLog,
                infoLog = infoLog,
                debugLog = debugLog,
                ioLoopDispatcher = ioLoopDispatcher ?: throw RuntimeException("No io loop dispatcher specified"),
                saveAfterDispatcher = saveAfterDispatcher
                    ?: throw RuntimeException("No save after dispatcher specified"),
                cronDispatcher = cronDispatcher ?: throw RuntimeException("No scheduled dispatcher specified")
            )
        }
    }

    companion object {

        fun defaultBuilder(): Builder {
            return bareBuilder()
                .setPixelblazeIp("192.168.4.1")
                .setPort(81)
                .setConfig(PixelblazeConfig.default())
                .setHttpClient(HttpClient {
                    install(WebSockets)
                })
                .setIoLoopDispatcher(Dispatchers.IO)
                .setRepeatedOutboundDispatcher(Dispatchers.Default)
                .setSaveAfterDispatcher(Dispatchers.Default)
                .addDefaultParsers()
        }

        fun bareBuilder(): Builder {
            return Builder()
        }
    }
}
