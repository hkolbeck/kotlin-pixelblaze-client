package industries.hannah.pixelblaze

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.websocket.*
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.lang.reflect.Type
import javax.imageio.ImageIO
import kotlin.streams.toList

sealed interface Inbound<T : InboundMessage> {
    val frameType: FrameType
}

abstract class InboundBinary<T : InboundMessage>(val binaryFlag: Byte) : Inbound<T> {
    override val frameType = FrameType.BINARY
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InboundBinary<*>) return false

        if (binaryFlag != other.binaryFlag) return false
        if (frameType != other.frameType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = binaryFlag.toInt()
        result = 31 * result + frameType.hashCode()
        return result
    }

    override fun toString(): String = this.javaClass.name
}

object InboundPreviewImage : InboundBinary<PreviewImage>(4)

object InboundPreviewFrame : InboundBinary<PreviewFrame>(5) 

object InboundAllPrograms : InboundBinary<AllPrograms>(7) 

object InboundExpanderChannels : InboundBinary<ExpanderChannels>(9) 

class InboundRawBinary<T : InboundMessage>(binaryFlag: Byte) : InboundBinary<T>(binaryFlag)


abstract class InboundText<T : InboundMessage>(val extractedType: Type) : Inbound<T> {
    override val frameType = FrameType.TEXT
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InboundText<*>) return false

        if (extractedType != other.extractedType) return false
        if (frameType != other.frameType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = extractedType.hashCode()
        result = 31 * result + frameType.hashCode()
        return result
    }

    override fun toString(): String = this.javaClass.name
}

object InboundStats : InboundText<Stats>(Stats::class.java) 
object InboundSequencerState : InboundText<SequencerState>(SequencerState::class.java) 
object InboundSettings : InboundText<Settings>(Settings::class.java) 
object InboundPeers : InboundText<Peers>(Peers::class.java) 
object InboundPlaylist : InboundText<Playlist>(Playlist::class.java) 
object InboundPlaylistUpdate : InboundText<PlaylistUpdate>(PlaylistUpdate::class.java) 
object InboundAck : InboundText<Ack>(Ack::class.java) 
class InboundParsedText<T : InboundMessage>(extractedType: Type) : InboundText<T>(extractedType) 

interface InboundMessage

//JSON Inbound messages
//TODO: Parse code copied from C++, move to gson annotations?

data class Stats(
    val fps: Float,
    val vmerr: Int,
    val vmerrpc: Int,
    val memBytes: Int,
    val expansions: Int,
    val renderType: RenderType,
    val uptimeMs: Int,
    val storageBytesUsed: Int,
    val storageBytesSize: Int,
    val rr0: Int,
    val rr1: Int,
    val rebootCounter: Int,
) : InboundMessage {
    companion object {
        fun fromText(gson: Gson, string: String): Stats? {
            val json = gson.fromJson(string, JsonObject::class.java)

            if (json.has("fps")) {
                return Stats(
                    fps = json["fps"].asFloat,
                    vmerr = json["vmerr"].asInt,
                    vmerrpc = json["vmerrpc"].asInt,
                    memBytes = json["mem"].asInt,
                    expansions = json["exp"].asInt,
                    renderType = RenderType.fromInt(json["renderType"].asInt) ?: RenderType.Invalid,
                    uptimeMs = json["uptime"].asInt,
                    storageBytesUsed = json["storageUsed"].asInt,
                    storageBytesSize = json["storageSize"].asInt,
                    rr0 = json["rr0"].asInt,
                    rr1 = json["rr1"].asInt,
                    rebootCounter = json["rebootCounter"].asInt
                )
            } else {
                return null
            }
        }
    }
}

data class Control(
    val name: String,
    val value: Float,
)

data class ActiveProgram(
    val name: String,
    val id: String,
    val controls: List<Control>,
)

data class PlaylistState(
    val position: Int,
    val id: String,
    val ttlMs: Int,
    val remainingMs: Int,
)

data class SequencerState(
    val activeProgram: ActiveProgram,
    val playlistState: PlaylistState,
    val sequencerMode: SequencerMode,
    val runSequencer: Boolean
) : InboundMessage {
    companion object {
        fun fromText(gson: Gson, string: String): SequencerState? {
            val json = gson.fromJson(string, JsonObject::class.java)

            if (!json.has("activeProgram")) {
                return null
            }

            val activeProgram = json["activeProgram"].asJsonObject;
            val playlist = json["playlist"].asJsonObject;

            return SequencerState(
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
                sequencerMode = SequencerMode.fromInt(json["sequencerMode"].asInt) ?: SequencerMode.Off,
                runSequencer = json["runSequencer"].asBoolean
            )
        }
    }
}

data class Settings(
    val name: String,
    val brandName: String,
    val pixelCount: Int,
    val brightness: Float,
    val maxBrightness: Float,
    val colorOrder: ColorOrder,
    val dataSpeedHz: Int,
    val ledType: LedType,
    val sequenceTimerMs: Int,
    val transitionDurationMs: Int,
    val sequencerMode: SequencerMode,
    val runSequencer: Boolean,
    val simpleUiMode: Boolean,
    val learningUiMode: Boolean,
    val discoveryEnabled: Boolean,
    val timezone: String,
    val autoOffEnable: Boolean,
    val autoOffStart: String,
    val autoOffEnd: String,
    val cpuSpeedMhz: Int,
    val networkPowerSave: Boolean,
    val mapperFit: Int,
    val leaderId: Int,
    val nodeId: Int,
    val soundSrc: InputSource,
    val accelSrc: InputSource,
    val lightSrc: InputSource,
    val analogSrc: InputSource,
    val exp: Int,
    val version: String,
    val chipId: Int,
) : InboundMessage {
    companion object {
        fun fromText(gson: Gson, string: String): Settings? {
            val json = gson.fromJson(string, JsonObject::class.java)

            if (!json.has("pixelCount")) {
                return null
            }

            return Settings(
                name = json["name"].asString,
                brandName = json["brandName"].asString,
                pixelCount = json["pixelCount"].asInt,
                brightness = json["brightness"].asFloat,
                maxBrightness = json["maxBrightness"].asFloat / 100.0f,
                colorOrder = ColorOrder.fromString(json["colorOrder"].asString) ?: ColorOrder.BGR,
                dataSpeedHz = json["dataSpeedHz"].asInt,
                ledType = LedType.fromInt(json["ledType"].asInt) ?: LedType.None,
                sequenceTimerMs = json["sequenceTimer"].asInt,
                transitionDurationMs = json["transitionDuration"].asInt,
                sequencerMode = SequencerMode.fromInt(json["sequencerMode"].asInt) ?: SequencerMode.Off,
                runSequencer = json["runSequencer"].asBoolean,
                simpleUiMode = json["simpleUiMode"].asBoolean,
                learningUiMode = json["learningUiMode"].asBoolean,
                discoveryEnabled = json["discoveryEnable"].asBoolean,
                timezone = json["timezone"].asString,
                autoOffEnable = json["autoOffEnable"].asBoolean,
                autoOffStart = json["autoOffStart"].asString,
                autoOffEnd = json["autoOffEnd"].asString,
                cpuSpeedMhz = json["cpuSpeed"].asInt,
                networkPowerSave = json["networkPowerSave"].asBoolean,
                mapperFit = json["mapperFit"].asInt,
                leaderId = json["leaderId"].asInt,
                nodeId = json["nodeId"].asInt,
                soundSrc = InputSource.fromInt(json["soundSrc"].asInt) ?: InputSource.Local,
                lightSrc = InputSource.fromInt(json["lightSrc"].asInt) ?: InputSource.Local,
                accelSrc = InputSource.fromInt(json["accelSrc"].asInt) ?: InputSource.Local,
                analogSrc = InputSource.fromInt(json["analogSrc"].asInt) ?: InputSource.Local,
                exp = json["exp"].asInt,
                version = json["ver"].asString,
                chipId = json["chipId"].asInt
            )
        }
    }
}

data class Peer(
    val id: Int,
    val ipAddress: String,
    val name: String,
    val version: String,
    val isFollowing: Boolean,
    val nodeId: Int,
    val followerCount: UInt,
)

data class Peers(
    val peers: List<Peer>
) : InboundMessage {
    companion object {
        fun fromText(gson: Gson, string: String): Peers? {
            val json = gson.fromJson(string, JsonObject::class.java)

            if (!json.has("peers")) {
                return null
            }

            val peers = json["peers"].asJsonArray;
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

            return Peers(parsed)
        }
    }
}

data class PixelblazePattern(
    val id: String,
    val durationMs: Int,
)

data class Playlist(
    val id: String,
    val position: Int,
    val currentDurationMs: Int,
    val remainingCurrentMs: Int,
    val patterns: List<PixelblazePattern>,
) : InboundMessage {
    companion object {
        fun fromText(gson: Gson, string: String): Playlist? {
            val json = gson.fromJson(string, JsonObject::class.java)

            if (
                !json.has("playlist")
                || !json["playlist"].isJsonObject
                || !json["playlist"].asJsonObject.has("position")
            ) {
                return null
            }

            val playlistObj = json["playlist"].asJsonObject
            return Playlist(
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
        }
    }
}

data class PlaylistUpdate(
    val id: String,
    val patterns: List<PixelblazePattern>,
) : InboundMessage {
    companion object {
        fun fromText(gson: Gson, string: String): PlaylistUpdate? {
            val json = gson.fromJson(string, JsonObject::class.java)

            if (
                !json.has("playlist")
                || !json["playlist"].isJsonObject
                || json["playlist"].asJsonObject.has("position")
            ) {
                return null
            }

            val playlistObj = json["playlist"].asJsonObject
            return PlaylistUpdate(
                id = playlistObj["id"].asString,
                patterns = playlistObj["items"].asJsonArray.map {
                    val asObj = it.asJsonObject
                    PixelblazePattern(
                        id = asObj["id"].asString,
                        durationMs = asObj["ms"].asInt
                    )
                }
            )
        }
    }
}

object Ack : InboundMessage {
    fun fromText(gson: Gson, string: String): Ack? {
        val json = gson.fromJson(string, JsonObject::class.java)

        return if (json.has("ack")) {
            Ack
        } else {
            null
        }
    }
}

data class RawJsonInboundMessage(
    val json: JsonObject
) : InboundMessage

// Binary responses

data class ExpanderChannel(
    val channelId: UByte,
    val ledType: LedType,
    val numElements: UByte,
    val colorOrder: ColorOrder,
    val pixels: UInt,
    val startIndex: UInt,
    val frequency: UInt,
)

const val EXPANDER_CHANNEL_BYTE_WIDTH = 12

data class ExpanderChannels(
    val channels: List<ExpanderChannel>
) : InboundMessage {
    companion object {
        fun fromBinary(stream: InputStream): ExpanderChannels? {
            val channelBuffer = ByteArray(EXPANDER_CHANNEL_BYTE_WIDTH)
            val channels = ArrayList<ExpanderChannel>(8) //Just an initial capacity
            var read = stream.read(channelBuffer)
            while (read == EXPANDER_CHANNEL_BYTE_WIDTH) {
                val dataStream = DataInputStream(ByteArrayInputStream(channelBuffer))
                channels.add(
                    ExpanderChannel(
                        channelId = dataStream.readUnsignedByte().toUByte(),
                        ledType = LedType.fromInt(dataStream.readUnsignedByte()) ?: LedType.None,
                        numElements = dataStream.readUnsignedByte().toUByte(),
                        colorOrder = ColorOrder.fromInt(dataStream.readUnsignedByte()) ?: ColorOrder.BGR,
                        pixels = dataStream.readUnsignedShort().toUInt(),
                        startIndex = dataStream.readUnsignedShort().toUInt(),
                        frequency = dataStream.readInt().toUInt()
                    )
                )
                read = stream.read(channelBuffer)
            }

            return ExpanderChannels(channels)
        }
    }
}

data class PreviewImage(
    val patternId: String,
    val img: BufferedImage
) : InboundMessage {
    companion object {
        fun fromBinary(stream: InputStream): PreviewImage? {
            val imageIdBuffer = ArrayList<Byte>(16)
            var read = stream.read()
            while (read in 1..254) {
                imageIdBuffer.add(read.toByte())
                read = stream.read()
            }

            return try {
                PreviewImage(String(imageIdBuffer.toByteArray()), ImageIO.read(stream))
            } catch (t: Throwable) {
                null
            }
        }
    }
}

data class Pixel(
    val red: UByte,
    val green: UByte,
    val blue: UByte
) {
    fun toInt(): Int = (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
}

//Per creator
const val PREVIEW_FRAME_MAX_LEN = 1024

data class PreviewFrame(
    val pixels: List<Pixel>
) : InboundMessage {

    fun toImage(width: UInt, height: UInt): Image {
        val buffer = pixels.map { it.toInt() }.toIntArray()
        val image = BufferedImage(width.toInt(), height.toInt(), BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, buffer.size, 1, buffer, 0, 1)

        return image.getScaledInstance(width.toInt(), height.toInt(), Image.SCALE_FAST)
    }

    companion object {
        fun fromBinary(stream: InputStream): PreviewFrame? {
            val pixels = ArrayList<Pixel>(PREVIEW_FRAME_MAX_LEN)

            while (pixels.size < PREVIEW_FRAME_MAX_LEN) {
                val red = stream.read()
                val green = stream.read()
                val blue = stream.read()

                if (red < 0 || green < 0 || blue < 0) {
                    break;
                }

                pixels.add(Pixel(red.toUByte(), green.toUByte(), blue.toUByte()))
            }

            return PreviewFrame(pixels)
        }
    }
}

data class NamedPattern(
    val id: String,
    val name: String
)

data class AllPrograms(
    val patterns: List<NamedPattern>
) : InboundMessage {
    companion object {
        fun fromBinary(stream: InputStream): AllPrograms? {
            val patterns = stream.bufferedReader().lines().map {
                val (id, name) = it.split('\t', limit = 2)
                NamedPattern(id, name)
            }.toList()

            return AllPrograms(patterns)
        }
    }
}

data class RawBinaryInboundMessage(
    val stream: InputStream
) : InboundMessage {
    companion object {
        fun fromBinary(stream: InputStream): RawBinaryInboundMessage {
            return RawBinaryInboundMessage(stream)
        }
    }
}