package industries.hannah.pixelblaze

import com.squareup.moshi.JsonClass
import java.awt.Image

sealed class Response

//JSON Responses

@JsonClass(generateAdapter = true)
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
) : Response()

@JsonClass(generateAdapter = true)
data class Control(
    val name: String,
    val value: Float,
)

@JsonClass(generateAdapter = true)
data class SequencerState(
    val name: String,
    val activeProgramId: String,
    val controls: List<Control>,
    val sequencerMode: SequencerMode,
    val runSequencer: Boolean,
    val playlistPos: Int,
    val playlistId: String,
    val ttlMs: Int,
    val remainingMs: Int,
) : Response()

@JsonClass(generateAdapter = true)
data class Settings(
    val name: String,
    val brandName: String,
    val pixelCount: Int,
    val brightness: Float,
    val maxBrightness: Int,
    val colorOrder: String,
    val dataSpeedHz: Int,
    val ledType: LedType,
    val sequenceTimerMs: Int,
    val transitionDurationMs: Int,
    val sequencerMode: Int,
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
) : Response()

@JsonClass(generateAdapter = true)
data class Peer(
    val id: Int,
    val ipAddress: String,
    val name: String,
    val version: String,
    val isFollowing: Boolean,
    val nodeId: Int,
    val followerCount: UInt,
)

@JsonClass(generateAdapter = true)
data class PeerResponse(
    val peers: List<Peer>
) : Response()

@JsonClass(generateAdapter = true)
data class PixelblazePattern(
    val id: String,
    val durationMs: Int,
)

@JsonClass(generateAdapter = true)
data class Playlist(
    val id: String,
    val position: Int,
    val currentDurationMs: Int,
    val remainingCurrentMs: Int,
    val patterns: List<PixelblazePattern>,
    val numItems: Int,
) : Response()

@JsonClass(generateAdapter = true)
data class PlaylistUpdate(
    val id: String,
    val patterns: List<PixelblazePattern>,
) : Response()


// Binary responses

data class ExpanderChannel(
    val channelId: UByte,
    val ledType: LedType,
    val numElements: UByte,
    val colorOrder: ColorOrder,
    val pixels: UInt,
    val startIndex: UShort,
    val frequency: UInt,
)

data class ExpanderChannels(
    val channels: List<ExpanderChannel>
) : Response()

data class PreviewImage(
    val img: Image
) : Response()

data class Pixel(
    val red: Byte,
    val green: Byte,
    val blue: Byte
)

data class PreviewFrame(
    val pixels: List<Pixel>
) : Response()

data class AllPatterns(
    val patterns: List<PixelblazePattern>
) : Response()