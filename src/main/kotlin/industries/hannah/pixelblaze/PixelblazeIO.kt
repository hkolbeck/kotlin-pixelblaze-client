@file:OptIn(ExperimentalUnsignedTypes::class)

package industries.hannah.pixelblaze

import com.google.gson.JsonObject
import io.ktor.websocket.*
import java.io.InputStream
import java.time.Instant
import javax.imageio.ImageIO

internal sealed class PixelblazeIO(
    val responseTypeKey: ResponseTypeKey,
    val failureNotifier: FailureNotifier
) {
    var requestAt: Instant? = null
    var satisfied = false
}

internal class NoResponseIO<Req : Request>(
    val request: Req,
    failureNotifier: FailureNotifier
) : PixelblazeIO(NoExpectedResponse, failureNotifier)

internal sealed class BinaryResponseIO<Req : Request>(
    val request: Req,
    val binResponseTypeKey: BinaryResponseTypeKey,
    failureNotifier: FailureNotifier
) : PixelblazeIO(
    binResponseTypeKey,
    failureNotifier
) {
    abstract fun handleRaw(inputStream: InputStream)
}

internal sealed class TextResponseIO<Req : Request>(
    val request: Req,
    val jsonResponseTypeKey: JsonResponseTypeKey,
    failureNotifier: FailureNotifier
) : PixelblazeIO(
    jsonResponseTypeKey,
    failureNotifier
) {

    abstract fun extractAndHandle(responseJson: JsonObject)
}

internal class RawTextResponseIO<Req : Request>(
    request: Req,
    matcher: (JsonObject) -> Boolean,
    private val handler: (JsonObject) -> Unit,
    failureNotifier: FailureNotifier,
) : TextResponseIO<Req>(request, JsonResponseTypeKey(matcher), failureNotifier) {
    override fun extractAndHandle(responseJson: JsonObject) {
        handler(responseJson)
    }
}

internal class SeqStateIo<Req : Request>(
    request: Req,
    private val handler: (SequencerState) -> Unit,
    failureNotifier: FailureNotifier,
) : TextResponseIO<Req>(
    request,
    JsonResponseTypeKey(matches),
    failureNotifier
) {
    override fun extractAndHandle(responseJson: JsonObject) {

//        JsonObject activeProgram = json["activeProgram"];
//        sequencerState.name = activeProgram["name"].as<String>();
//        sequencerState.activeProgramId = activeProgram["activeProgramId"].as<String>();
//
//        JsonObject controlsObj = activeProgram["controls"];
//        int controlIdx = 0;
//        for (JsonPair kv: controlsObj) {
//            sequencerState.controls[controlIdx].name = kv.key().c_str();
//            sequencerState.controls[controlIdx].value = kv.value();
//            controlIdx++;
//            if (controlIdx >= clientConfig.controlLimit) {
//                Serial.print(F("Got more controls than could be saved: "));
//                Serial.println(controlsObj.size());
//                break;
//            }
//        }
//        sequencerState.controlCount = controlIdx;
//
//        sequencerState.sequencerMode = sequencerModeFromInt(json["sequencerMode"]);
//        sequencerState.runSequencer = json["runSequencer"];
//
//        JsonObject playlistObj = json["playlist"];
//        sequencerState.playlistPos = playlistObj["position"];
//        sequencerState.playlistId = playlistObj["id"].as<String>();
//        sequencerState.ttlMs = playlistObj["ms"];
//        sequencerState.remainingMs = playlistObj["remainingMs"];




        TODO()
        handler()
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
) : TextResponseIO<Req>(
    request,
    JsonResponseTypeKey(matches),
    failureNotifier
) {
    override fun extractAndHandle(responseJson: JsonObject) {
        TODO()
        handler()
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
) : BinaryResponseIO<Req>(
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
) : TextResponseIO<JsonRequest<PeersReq>>(
    JsonRequest(PeersReq),
    JsonResponseTypeKey(matches),
    failureNotifier
) {
    override fun extractAndHandle(responseJson: JsonObject) {
        TODO()
        handler()
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
) : TextResponseIO<JsonRequest<PlaylistReq>>(
    JsonRequest(PlaylistReq(playlistId)),
    JsonResponseTypeKey(matches),
    failureNotifier
) {
    override fun extractAndHandle(responseJson: JsonObject) {
        TODO()
        handler()
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
) : BinaryResponseIO<JsonRequest<GetPreviewImageReq>>(
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
) : BinaryResponseIO<JsonRequest<AllPatternsReq>>(
    JsonRequest(AllPatternsReq),
    BinaryResponseTypeKey(BinaryMsgType.PreviewImage),
    failureNotifier
) {
    override fun handleRaw(inputStream: InputStream) {
        TODO("Not yet implemented")
    }
}

class PixelblazeException(val failureCause: FailureCause, message: String, thrown: Throwable? = null) :
    Exception(message, thrown)

typealias FailureNotifier = (PixelblazeException) -> Unit

val ignoreFailure: FailureNotifier = { _ -> }