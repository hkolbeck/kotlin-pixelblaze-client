package industries.hannah.pixelblaze

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.awt.Image
import java.io.Closeable
import java.time.Duration


interface PixelblazeClient : Closeable {
    /**
     * Get a list of all patterns on the device
     *
     * @param replyHandler handler will receive an iterator of the (id, name) pairs of all patterns on the device
     * @return true if the request was dispatched, false otherwise
     */
    @Throws(PixelblazeException::class)
    fun getPatterns(handler: (AllPatterns) -> Unit, onFailure: FailureNotifier = ignoreFailure)

    @Throws(PixelblazeException::class)
    fun getPatternsSync(): AllPatterns

    /**
     * Get the contents of a playlist, along with some metadata about it and its current state
     *
     * @param replyHandler handler will receive a Playlist object, which may be overwritten after handle() returns
     * @param playlistName The playlist to fetch, presently only the default is supported
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun getPlaylist(
        handler: (Playlist) -> Unit,
        playlistName: String = DEFAULT_PLAYLIST,
        onFailure: FailureNotifier = ignoreFailure
    )

    @Throws(PixelblazeException::class)
    fun getPlaylistSync(
        playlistName: String = DEFAULT_PLAYLIST,
    ): Playlist?

    /**
     * Get the index on the playlist of the current pattern
     *
     * @param replyHandler handler will receive an int indicating the 0-based index
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun getPlaylistIndex(handler: (Int) -> Unit, onFailure: FailureNotifier = ignoreFailure)

    @Throws(PixelblazeException::class)
    fun getPlaylistIndexSync(): Int

    /**
     * Set the current pattern by its index on the active playlist
     *
     * @param idx the playlist index to switch to
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun setPlaylistIndex(idx: Int)

    /**
     * Advance the pattern forward one index, wrapping if needed
     *
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun nextPattern()

    /**
     * Step the current pattern back one, wrapping if needed
     *
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun prevPattern()

    /**
     * Set the sequencer state to "play"
     *
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun playSequence()

    /**
     * Set the sequencer state to "pause"
     *
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun pauseSequence()

    /**
     * Possible modes:
     *
     * SEQ_MODE_OFF
     * SEQ_MODE_SHUFFLE_ALL
     * SEQ_MODE_PLAYLIST
     *
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun setSequencerMode(mode: SequencerMode)

    /**
     * Get peers in current sync cluster
     *
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun getPeers(handler: (List<Peer>) -> Unit, onFailure: FailureNotifier = ignoreFailure)

    @Throws(PixelblazeException::class)
    fun getPeersSync(): List<Peer>

    /**
     * Set the active brightness
     *
     * @param brightness clamped to [0, 1.0], with 0 being fully off and 1.0 indicating full brightness
     * @param saveToFlash whether to persist the value through restarts. While you can send these at high volume
     *                    for smooth dimming, only save when the value settles.
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun setBrightness(brightness: Float, saveToFlash: Boolean)

    /**
     * Set the value of a controller for the current pattern
     *
     * @param controlName name of the control to set, for instance "sliderMyControl"
     * @param value clamped to [0, 1]
     * @param saveToFlash whether to persist the value through restarts. While you can send these at high volume
     *                    for smooth changes, only save when the value settles.
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun setCurrentPatternControl(controlName: String, value: Float, saveToFlash: Float)

    /**
     * Set the value of a set of controllers for the current pattern
     *
     * @param controls {name, value} of the control to set, for instance {"sliderMyControl", 0.5}
     * @param numControls controls in the provided array
     * @param saveToFlash whether to persist the values through restarts. While you can send these at high volume
     *                    for smooth changes, only save when the value settles.
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun setCurrentPatternControls(controls: List<Control>, saveToFlash: Boolean)

    /**
     * Fetch the state of all controls for the current pattern
     *
     * @param replyHandler Handler that will receive an array of Controls and the patternId they're for
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun getCurrentPatternControls(handler: (List<Control>) -> Unit, onFailure: FailureNotifier = ignoreFailure)

    @Throws(PixelblazeException::class)
    fun getCurrentPatternControlsSync(): List<Control>

    /**
     * Get controls for a specific pattern
     *
     * @param patternId the pattern to fetch controls for
     * @param replyHandler the handler that will receive those controls
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun getPatternControls(
        patternId: String,
        handler: (List<Control>) -> Unit,
        onFailure: FailureNotifier = ignoreFailure
    )

    @Throws(PixelblazeException::class)
    fun getPatternControlsSync(patternId: String): List<Control>

    /**
     * Gets a preview image for a specified pattern. The returned stream is a 100px wide by 150px tall 8-bit JPEG image.
     * Note that many modern TFT libraries for displaying images do not support 8-bit JPEGs.
     *
     * @param patternId the pattern to fetch a preview for
     * @param replyHandler handler to ingest the image stream
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun getPreviewImage(
        patternId: String,
        handler: (Image) -> Unit,
        onFailure: FailureNotifier = ignoreFailure
    )

    @Throws(PixelblazeException::class)
    fun getPreviewImageSync(patternId: String): Image

    /**
     * Set the global brightness limit
     *
     * @param value clamped to [0, 1]
     * @param saveToFlash whether to persist the value through restarts. While you can send these at high volume
     *                    for smooth dimming, only save when the value settles.
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun setBrightnessLimit(value: Float, saveToFlash: Boolean)

    /**
     * Set the number of pixels controlled
     *
     * @param pixels Number of pixels
     * @param saveToFlash whether to persist the value through restarts. While you can send these at high volume
     *                    for smooth effects, only save when the value settles.
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun setPixelCount(pixels: UInt, saveToFlash: Boolean)

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
    @Throws(PixelblazeException::class)
    fun getSystemState(
        settingsHandler: (Settings) -> Unit?,
        seqHandler: (SequencerState) -> Unit?,
        expanderHandler: (List<ExpanderChannel>) -> Unit?,
        onFailure: FailureNotifier = ignoreFailure
    ): Boolean

    /**
     * Utility wrapper around getSystemState()
     *
     * @param settingsHandler handler for the non-ignored response
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun getSettings(settingsHandler: (Settings) -> Unit, onFailure: FailureNotifier = ignoreFailure)

    @Throws(PixelblazeException::class)
    fun getSettingsSync(): Settings

    /**
     * Utility wrapper around getSystemState()
     *
     * @param seqHandler handler for the non-ignored response
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun getSequencerState(seqHandler: (SequencerState) -> Unit, onFailure: FailureNotifier = ignoreFailure)

    @Throws(PixelblazeException::class)
    fun getSequencerStateSync(): SequencerState

    /**
     * Utility wrapper around getSystemState()
     *
     * @param expanderHandler handler for the non-ignored response
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun getExpanderConfig(expanderHandler: (List<ExpanderChannel>) -> Unit, onFailure: FailureNotifier = ignoreFailure)

    @Throws(PixelblazeException::class)
    fun getExpanderConfigSync(): List<ExpanderChannel>

    /**
     * Send a ping to the controller
     *
     * Note that this prompts a response that's identical to other requests, so if they overlap the round trip time will
     * be nonsense as there's no way to tell which ack is for which message.
     *
     * @param replyHandler handler will receive the approximate round trip time
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun ping(handler: (Duration) -> Unit, onFailure: FailureNotifier = ignoreFailure)

    @Throws(PixelblazeException::class)
    fun pingSync(): Duration

    /**
     * Specify whether the controller should send a preview of each render cycle. If sent they're handled in the
     * unrequested message handler.
     *
     * TODO: What's the default?
     *
     * @param sendEm
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun sendFramePreviews(sendEm: Boolean)

    /**
     * Utility function for interacting with the backend in arbitrary ways if they're not implemented in this library
     *
     * @param replyHandler handler to deal with the resulting message
     * @param request json to send to the backend
     * @return true if the request was dispatched, false otherwise.
     */
    @Throws(PixelblazeException::class)
    fun <Req, Resp>rawJsonRequest(request: Req, requestClass: Class<Req>) //TODO: Response handling?

    @Throws(PixelblazeException::class)
    fun rawBinaryRequest(requestType: BinaryMsgType, requestData: ByteArray, canBeSplit: Boolean)

    companion object {
        const val DEFAULT_PLAYLIST = "_defaultplaylist_"
    }
}



