package industries.hannah.pixelblaze

import com.google.gson.Gson
import io.ktor.websocket.*
import kotlin.math.min
import kotlin.math.roundToInt

internal sealed interface Request

object NoopRequest : Request

internal class JsonRequest<R: JsonRequestBody>(
    private val body: R
) : Request {
    fun toFrame(gson: Gson): Frame {
        return Frame.Text(gson.toJson(body))
    }
}

internal sealed interface BinaryRequest : Request {
    fun serialize(): ByteArray

    fun getType(): BinaryMsgType
    fun getCanBeSplit(): Boolean

    fun toFrames(frameDataSize: Int): List<Frame>? {
        val canBeSplit = getCanBeSplit()
        val type = getType()
        val serialized = serialize()
        if (serialized.size > frameDataSize && !canBeSplit) {
            return null
        }

        if (serialized.size <= frameDataSize) {
            val withHeaders = ByteArray((if (canBeSplit) 2 else 1) + serialized.size)
            withHeaders[0] = type.typeVal
            if (canBeSplit) {
                withHeaders[1] = FramePosition.Only.typeVal
            }

            serialized.copyInto(withHeaders, if (canBeSplit) 2 else 1)
            return listOf(Frame.Binary(true, withHeaders))
        } else {
            val frames = ArrayList<Frame>(
                serialized.size / frameDataSize
                        + (serialized.size % frameDataSize).coerceIn(0, 1)
            )

            var startIdx = 0
            while (startIdx < serialized.size) {
                val thisFrameDataSize = min(frameDataSize, serialized.size - startIdx)
                val isLast = startIdx + thisFrameDataSize >= serialized.size
                val frameData = ByteArray((if (canBeSplit) 2 else 1) + thisFrameDataSize)
                frameData[0] = type.typeVal
                if (canBeSplit) {
                    frameData[1] =
                        if (startIdx == 0) FramePosition.First.typeVal
                        else if (isLast) FramePosition.Last.typeVal
                        else FramePosition.Middle.typeVal
                }

                serialized.copyInto(frameData, if (canBeSplit) 2 else 1, startIdx, startIdx + thisFrameDataSize)

                // Pixelblaze does its own management of frame sequences, so fin is always true
                frames.add(Frame.Binary(true, frameData))
                startIdx += thisFrameDataSize
            }
            return frames
        }
    }
}

internal class RawBinaryReq(
    private val type: BinaryMsgType,
    private val canBeSplit: Boolean,
    private val body: ByteArray
) : BinaryRequest {
    override fun serialize(): ByteArray {
        return body
    }

    override fun getType(): BinaryMsgType {
        return type
    }

    override fun getCanBeSplit(): Boolean {
        return canBeSplit
    }
}


internal sealed interface JsonRequestBody


internal object AllPatternsReq : JsonRequestBody {
    val listPrograms = true
}


internal class PlaylistReq(
    val getPlaylist: String
) : JsonRequestBody


internal class SetPlaylistPositionReq(
    position: UInt
) : JsonRequestBody {
    val position = position.toInt()
}


internal object NextPatternReq : JsonRequestBody {
    val nextProgram = true
}


internal object PeersReq : JsonRequestBody {
    val getPeers = 1
}


internal class SetCurrentPatternControlsReq(
    val controls: List<Control>,
    val saveToFlash: Boolean
) : JsonRequestBody


internal class SetBrightnessReq(
    val brightness: Float,
    val saveToFlash: Boolean
) : JsonRequestBody


internal class GetPatternControlsReq(
    val patternId: String //getControls
) : JsonRequestBody


internal class GetPreviewImageReq(
    val patternId: String //getPreviewImg
) : JsonRequestBody


internal class SetMaxBrightnessReq(
    brightness: Float,
    val saveToFlash: Boolean
) : JsonRequestBody {
    val brightness = (brightness.coerceIn(0f, 1f) * 255).roundToInt()
}


internal class SetPixelCountReq(
    pixelCount: UInt,
    val saveToFlash: Boolean
) : JsonRequestBody {
    val pixelCount = pixelCount.toInt()
}


internal object GetSystemStateReq : JsonRequestBody {
    val getConfig = true
}


internal object PingReq : JsonRequestBody {
    val ping = true
}


internal class SendUpdatesReq(
    val sendUpdates: Boolean
) : JsonRequestBody
