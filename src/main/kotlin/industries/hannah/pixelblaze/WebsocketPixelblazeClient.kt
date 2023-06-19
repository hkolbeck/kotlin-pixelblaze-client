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
import java.io.InputStream
import java.io.SequenceInputStream
import java.lang.reflect.Type
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.*
import kotlin.collections.ArrayDeque
import kotlin.collections.set
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

class WebsocketPixelblazeClient internal constructor(
    private val address: String,
    private val port: Int,
    private val watchers: ConcurrentMap<Inbound<InboundMessage>, ConcurrentLinkedQueue<Watcher<InboundMessage>>>,
    private val textParsers: PriorityBlockingQueue<TextParser<*>>,
    private val binaryParsers: ConcurrentMap<InboundBinary<*>, SerialParser<*>>,
    private val connectionWatcher: (ConnectionStatus, String?, Throwable?) -> Unit,
    private val config: PixelblazeConfig,
    private val gson: Gson,
    private val httpClient: HttpClient,
    private val errorLog: (String?, Throwable?) -> Unit = { _, _ -> },
    private val infoLog: (String) -> Unit = { _ -> },
    private val debugLog: (String) -> Unit = { _ -> }
) : PixelblazeClient {
    private val outboundQueue: BlockingQueue<OutboundMessageWrapper<*>> =
        ArrayBlockingQueue(config.outboundQueueDepth)
    private val scheduledMessages: PriorityBlockingQueue<ScheduledMessage<*>> =
        PriorityBlockingQueue(10) { a, b -> a.runAt.compareTo(b.runAt) }

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
    )

    enum class ConnectionStatus {
        Connected,
        ScheduledMessageEnqueueFailure,
        WatchThreadException,
        ClientDisconnect
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val connectionHandler: Job = coroutineScope.async {
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
                    ConnectionStatus.WatchThreadException,
                    "Unexpected error from connection handler, reconnecting",
                    t
                )
                delay(config.sleepStrategyOnDisconnect(retry))
            }
        }
    }

    private val scheduleHandler: Job = coroutineScope.async {
        while (isActive) {
            try {
                var messageDispatched = false
                scheduledMessages.peek()?.run {
                    if (this.runAt.isBefore(Instant.now())) {
                        scheduledMessages.poll()!!
                        if (outboundQueue.offer(
                                OutboundMessageWrapper(this.type as Outbound<Any>, this.messageGenerator() as Any),
                                config.scheduledTaskDispatchWait.inWholeMilliseconds,
                                TimeUnit.MILLISECONDS
                            )
                        ) {
                            messageDispatched = true
                        } else {
                            connectionWatcher(
                                ConnectionStatus.ScheduledMessageEnqueueFailure,
                                "Dispatch of scheduled message failed, queue full",
                                null
                            )
                        }

                        scheduledMessages.put(
                            ScheduledMessage(
                                this.type,
                                this.messageGenerator as () -> Any,
                                this.scheduleId,
                                this.runAt.plus(this.interval.inWholeMilliseconds, ChronoUnit.MILLIS),
                                this.interval
                            )
                        )
                    }
                }

                if (!messageDispatched) {
                    delay(100.toDuration(DurationUnit.MILLISECONDS))
                }
            } catch (t: Throwable) {
                errorLog("Unexpected exception in scheduler coroutine", t)
                delay(1.toDuration(DurationUnit.SECONDS)) //This would be unexpected, don't spam
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
        return suspendCoroutineWithTimeout(maxWait) { continuation ->
            var id: WatcherID? = null
            val watchFn: (Resp) -> Unit = { resp: Resp ->
                continuation.resume(resp)
                removeWatcher(id!!)
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
        scheduledMessages.add(
            ScheduledMessage(
                type,
                msgGenerator,
                scheduleId,
                Instant.now().plus(initialDelay.toJavaDuration()),
                interval
            )
        )

        return scheduleId
    }

    override fun cancelRepeatedOutbound(id: ScheduledMessageId): Boolean {
        return scheduledMessages.removeIf { msg -> msg.scheduleId == id }
    }

    override fun <T, Out, Message : OutboundMessage<*, Out>> saveAfter(
        wrapperBuilder: (T, Boolean) -> Message,
        saveAfter: Duration
    ): Channel<T> {
        TODO("Not yet implemented")
    }

    override fun <ParsedType : InboundMessage> addWatcher(
        type: Inbound<ParsedType>,
        handler: (ParsedType) -> Unit
    ): WatcherID {
        val watcherID = UUID.randomUUID()
        watchers.putIfAbsent(type as Inbound<InboundMessage>, ConcurrentLinkedQueue())
        watchers[type]!!.add(Watcher(watcherID, handler as (InboundMessage) -> Unit))
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
                            ConnectionStatus.ClientDisconnect,
                            "Client disconnected, will attempt to reconnect",
                            null
                        )
                    }
                    errorLog("Connection closed, will retry", null)
                    break
                } else if (receiveResult.isSuccess) {
                    if (!unclosed) {
                        connectionWatcher(
                            ConnectionStatus.Connected,
                            "Client received first message on new connection",
                            null
                        )
                    }

                    readFrame(receiveResult.getOrThrow())?.run { dispatchInboundFrame(this) }
                    workDone = true //True even if we discarded the read, don't want to sleep
                    unclosed = true
                } else {
                    // else receiveResult.isFailure => nothing to read but mark conn as healthy
                    unclosed = true
                }

                outboundQueue.poll()?.run {
                    sendOutboundMessage(this)
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

    private fun dispatchInboundFrame(message: Pair<Inbound<*>, InboundMessage>) {
        TODO()
    }

    private fun sendOutboundMessage(message: OutboundMessageWrapper<*>) {
        TODO()
    }

    override fun close() {
        runBlocking {
            scheduleHandler.cancel()
            connectionHandler.cancel()
            scheduleHandler.join()
            connectionHandler.join()
        }
    }

    class Builder {
        private val watchers: ConcurrentMap<Inbound<InboundMessage>, ConcurrentLinkedQueue<Watcher<InboundMessage>>> =
            ConcurrentHashMap()
        private val textParsers: PriorityBlockingQueue<TextParser<*>> =
            PriorityBlockingQueue(16) { a, b -> a.priority - b.priority }
        private val binaryParsers: ConcurrentMap<InboundBinary<*>, SerialParser<*>> = ConcurrentHashMap()
        private val gsonBuilder: GsonBuilder = GsonBuilder()

        private var httpClient: HttpClient? = null
        private var address: String? = null
        private var port: Int = 81
        private var config: PixelblazeConfig = PixelblazeConfig()
        private var connectionWatcher: (ConnectionStatus, String?, Throwable?) -> Unit = { _, _, _ -> }
        private var errorLog: (String?, Throwable?) -> Unit = { _, _ -> }
        private var infoLog: (String) -> Unit = { _ -> }
        private var debugLog: (String) -> Unit = { _ -> }

        fun <ParsedType : InboundMessage> addWatcher(
            type: Inbound<ParsedType>,
            handler: (ParsedType) -> Unit
        ): Pair<WatcherID, Builder> {
            val watcherID = UUID.randomUUID()
            watchers.putIfAbsent(type as Inbound<InboundMessage>, ConcurrentLinkedQueue())
            watchers[type]!!.add(Watcher(watcherID, handler as (InboundMessage) -> Unit))
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

        fun setAddress(address: String): Builder {
            this.address = address
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

        fun setConnectionWatcher(connectionWatcher: (ConnectionStatus, String?, Throwable?) -> Unit): Builder {
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

        fun build(): WebsocketPixelblazeClient {
            val httpClient = httpClient ?: HttpClient {
                install(WebSockets)
            }

            return WebsocketPixelblazeClient(
                watchers = watchers,
                textParsers = textParsers,
                binaryParsers = binaryParsers,
                gson = gsonBuilder.create(),
                httpClient = httpClient,
                address = address ?: throw RuntimeException("No address specified"),
                port = port,
                config = config,
                connectionWatcher = connectionWatcher,
                errorLog = errorLog,
                infoLog = infoLog,
                debugLog = debugLog
            )
        }
    }

    companion object {

        fun builder(): Builder {
            //private val STD_JSON_PARSERS = listOf<(JsonObject) -> InboundMessage?>(
//    Stats::fromJson,
//    SequencerState::fromJson,
//    Settings::fromJson,
//    PeerInboundMessage::fromJson,
//
//    //PlaylistUpdate has a subset of Playlist's fields, important to check Playlist first
//    Playlist::fromJson,
//    PlaylistUpdate::fromJson,
//    Ack::fromJson
//)
//
//private val STD_BINARY_PARSERS = mapOf<BinaryTypeFlag, (InputStream) -> InboundMessage?>(
//    Pair(BinaryTypeFlag.ExpanderChannels, ExpanderChannels::fromBinary),
//    Pair(BinaryTypeFlag.PreviewImage, PreviewImage::fromBinary),
//    Pair(BinaryTypeFlag.PreviewFrame, PreviewFrame::fromBinary),
//    Pair(BinaryTypeFlag.GetProgramList, ProgramList::fromBinary)
//);


            return parserlessBuilder()
        }

        fun parserlessBuilder(): Builder {
            return Builder()
        }
    }
}
