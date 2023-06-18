package industries.hannah.pixelblaze

import com.google.gson.Gson
import io.ktor.websocket.*
import kotlin.math.min
import kotlin.math.roundToInt

sealed interface Outbound<R> {
    val frameType: FrameType
}

abstract class OutboundJson<R : OutboundJsonMessage<*>>(
    val messageClass: Class<R>
) : Outbound<R> {
    override val frameType = FrameType.TEXT
}

object OutboundGetPlaylistJson : OutboundJson<GetPlaylist>(GetPlaylist::class.java)
object OutboundSetPlaylistPositionJson : OutboundJson<SetPlaylistPosition>(SetPlaylistPosition::class.java)
object OutboundGetPatternControlsJson : OutboundJson<GetPatternControls>(GetPatternControls::class.java)
object OutboundGetPreviewImageJson : OutboundJson<GetPreviewImage>(GetPreviewImage::class.java)
object OutboundSetSendUpdatesJson : OutboundJson<SetSendUpdates>(SetSendUpdates::class.java)
object OutboundSetCurrentPatternControlsJson :
    OutboundJson<SetCurrentPatternControls>(SetCurrentPatternControls::class.java)

object OutboundSetBrightnessJson : OutboundJson<SetBrightness>(SetBrightness::class.java)
object OutboundSetMaxBrightnessJson : OutboundJson<SetMaxBrightness>(SetMaxBrightness::class.java)
object OutboundSetPixelCountJson : OutboundJson<SetPixelCount>(SetPixelCount::class.java)
object OutboundGetAllProgramsJson : OutboundJson<GetAllPrograms>(GetAllPrograms::class.java)
object OutboundNextPatternJson : OutboundJson<NextPattern>(NextPattern::class.java)
object OutboundGetPeersJson : OutboundJson<GetPeers>(GetPeers::class.java)
object OutboundGetSystemStateJson : OutboundJson<GetSystemState>(GetSystemState::class.java)
object OutboundPingJson : OutboundJson<Ping>(Ping::class.java)
class OutboundRawJson<R : OutboundJsonMessage<*>>(messageClass: Class<R>) : OutboundJson<R>(messageClass)


abstract class OutboundBinary<R>(
    val binaryFlag: Byte,
    val canBeSplit: Boolean
) : Outbound<R> {
    override val frameType = FrameType.BINARY
}

class OutboundRawBinary(binaryFlag: Byte, canBeSplit: Boolean) : OutboundBinary<ByteArray>(binaryFlag, canBeSplit)

sealed interface OutboundMessage<Context, Msg> {
    fun toFrames(context: Context): List<Frame>?
}

data class BinarySerializationContext(
    val frameDataSize: Int,
    val binaryFlag: Byte,
    val canBeSplit: Boolean
)

abstract class OutboundBinaryMessage<Msg> : OutboundMessage<BinarySerializationContext, Msg> {

    abstract fun serialize(): ByteArray

    override fun toFrames(context: BinarySerializationContext): List<Frame>? {
        val serialized = serialize()
        if (serialized.size > context.frameDataSize && !context.canBeSplit) {
            return null
        }

        if (serialized.size <= context.frameDataSize) {
            val withHeaders = ByteArray((if (context.canBeSplit) 2 else 1) + serialized.size)
            withHeaders[0] = context.binaryFlag
            if (context.canBeSplit) {
                withHeaders[1] = FramePosition.Only.typeVal
            }

            serialized.copyInto(withHeaders, if (context.canBeSplit) 2 else 1)
            return listOf(Frame.Binary(true, withHeaders))
        } else {
            val frames = ArrayList<Frame>(
                serialized.size / context.frameDataSize
                        + (serialized.size % context.frameDataSize).coerceIn(0, 1)
            )

            var startIdx = 0
            while (startIdx < serialized.size) {
                val thisFrameDataSize = min(context.frameDataSize, serialized.size - startIdx)
                val isLast = startIdx + thisFrameDataSize >= serialized.size
                val frameData = ByteArray((if (context.canBeSplit) 2 else 1) + thisFrameDataSize)
                frameData[0] = context.binaryFlag
                if (context.canBeSplit) {
                    frameData[1] =
                        if (startIdx == 0) FramePosition.First.typeVal
                        else if (isLast) FramePosition.Last.typeVal
                        else FramePosition.Middle.typeVal
                }

                serialized.copyInto(frameData, if (context.canBeSplit) 2 else 1, startIdx, startIdx + thisFrameDataSize)

                // Pixelblaze does its own management of frame sequences, so fin is always true
                frames.add(Frame.Binary(true, frameData))
                startIdx += thisFrameDataSize
            }
            return frames
        }
    }
}

class OutboundRawBinaryMessage(
    private val body: ByteArray
) : OutboundBinaryMessage<ByteArray>() {
    override fun serialize(): ByteArray {
        return body
    }
}

abstract class OutboundJsonMessage<M> : OutboundMessage<Gson, M> {
    abstract fun serialize(gson: Gson): String

    override fun toFrames(context: Gson): List<Frame> {
        val serialized = serialize(context)
        return listOf(Frame.Text(serialized))
    }
}

open class TrivialJsonMessage<V>(key: String, value: V) :
    OutboundJsonMessage<Map<String, V>>() {
    private val body = mapOf(Pair(key, value))

    override fun serialize(gson: Gson): String {
        return gson.toJson(body)
    }
}

class GetPlaylist(id: String) : TrivialJsonMessage<String>("getPlaylist", id)
class SetPlaylistPosition(position: UInt) : TrivialJsonMessage<Int>("position", position.toInt())
class GetPatternControls(id: String) : TrivialJsonMessage<String>("patternId", id)
class GetPreviewImage(id: String) : TrivialJsonMessage<String>("patternId", id)
class SetSendUpdates(sendUpdates: Boolean) : TrivialJsonMessage<Boolean>("sendUpdates", sendUpdates)


open class SaveOptionalSet<V>(key: String, value: V, save: Boolean) : OutboundJsonMessage<Map<String, Any>>() {
    private val body = mapOf(
        Pair(key, value),
        Pair("save", save)
    )

    override fun serialize(gson: Gson): String {
        return gson.toJson(body)
    }
}

class SetCurrentPatternControls(
    controls: List<Control>,
    saveToFlash: Boolean
) : SaveOptionalSet<List<Control>>("controls", controls, saveToFlash)

class SetBrightness(
    brightness: Float,
    saveToFlash: Boolean
) : SaveOptionalSet<Float>("brightness", brightness, saveToFlash)

class SetMaxBrightness(
    brightness: Float,
    saveToFlash: Boolean
) : SaveOptionalSet<Int>("maxBrightness", (brightness.coerceIn(0f, 1f) * 255).roundToInt(), saveToFlash)

class SetPixelCount(
    pixelCount: UInt,
    saveToFlash: Boolean
) : SaveOptionalSet<Int>("pixelCount", pixelCount.toInt(), saveToFlash)


open class StringLiteralJsonMessage(private val str: String) : OutboundJsonMessage<String>() {
    override fun serialize(gson: Gson): String {
        return str
    }
}

object GetAllPrograms : StringLiteralJsonMessage("""{"listPrograms": true}""")
object NextPattern : StringLiteralJsonMessage("""{"nextProgram": true}""")
object GetPeers : StringLiteralJsonMessage("""{"getPeers": 1}""")
object GetSystemState : StringLiteralJsonMessage("""{"getConfig": true}""")
object Ping : StringLiteralJsonMessage("""{"ping": true}""")
















