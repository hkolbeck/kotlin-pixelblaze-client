package industries.hannah.pixelblaze

import com.google.gson.Gson
import io.ktor.websocket.*
import kotlin.math.min
import kotlin.math.roundToInt

sealed interface Outbound<R> {
    val frameType: FrameType
}

abstract class OutboundText<R : OutboundJsonMessage<*>>(
    val messageClass: Class<R>
) : Outbound<R> {
    override val frameType = FrameType.TEXT
}

object OutboundGetPlaylist : OutboundText<GetPlaylist>(GetPlaylist::class.java)
object OutboundSetPlaylistPosition : OutboundText<SetPlaylistPosition>(SetPlaylistPosition::class.java)
object OutboundGetPatternControls : OutboundText<GetPatternControls>(GetPatternControls::class.java)
object OutboundGetPreviewImage : OutboundText<GetPreviewImage>(GetPreviewImage::class.java)
object OutboundSetSendUpdates : OutboundText<SetSendUpdates>(SetSendUpdates::class.java)
object OutboundSetCurrentPatternControls :
    OutboundText<SetCurrentPatternControls>(SetCurrentPatternControls::class.java)

object OutboundSetBrightness : OutboundText<SetBrightness>(SetBrightness::class.java)
object OutboundSetMaxBrightness : OutboundText<SetMaxBrightness>(SetMaxBrightness::class.java)
object OutboundSetPixelCount : OutboundText<SetPixelCount>(SetPixelCount::class.java)
object OutboundGetAllPrograms : OutboundText<GetAllPrograms>(GetAllPrograms::class.java)
object OutboundNextPattern : OutboundText<NextPattern>(NextPattern::class.java)
object OutboundGetPeers : OutboundText<GetPeers>(GetPeers::class.java)
object OutboundGetSystemState : OutboundText<GetSystemState>(GetSystemState::class.java)

object OutboundSetRunSequencer : OutboundText<SetRunSequencer>(SetRunSequencer::class.java)
object OutboundPing : OutboundText<Ping>(Ping::class.java)
class OutboundRawText<R : OutboundJsonMessage<*>>(messageClass: Class<R>) : OutboundText<R>(messageClass)


abstract class OutboundBinary<R>(
    val binaryFlag: Byte,
    val canBeSplit: Boolean
) : Outbound<R> {
    override val frameType = FrameType.BINARY
}

class OutboundRawBinary(binaryFlag: Byte, canBeSplit: Boolean) : OutboundBinary<ByteArray>(binaryFlag, canBeSplit)

sealed interface OutboundMessage<Context, Msg> {
    val type: Outbound<out OutboundMessage<Context, Msg>>

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
    override val type: Outbound<OutboundMessage<BinarySerializationContext, ByteArray>>,
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

abstract class RawMapMessage<K>(private val map: Map<K, Any>) :
    OutboundJsonMessage<Map<String, Any>>() {

    override fun serialize(gson: Gson): String = gson.toJson(map)
}

abstract class TrivialJsonMessage<V>(key: String, value: V) :
    OutboundJsonMessage<Map<String, V>>() {
    private val body = mapOf(Pair(key, value))

    override fun serialize(gson: Gson): String = gson.toJson(body)
}

class GetPlaylist(id: String) : TrivialJsonMessage<String>("getPlaylist", id) {
    override val type = OutboundGetPlaylist
}

class SetPlaylistPosition(position: UInt) : TrivialJsonMessage<Int>("position", position.toInt()) {
    override val type = OutboundSetPlaylistPosition
}

class GetPatternControls(id: String) : TrivialJsonMessage<String>("patternId", id) {
    override val type = OutboundGetPatternControls
}

class GetPreviewImage(id: String) : TrivialJsonMessage<String>("patternId", id) {
    override val type = OutboundGetPreviewImage
}

class SetSendUpdates(sendUpdates: Boolean) : TrivialJsonMessage<Boolean>("sendUpdates", sendUpdates) {
    override val type = OutboundSetSendUpdates
}

class SetRunSequencer(play: Boolean) : TrivialJsonMessage<Boolean>("runSequencer", play) {
    override val type = OutboundSetRunSequencer
}
abstract class SaveOptionalSet<V>(key: String, value: V, save: Boolean) : OutboundJsonMessage<Map<String, Any>>() {
    private val body = mapOf(
        Pair(key, value),
        Pair("save", save)
    )

    override fun serialize(gson: Gson): String {
        return gson.toJson(body)
    }
}

class SetCurrentPatternControls(
    controls: Map<String, Float>,
    saveToFlash: Boolean
) : SaveOptionalSet<Map<String, Float>>("controls", controls, saveToFlash) {
    override val type = OutboundSetCurrentPatternControls
}

class SetBrightness(
    brightness: Float,
    saveToFlash: Boolean
) : SaveOptionalSet<Float>("brightness", brightness, saveToFlash) {
    override val type = OutboundSetBrightness
}

class SetMaxBrightness(
    brightness: Float,
    saveToFlash: Boolean
) : SaveOptionalSet<Int>("maxBrightness", (brightness.coerceIn(0f, 1f) * 255).roundToInt(), saveToFlash) {
    override val type = OutboundSetMaxBrightness
}

class SetPixelCount(
    pixelCount: UInt,
    saveToFlash: Boolean
) : SaveOptionalSet<Int>("pixelCount", pixelCount.toInt(), saveToFlash) {
    override val type = OutboundSetPixelCount
}


abstract class StringLiteralTextMessage(private val str: String) : OutboundJsonMessage<String>() {
    override fun serialize(gson: Gson): String {
        return str
    }
}

object GetAllPrograms : StringLiteralTextMessage("""{"listPrograms": true}""") {
    override val type = OutboundGetAllPrograms
}

object NextPattern : StringLiteralTextMessage("""{"nextProgram": true}""") {
    override val type = OutboundNextPattern
}

object GetPeers : StringLiteralTextMessage("""{"getPeers": 1}""") {
    override val type = OutboundGetPeers
}

object GetSystemState : StringLiteralTextMessage("""{"getConfig": true}""") {
    override val type = OutboundGetSystemState
}

object Ping : StringLiteralTextMessage("""{"ping": true}""") {
    override val type = OutboundPing
}
















