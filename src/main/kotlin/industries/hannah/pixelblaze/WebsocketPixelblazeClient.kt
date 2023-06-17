package industries.hannah.pixelblaze

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.awt.Image
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.SequenceInputStream
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class WebsocketPixelblazeClient(
    private val config: PixelblazeConfig,
    private val jsonMessageWatchers: List<Consumer<JsonObject>> = listOf(),
    private val unhandledBinaryWatchers: Map<BinaryMsgType, Consumer<InputStream>> = mapOf(),
    private val errorLog: (String?, Throwable?) -> Unit = { _, _ -> },
    private val infoLog: (String) -> Unit = { _ -> },
    private val debugLog: (String) -> Unit = { _ -> },
) : PixelblazeClient {
    private val shouldRun = AtomicBoolean(true)
    private val requestQueue: BlockingQueue<PixelblazeIo<*>> = ArrayBlockingQueue(config.requestQueueDepth)
    private val awaitingResponse: BlockingQueue<PixelblazeIo<*>> =
        ArrayBlockingQueue(config.awaitingResponseQueueDepth)

    private val binaryFrames: ArrayDeque<InputStream> = ArrayDeque(config.inboundBufferQueueDepth)
    private var activeMessageType: BinaryMsgType? = null

    private val gson = GsonBuilder().create()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val requestHandler: Job = coroutineScope.async {
        val client = HttpClient {
            install(WebSockets)
        }
        handleRequests(client)
    }

    private suspend fun handleRequests(client: HttpClient) {
        client.webSocket(
            method = HttpMethod.Get,
            host = config.address.toString(),
            port = config.port,
            path = "/"
        ) {
            while (shouldRun.get()) {
                try {
                    awaitingResponse.removeIf { io -> io.satisfied || io.responseTypeKey is NoResponseManaged }
                    var ingressed = 0

                    // There are basically 3 variables at play here, and we have to cover all the combinations:
                    //  1. The frame type of the received message (BINARY/TEXT)
                    //  2. Whether the request queue had anyone waiting in it
                    //  3. If it did, does the message type they're seeking match the received message?
                    while (ingressed < config.maxInboundMessagesBeforeOutbound) {
                        incoming.tryReceive().getOrNull()?.run {
                            val headOfQueue = awaitingResponse.peek()
                            when (this.frameType) {
                                FrameType.TEXT -> {
                                    try {
                                        gson.fromJson(
                                            InputStreamReader(ByteArrayInputStream(this.data)),
                                            JsonObject::class.java
                                        )
                                    } catch (e: JsonParseException) {
                                        errorLog("Couldn't parse message as JSON object", e)
                                        null
                                    }?.run {
                                        if (headOfQueue != null) {
                                            when (headOfQueue) {
                                                is TextResponseIo<*> -> {
                                                    if (headOfQueue.jsonResponseTypeKey.matches(this)) {
                                                        headOfQueue.extractAndHandle(this)
                                                        headOfQueue.satisfied = true
                                                        awaitingResponse.poll()!! //Discard head, we peeked
                                                    } else {
                                                        handleUnrequestedObject(this)
                                                    }
                                                }

                                                is BinaryResponseIo<*> -> {}
                                                is NoResponseIo<*> -> {}
                                            }
                                        } else {
                                            handleUnrequestedObject(this)
                                        }

                                        jsonMessageWatchers.forEach { it.accept(this) }
                                    }
                                }

                                FrameType.BINARY -> {
                                    val frame = this
                                    this.data.getOrNull(0)?.run {
                                        BinaryMsgType.fromByte(this)
                                    }?.run {
                                        val msgType = this
                                        when (msgType) {
                                            BinaryMsgType.PreviewFrame -> Pair(
                                                msgType, ByteArrayInputStream(
                                                    frame.data,
                                                    1,
                                                    frame.data.size - 1
                                                )
                                            )

                                            else -> {
                                                frame.data.getOrNull(1)?.run {
                                                    FramePosition.fromByte(this)
                                                }?.run {
                                                    when (this) {
                                                        FramePosition.First -> {
                                                            if (binaryFrames.size > 0) {
                                                                addFrameToBuffer(frame)
                                                                activeMessageType = msgType
                                                            } else {
                                                                errorLog(
                                                                    "Got 'first' message, but was already buffering",
                                                                    null
                                                                )
                                                                activeMessageType = null
                                                                binaryFrames.clear()
                                                            }

                                                            null
                                                        }

                                                        FramePosition.Middle -> {
                                                            if (binaryFrames.size > 0) {
                                                                if (activeMessageType != msgType) {
                                                                    errorLog("Got middle message with wrong type", null)
                                                                    activeMessageType = null
                                                                    binaryFrames.clear()
                                                                } else if (binaryFrames.size + 1 <= config.inboundBufferQueueDepth) {
                                                                    addFrameToBuffer(frame)
                                                                } else {
                                                                    errorLog("Inbound buffer full", null)
                                                                    activeMessageType = null
                                                                    binaryFrames.clear()
                                                                }
                                                            } else {
                                                                errorLog("Got a middle message but no start", null)
                                                                activeMessageType = null
                                                                binaryFrames.clear()
                                                            }
                                                            null
                                                        }

                                                        FramePosition.Last -> {
                                                            if (binaryFrames.size > 0) {
                                                                if (activeMessageType == msgType) {
                                                                    activeMessageType = null
                                                                    val concatStream: InputStream = binaryFrames.fold(
                                                                        ByteArrayInputStream(ByteArray(0))
                                                                    ) { acc: InputStream, stream ->
                                                                        SequenceInputStream(acc, stream)
                                                                    }
                                                                    binaryFrames.clear()
                                                                    Pair(msgType, concatStream)
                                                                } else {
                                                                    errorLog("Got last message with wrong type", null)
                                                                    activeMessageType = null
                                                                    binaryFrames.clear()
                                                                    null
                                                                }
                                                            } else {
                                                                errorLog("Got a last message but no start", null)
                                                                activeMessageType = null
                                                                binaryFrames.clear()
                                                                null
                                                            }
                                                        }

                                                        FramePosition.Only -> {
                                                            Pair<BinaryMsgType, InputStream>(
                                                                msgType, ByteArrayInputStream(
                                                                    frame.data,
                                                                    2,
                                                                    frame.data.size - 2
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }?.run {
                                        when (headOfQueue) {
                                            null -> handleUnrequestedBinary(this.first, this.second)
                                            is BinaryResponseIo<*> -> {
                                                if (headOfQueue.binResponseTypeKey.binaryMsgType == this.first) {
                                                    headOfQueue.handleRaw(this.second)
                                                    headOfQueue.satisfied = true
                                                    awaitingResponse.poll()!!
                                                } else {
                                                    handleUnrequestedBinary(this.first, this.second)
                                                }
                                            }

                                            is TextResponseIo<*> -> {
                                                handleUnrequestedBinary(this.first, this.second)
                                            }

                                            is NoResponseIo<*> -> {}
                                        }
                                    }
                                }

                                FrameType.CLOSE -> TODO()
                                FrameType.PING -> TODO()
                                FrameType.PONG -> TODO()
                            }
                            true
                        } ?: break

                        ingressed++
                    }

                    var egressed = 0
                    var reqHandleFailed = false
                    while (!reqHandleFailed && egressed < config.maxOutboundMessagesBeforeInbound) {
                        requestQueue.poll()?.run {
                            val io = this
                            when (this.request) {
                                is JsonRequest<*> -> listOf(this.request.toFrame(gson))

                                is BinaryRequest -> this.request.toFrames(config.outboundFrameSize) ?: run {
                                    reportFailure(this.failureNotifier, FailureCause.RequestTooLarge)
                                    listOf()
                                }

                                is NoopRequest -> listOf()
                            }.run {
                                for (frame in this) {
                                    try {
                                        outgoing.send(frame)
                                    } catch (t: Throwable) {
                                        reportFailure(
                                            notifier = io.failureNotifier,
                                            cause = FailureCause.MessageRejected,
                                            thrown = t
                                        )
                                        reqHandleFailed = true
                                        break
                                    }
                                }

                                if (!reqHandleFailed) {
                                    if (io.responseTypeKey !is NoResponseManaged) {
                                        if (!awaitingResponse.offer(io)) {
                                            io.failureNotifier(PixelblazeException(FailureCause.AwaitingResponseQueueFull))
                                            reqHandleFailed = true
                                        }
                                    }
                                }
                            }
                        } ?: break
                        egressed++
                    }
                } catch (t: Throwable) {
                    errorLog("Unexpected error in main loop", t)
                }
            }
        }

        client.close()
    }

    private fun addFrameToBuffer(frame: Frame) {
        binaryFrames.add(
            ByteArrayInputStream(
                frame.data,
                2,
                frame.data.size - 2
            )
        )
    }

    private fun reportFailure(
        notifier: FailureNotifier,
        cause: FailureCause,
        msg: String = "",
        thrown: Throwable? = null
    ) {
        try {
            throw PixelblazeException(cause, msg, thrown)
        } catch (p: PixelblazeException) {
            notifier(p)
        }
    }

    private fun handleUnrequestedBinary(msgType: BinaryMsgType, stream: InputStream) {
        unhandledBinaryWatchers[msgType]?.run { this.accept(stream) }
    }

    private fun handleUnrequestedObject(obj: JsonObject) {

    }

    override fun getPatterns(handler: (AllPatterns) -> Unit, onFailure: FailureNotifier) {
        TODO("Not yet implemented")
    }

    override fun getPatternsSync(): AllPatterns {
        TODO("Not yet implemented")
    }

    override fun getPlaylist(handler: (Playlist) -> Unit, playlistName: String, onFailure: FailureNotifier) {
        TODO("Not yet implemented")
    }

    override fun getPlaylistSync(playlistName: String): Playlist? {
        TODO("Not yet implemented")
    }

    override fun getPlaylistIndex(handler: (Int) -> Unit, onFailure: FailureNotifier) {
        TODO("Not yet implemented")
    }

    override fun getPlaylistIndexSync(): Int {
        TODO("Not yet implemented")
    }

    override fun setPlaylistIndex(idx: Int) {
        TODO("Not yet implemented")
    }

    override fun nextPattern() {
        TODO("Not yet implemented")
    }

    override fun prevPattern() {
        TODO("Not yet implemented")
    }

    override fun playSequence() {
        TODO("Not yet implemented")
    }

    override fun pauseSequence() {
        TODO("Not yet implemented")
    }

    override fun setSequencerMode(mode: SequencerMode) {
        TODO("Not yet implemented")
    }

    override fun getPeers(handler: (List<Peer>) -> Unit, onFailure: FailureNotifier) {
        TODO("Not yet implemented")
    }

    override fun getPeersSync(): List<Peer> {
        TODO("Not yet implemented")
    }

    override fun setBrightness(brightness: Float, saveToFlash: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setCurrentPatternControl(controlName: String, value: Float, saveToFlash: Float) {
        TODO("Not yet implemented")
    }

    override fun setCurrentPatternControls(controls: List<Control>, saveToFlash: Boolean) {
        TODO("Not yet implemented")
    }

    override fun getCurrentPatternControls(handler: (List<Control>) -> Unit, onFailure: FailureNotifier) {
        TODO("Not yet implemented")
    }

    override fun getCurrentPatternControlsSync(): List<Control> {
        TODO("Not yet implemented")
    }

    override fun getPatternControls(patternId: String, handler: (List<Control>) -> Unit, onFailure: FailureNotifier) {
        TODO("Not yet implemented")
    }

    override fun getPatternControlsSync(patternId: String): List<Control> {
        TODO("Not yet implemented")
    }

    override fun getPreviewImage(patternId: String, handler: (Image) -> Unit, onFailure: FailureNotifier) {
        TODO("Not yet implemented")
    }

    override fun getPreviewImageSync(patternId: String): Image {
        TODO("Not yet implemented")
    }

    override fun setBrightnessLimit(value: Float, saveToFlash: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setPixelCount(pixels: UInt, saveToFlash: Boolean) {
        TODO("Not yet implemented")
    }

    override fun getSystemState(
        settingsHandler: (Settings) -> Unit?,
        seqHandler: (SequencerState) -> Unit?,
        expanderHandler: (List<ExpanderChannel>) -> Unit?,
        onFailure: FailureNotifier
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun getSettings(settingsHandler: (Settings) -> Unit, onFailure: FailureNotifier) {
        TODO("Not yet implemented")
    }

    override fun getSettingsSync(): Settings {
        TODO("Not yet implemented")
    }

    override fun getSequencerState(seqHandler: (SequencerState) -> Unit, onFailure: FailureNotifier) {
        TODO("Not yet implemented")
    }

    override fun getSequencerStateSync(): SequencerState {
        TODO("Not yet implemented")
    }

    override fun getExpanderConfig(expanderHandler: (List<ExpanderChannel>) -> Unit, onFailure: FailureNotifier) {
        TODO("Not yet implemented")
    }

    override fun getExpanderConfigSync(): List<ExpanderChannel> {
        TODO("Not yet implemented")
    }

    override fun ping(handler: (Duration) -> Unit, onFailure: FailureNotifier) {
        TODO("Not yet implemented")
    }

    override fun pingSync(): Duration {
        TODO("Not yet implemented")
    }

    override fun sendFramePreviews(sendEm: Boolean) {
        TODO("Not yet implemented")
    }

    override fun <Req, Resp> rawJsonRequest(request: Req, requestClass: Class<Req>) {
        TODO("Not yet implemented")
    }

    override fun rawBinaryRequest(requestType: BinaryMsgType, requestData: ByteArray, canBeSplit: Boolean) {
        TODO("Not yet implemented")
    }

    override fun close() {
        shouldRun.set(false)
        runBlocking {
            requestHandler.join()
        }
    }
}
