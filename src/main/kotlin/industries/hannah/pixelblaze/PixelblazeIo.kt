package industries.hannah.pixelblaze

import com.google.gson.JsonObject
import java.io.InputStream
import java.time.Instant
import javax.imageio.ImageIO

internal sealed class PixelblazeIo<Req : Request>(
    val request: Req,
    val responseTypeKey: ResponseTypeKey,
    val failureNotifier: FailureNotifier
) {
    var requestAt: Instant? = null
    var satisfied = false
}

internal class NoResponseIo<Req : Request>(
    request: Req,
    failureNotifier: FailureNotifier
) : PixelblazeIo<Req>(request, NoResponseManaged, failureNotifier)

internal sealed class BinaryResponseIo<Req : Request>(
    request: Req,
    val binResponseTypeKey: BinaryResponseTypeKey,
    failureNotifier: FailureNotifier
) : PixelblazeIo<Req>(
    request,
    binResponseTypeKey,
    failureNotifier
) {
    abstract fun handleRaw(inputStream: InputStream)
}

internal sealed class TextResponseIo<Req : Request>(
    request: Req,
    val jsonResponseTypeKey: JsonResponseTypeKey,
    failureNotifier: FailureNotifier
) : PixelblazeIo<Req>(
    request,
    jsonResponseTypeKey,
    failureNotifier
) {

    abstract fun extractAndHandle(responseJson: JsonObject)
}

internal class RawTextResponseIo<Req : Request>(
    request: Req,
    matcher: (JsonObject) -> Boolean,
    private val handler: (JsonObject) -> Unit,
    failureNotifier: FailureNotifier,
) : TextResponseIo<Req>(request, JsonResponseTypeKey(matcher), failureNotifier) {
    override fun extractAndHandle(responseJson: JsonObject) {
        handler(responseJson)
    }
}

internal class SeqStateIo<Req : Request>(
    request: Req,
    private val handler: (SequencerState) -> Unit,
    failureNotifier: FailureNotifier,
) : TextResponseIo<Req>(
    request,
    JsonResponseTypeKey(matches),
    failureNotifier
) {
    override fun extractAndHandle(responseJson: JsonObject) {
        val activeProgram = responseJson["activeProgram"].asJsonObject;
        val playlist = responseJson["playlist"].asJsonObject;

        val seqState = SequencerState(
            activeProgram = ActiveProgram(
                name = activeProgram["name"].asString,
                id = activeProgram["id"].asString,
                controls = activeProgram["controls"].asJsonObject.asMap().map {
                    Control(
                        name = it.key,
                        value = it.value.asFloat
                    )
                }
            ),
            playlistState = PlaylistState(
                position = playlist["position"].asInt,
                id = playlist["id"].asString,
                ttlMs = playlist["ms"].asInt,
                remainingMs = playlist["remainingMs"].asInt
            ),
            sequencerMode = SequencerMode.fromInt(responseJson["sequencerMode"].asInt) ?: SequencerMode.Off,
            runSequencer = responseJson["runSequencer"].asBoolean
        )

        handler(seqState)
    }

    companion object {
        val matches: (JsonObject) -> Boolean = {
            it.has("activeProgram")
        }
    }
}

internal class SettingIo<Req : Request>(
    request: Req,
    val handler: (Settings) -> Unit,
    failureNotifier: FailureNotifier,
) : TextResponseIo<Req>(
    request,
    JsonResponseTypeKey(matches),
    failureNotifier
) {
    override fun extractAndHandle(responseJson: JsonObject) {
        val settings = Settings(
            name = responseJson["name"].asString,
            brandName = responseJson["brandName"].asString,
            pixelCount = responseJson["pixelCount"].asInt,
            brightness = responseJson["brightness"].asFloat,
            maxBrightness = responseJson["maxBrightness"].asFloat / 100.0f,
            colorOrder = ColorOrder.fromString(responseJson["colorOrder"].asString) ?: ColorOrder.BGR,
            dataSpeedHz = responseJson["dataSpeedHz"].asInt,
            ledType = LedType.fromInt(responseJson["ledType"].asInt) ?: LedType.None,
            sequenceTimerMs = responseJson["sequenceTimer"].asInt,
            transitionDurationMs = responseJson["transitionDuration"].asInt,
            sequencerMode = SequencerMode.fromInt(responseJson["sequencerMode"].asInt) ?: SequencerMode.Off,
            runSequencer = responseJson["runSequencer"].asBoolean,
            simpleUiMode = responseJson["simpleUiMode"].asBoolean,
            learningUiMode = responseJson["learningUiMode"].asBoolean,
            discoveryEnabled = responseJson["discoveryEnable"].asBoolean,
            timezone = responseJson["timezone"].asString,
            autoOffEnable = responseJson["autoOffEnable"].asBoolean,
            autoOffStart = responseJson["autoOffStart"].asString,
            autoOffEnd = responseJson["autoOffEnd"].asString,
            cpuSpeedMhz = responseJson["cpuSpeed"].asInt,
            networkPowerSave = responseJson["networkPowerSave"].asBoolean,
            mapperFit = responseJson["mapperFit"].asInt,
            leaderId = responseJson["leaderId"].asInt,
            nodeId = responseJson["nodeId"].asInt,
            soundSrc = InputSource.fromInt(responseJson["soundSrc"].asInt) ?: InputSource.Local,
            lightSrc = InputSource.fromInt(responseJson["lightSrc"].asInt) ?: InputSource.Local,
            accelSrc = InputSource.fromInt(responseJson["accelSrc"].asInt) ?: InputSource.Local,
            analogSrc = InputSource.fromInt(responseJson["analogSrc"].asInt) ?: InputSource.Local,
            exp = responseJson["exp"].asInt,
            version = responseJson["ver"].asString,
            chipId = responseJson["chipId"].asInt
        )

        handler(settings)
    }

    companion object {
        val matches: (JsonObject) -> Boolean = {
            it.has("pixelCount")
        }
    }
}

internal class ExpanderIo<Req : Request>(
    request: Req,
    val handler: (List<ExpanderChannel>) -> Unit,
    failureNotifier: FailureNotifier,
) : BinaryResponseIo<Req>(
    request,
    BinaryResponseTypeKey(BinaryMsgType.ExpanderChannels), failureNotifier
) {

    override fun handleRaw(inputStream: InputStream) {
        TODO("Not yet implemented")
    }
}

internal class PeersIo(
    val handler: (List<Peer>) -> Unit,
    failureNotifier: FailureNotifier,
) : TextResponseIo<JsonRequest<PeersReq>>(
    JsonRequest(PeersReq),
    JsonResponseTypeKey(matches),
    failureNotifier
) {
    override fun extractAndHandle(responseJson: JsonObject) {
        val peers = responseJson["peers"].asJsonArray;
        val parsed = peers.map { element ->
            val peer = element.asJsonObject
            Peer(
                id = peer["id"].asInt,
                ipAddress = peer["address"].asString,
                name = peer["name"].asString,
                version = peer["ver"].asString,
                isFollowing = peer["isFollowing"].asInt != 0,
                nodeId = peer["nodeId"].asInt,
                followerCount = peer["followerCount"].asInt.toUInt()
            )
        }

        handler(parsed)
    }

    companion object {
        val matches: (JsonObject) -> Boolean = {
            it.has("peers")
        }
    }

}

internal class PlaylistIo(
    playlistId: String,
    val handler: (Playlist) -> Unit,
    failureNotifier: FailureNotifier,
) : TextResponseIo<JsonRequest<PlaylistReq>>(
    JsonRequest(PlaylistReq(playlistId)),
    JsonResponseTypeKey(matches),
    failureNotifier
) {
    override fun extractAndHandle(responseJson: JsonObject) {
        val playlistObj = responseJson["playlist"].asJsonObject
        val playlist = Playlist(
            id = playlistObj["id"].asString,
            position = playlistObj["position"].asInt,
            currentDurationMs = playlistObj["ms"].asInt,
            remainingCurrentMs = playlistObj["remainingMs"].asInt,
            patterns = playlistObj["items"].asJsonArray.map {
                val asObj = it.asJsonObject
                PixelblazePattern(
                    id = asObj["id"].asString,
                    durationMs = asObj["ms"].asInt
                )
            }
        )

        handler(playlist)
    }

    companion object {
        val matches: (JsonObject) -> Boolean = {
            it.has("playlist")
                    && it["playlist"].isJsonObject
                    && it["playlist"].asJsonObject.has("position")
        }
    }
}

internal class PreviewImageIo(
    private val patternId: String,
    private val handler: (PreviewImage) -> Unit,
    failureNotifier: FailureNotifier,
) : BinaryResponseIo<JsonRequest<GetPreviewImageReq>>(
    JsonRequest(GetPreviewImageReq(patternId)),
    BinaryResponseTypeKey(BinaryMsgType.PreviewImage),
    failureNotifier
) {
    override fun handleRaw(inputStream: InputStream) {

        //Discard ID header
        var read = inputStream.read()
        while (read in 1..254) {
            read = inputStream.read()
        }

        try {
            ImageIO.read(inputStream)?.run { handler(PreviewImage(patternId, this)) }
        } catch (t: Throwable) {
            failureNotifier(PixelblazeException(FailureCause.ResponseParseError, "Image parse error", t))
        }
    }
}

/**
 * Expander
 */

/**
 * All patterns
 * patternId1\tname1\npatternId2...
 *
 */
internal class AllPatternsIo(
    private val handler: (List<PixelblazePattern>) -> Unit,
    failureNotifier: FailureNotifier,
) : BinaryResponseIo<JsonRequest<AllPatternsReq>>(
    JsonRequest(AllPatternsReq),
    BinaryResponseTypeKey(BinaryMsgType.PreviewImage),
    failureNotifier
) {
    override fun handleRaw(inputStream: InputStream) {
        TODO("Not yet implemented")
    }
}

class PixelblazeException(val failureCause: FailureCause, message: String = "", thrown: Throwable? = null) :
    Exception(message, thrown)

typealias FailureNotifier = (PixelblazeException) -> Unit

val ignoreFailure: FailureNotifier = { _ -> }