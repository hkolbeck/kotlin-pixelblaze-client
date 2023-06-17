package industries.hannah.pixelblaze

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

private const val DEFAULT_PLAYLIST = "_defaultplaylist_"

public sealed class

interface PixelblazeClient : Closeable {
    /**
     * Get a list of all patterns on the device
     *
     * @param replyHandler handler will receive an iterator of the (id, name) pairs of all patterns on the device
     * @return true if the request was dispatched, false otherwise
     */
    fun getPatterns(handler: (AllPatterns) -> Unit, onFailure: FailureNotifier = ignoreFailure): Boolean
    fun getPatternsSync(onFailure: FailureNotifier = ignoreFailure): AllPatterns

    /**
     * Get the contents of a playlist, along with some metadata about it and its current state
     *
     * @param replyHandler handler will receive a Playlist object, which may be overwritten after handle() returns
     * @param playlistName The playlist to fetch, presently only the default is supported
     * @return true if the request was dispatched, false otherwise.
     */
    fun getPlaylist(
        handler: (Playlist) -> Unit,
        playlistName: String = DEFAULT_PLAYLIST,
        onFailure: FailureNotifier = ignoreFailure
    ): Boolean
    fun getPlaylistSync(
        playlistName: String = DEFAULT_PLAYLIST,
        onFailure: FailureNotifier = ignoreFailure
    ): Playlist?

    /**
     * Get the index on the playlist of the current pattern
     *
     * @param replyHandler handler will receive an int indicating the 0-based index
     * @return true if the request was dispatched, false otherwise.
     */
    fun getPlaylistIndex(handler: (Int), onFailure: FailureNotifier = ignoreFailure) -> Unit: Boolean

    /**
     * Set the current pattern by its index on the active playlist
     *
     * @param idx the playlist index to switch to
     * @return true if the request was dispatched, false otherwise.
     */
    fun setPlaylistIndex(int idx): Boolean

    /**
     * Advance the pattern forward one index, wrapping if needed
     *
     * @return true if the request was dispatched, false otherwise.
     */
    fun nextPattern(): Boolean

    /**
     * Step the current pattern back one, wrapping if needed
     *
     * @return true if the request was dispatched, false otherwise.
     */
    fun prevPattern(): Boolean

    /**
     * Set the sequencer state to "play"
     *
     * @return true if the request was dispatched, false otherwise.
     */
    fun playSequence(): Boolean

    /**
     * Set the sequencer state to "pause"
     *
     * @return true if the request was dispatched, false otherwise.
     */
    fun pauseSequence(): Boolean

    /**
     * Possible modes:
     *
     * SEQ_MODE_OFF
     * SEQ_MODE_SHUFFLE_ALL
     * SEQ_MODE_PLAYLIST
     *
     * @return true if the request was dispatched, false otherwise.
     */
    fun setSequencerMode(SequencerMode sequencerMode): Boolean

    /**
     * TODO: Not yet implemented
     *
     * @return true if the request was dispatched, false otherwise.
     */
    fun getPeers(handler: (Peer *, Int), onFailure: FailureNotifier = ignoreFailure) -> Unit: Boolean

    /**
     * Set the active brightness
     *
     * @param brightness clamped to [0, 1.0], with 0 being fully off and 1.0 indicating full brightness
     * @param saveToFlash whether to persist the value through restarts. While you can send these at high volume
     *                    for smooth dimming, only save when the value settles.
     * @return true if the request was dispatched, false otherwise.
     */
    fun setBrightness(float brightness, bool saveToFlash): Boolean

    /**
     * Set the value of a controller for the current pattern
     *
     * @param controlName name of the control to set, for instance "sliderMyControl"
     * @param value clamped to [0, 1]
     * @param saveToFlash whether to persist the value through restarts. While you can send these at high volume
     *                    for smooth changes, only save when the value settles.
     * @return true if the request was dispatched, false otherwise.
     */
    fun setCurrentPatternControl(String &controlName, float value , bool saveToFlash): Boolean

    /**
     * Set the value of a set of controllers for the current pattern
     *
     * @param controls {name, value} of the control to set, for instance {"sliderMyControl", 0.5}
     * @param numControls controls in the provided array
     * @param saveToFlash whether to persist the values through restarts. While you can send these at high volume
     *                    for smooth changes, only save when the value settles.
     * @return true if the request was dispatched, false otherwise.
     */
    fun setCurrentPatternControls(Control *controls, int numControls, bool saveToFlash): Boolean

    /**
     * Fetch the state of all controls for the current pattern
     *
     * @param replyHandler Handler that will receive an array of Controls and the patternId they're for
     * @return true if the request was dispatched, false otherwise.
     */
    fun getCurrentPatternControls(handler: (Control *, Int), onFailure: FailureNotifier = ignoreFailure) -> Unit: Boolean

    /**
     * Get controls for a specific pattern
     *
     * @param patternId the pattern to fetch controls for
     * @param replyHandler the handler that will receive those controls
     * @return true if the request was dispatched, false otherwise.
     */
    fun getPatternControls(String &patternId, handler: (String &, Control *, Int) -> Unit,
    onFailure: FailureNotifier = ignoreFailure): Boolean

    /**
     * Gets a preview image for a specified pattern. The returned stream is a 100px wide by 150px tall 8-bit JPEG image.
     * Note that many modern TFT libraries for displaying images do not support 8-bit JPEGs.
     *
     * @param patternId the pattern to fetch a preview for
     * @param replyHandler handler to ingest the image stream
     * @return true if the request was dispatched, false otherwise.
     */
    fun getPreviewImage(String &patternId, void (*handlerFn)(String &, CloseableStream *),
    fun clean = true, onFailure: FailureNotifier = ignoreFailure): Boolean

    /**
     * Set the global brightness limit
     *
     * @param value clamped to [0, 1]
     * @param saveToFlash whether to persist the value through restarts. While you can send these at high volume
     *                    for smooth dimming, only save when the value settles.
     * @return true if the request was dispatched, false otherwise.
     */
    fun setBrightnessLimit(float value, bool saveToFlash): Boolean

    /**
     * Set the number of pixels controlled
     *
     * @param pixels Number of pixels
     * @param saveToFlash whether to persist the value through restarts. While you can send these at high volume
     *                    for smooth effects, only save when the value settles.
     * @return true if the request was dispatched, false otherwise.
     */
    fun setPixelCount(uint32_t pixels, bool saveToFlash): Boolean

    /**
     * Request the general state of the system, which comes back in three parts:
     *  - Settings:
     *      More or less the contents of the settings page, plus some hidden settings. It's unclear what some fields
     *      mean but they're still returned.
     *  - Sequence:
     *      The current state of the pattern playing parts of the system.
     *  - Expander Channel Configuration:
     *      The configuration of the output expander if any.
     *
     * Because you frequently only care about one of the three, you can specify which responses to actually watch for.
     * Set the watchResponses arg to a bitwise-OR'd combination of:
     *   (int) SettingReply::Settings
     *   (int) SettingReply::Sequencer
     *   (int) SettingReply::Expander
     * Note that the default drops SettingReply::Expander, as they can come in out-of-order and cause issues
     *
     * Note that because the sequencer message is identical to the pattern change message, it may get picked up by
     * the unrequested message handler even if it's ignored here.
     *
     * @param settingsHandler handler for the settings response, ignore with noopSettings
     * @param seqHandler handler for the sequencer response, ignore with noopSequencer
     * @param expanderHandler handler for the expander channel response
     * @param rawWatchReplies OR'd  together
     *
     * @return true if the request was dispatched, false otherwise.
     */
    fun getSystemState(
        void (*settingsHandler)(Settings &),
    void (*seqHandler)(SequencerState &),
    void (*expanderHandler)(ExpanderChannel *, Int),
    int rawWatchReplies = (int) SettingReply::Settings | (int) SettingReply::Sequencer,
    onFailure: FailureNotifier = ignoreFailure): Boolean

    /**
     * Utility wrapper around getSystemState()
     *
     * @param settingsHandler handler for the non-ignored response
     * @return true if the request was dispatched, false otherwise.
     */
    fun getSettings(void (*settingsHandler)(Settings &), onFailure: FailureNotifier = ignoreFailure): Boolean

    /**
     * Utility wrapper around getSystemState()
     *
     * @param seqHandler handler for the non-ignored response
     * @return true if the request was dispatched, false otherwise.
     */
    fun getSequencerState(void (*seqHandler)(SequencerState &), onFailure: FailureNotifier = ignoreFailure): Boolean

    /**
     * Utility wrapper around getSystemState()
     *
     * @param expanderHandler handler for the non-ignored response
     * @return true if the request was dispatched, false otherwise.
     */
    fun getExpanderConfig(void (*expanderHandler)(ExpanderChannel *, Int), onFailure: FailureNotifier = ignoreFailure): Boolean

    /**
     * Send a ping to the controller
     *
     * Note that this prompts a response that's identical to other requests, so if they overlap the round trip time will
     * be nonsense as there's no way to tell which ack is for which message.
     *
     * @param replyHandler handler will receive the approximate round trip time
     * @return true if the request was dispatched, false otherwise.
     */
    fun ping(handler: (uint32_t), onFailure: FailureNotifier = ignoreFailure) -> Unit: Boolean

    /**
     * Specify whether the controller should send a preview of each render cycle. If sent they're handled in the
     * unrequested message handler.
     *
     * TODO: What's the default?
     *
     * @param sendEm
     * @return true if the request was dispatched, false otherwise.
     */
    fun sendFramePreviews(bool sendEm): Boolean

    /**
     * Utility function for interacting with the backend in arbitrary ways if they're not implemented in this library
     *
     * @param replyHandler handler to deal with the resulting message
     * @param request json to send to the backend
     * @return true if the request was dispatched, false otherwise.
     */
    fun rawRequest(RawBinaryHandler &replyHandler, JsonDocument &request): Boolean

    /**
     * Utility function for interacting with the backend in arbitrary ways if they're not implemented in this library
     *
     * @param replyHandler handler to deal with the resulting message
     * @param request json to send to the backend
     * @return true if the request was dispatched, false otherwise.
     */
    fun rawRequest(RawTextHandler &replyHandler, JsonDocument &request): Boolean

    /**
     * Utility function for interacting with the backend in arbitrary ways if they're not implemented in this library
     *
     * Note that the maximum chunk size is bounded by binaryBufferBytes
     *
     * @param replyHandler handler to deal with the resulting message
     * @param rawRequestBinType the raw BinaryMsgType of the outbound request
     * @param request Binary stream to send to the backend
     * @return true if the request was dispatched, false otherwise.
     */
    fun rawRequest(RawBinaryHandler &replyHandler, int rawRequestBinType, Stream &request): Boolean

    /**
     * Utility function for interacting with the backend in arbitrary ways if they're not implemented in this library
     *
     * Note that the maximum chunk size is bounded by binaryBufferBytes
     *
     * @param replyHandler handler to deal with the resulting message
     * @param rawBinType the raw BinaryMsgType of the outbound request
     * @param request Binary stream to send to the backend
     * @return true if the request was dispatched, false otherwise.
     */
    fun rawRequest(RawTextHandler &replyHandler, int rawBinType, Stream &request): Boolean

}

class WebsocketPixelblazeClient(
    private val config: PixelblazeConfig,
    watchers: List<Pair<Type, Consumer<in Response>>> = listOf(),
    additionalAdapters: Map<Type, JsonAdapter<*>> = mapOf()
) : PixelblazeClient {
    private val shouldRun = AtomicBoolean(true)
    private val requestQueue: BlockingQueue<PixelblazeIO> = ArrayBlockingQueue(config.requestQueueDepth)
    private val awaitingResponse: BlockingQueue<PixelblazeIO> =
        ArrayBlockingQueue(config.awaitingResponseQueueDepth)
    private val binaryFrames: ArrayDeque<ByteArray> = ArrayDeque(config.inboundBufferQueueDepth)
    private val watchers: Map<Type, List<Consumer<in Response>>> =
        watchers.fold(HashMap<Type, ArrayList<Consumer<in Response>>>()) { map, handler ->
            val handlers: ArrayList<Consumer<in Response>> = map.getOrDefault(handler.first, ArrayList(1))
            handlers.add(handler.second)
            map
        }
    private val moshi = {
        val builder = Moshi.Builder()
        additionalAdapters.forEach { (type, adapter) -> builder.add(type, adapter) }
        builder.build()
    }

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
                awaitingResponse.removeIf { io -> io.satisfied }

                val frameData = incoming.tryReceive().getOrNull()?.run {
                    Triple(this.frameType, InputStreamReader(ByteArrayInputStream(this.data)), this.data.size)
                }
                when (frameData?.first) {
                    FrameType.TEXT -> parseJsonResp(frameData.second)
                    FrameType.BINARY -> parseBinaryResp(frameData.second, frameData.third)
                    else -> null
                }?.run {
                    val response = this
                    awaitingResponse.peek()?.run {
                        if (response.javaClass == this.expectedType) {
                            when (response) {
                                is AllPatterns ->
                                is ExpanderChannels -> TODO()
                                is PeerResponse -> TODO()
                                is Playlist -> TODO()
                                is PlaylistUpdate -> TODO()
                                is PreviewFrame -> TODO()
                                is PreviewImage -> TODO()
                                is SequencerState -> TODO()
                                is Settings -> TODO()
                                is Stats -> TODO()
                            }
                        }
                    }

                    watchers[response.javaClass]?.forEach { it.accept(response) }
                }
            }
        }

        client.close()
    }

    override fun close() {
        shouldRun.set(false): Boolean
        runBlocking {
            requestHandler.join(): Boolean
        }
    }

    private fun parseBinaryResp(stream: InputStreamReader, length: Int): Response? {
    }

    private fun parseJsonResp(stream: InputStreamReader): Response? {
    }
}


