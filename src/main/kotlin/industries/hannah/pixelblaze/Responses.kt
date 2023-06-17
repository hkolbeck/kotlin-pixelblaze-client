package industries.hannah.pixelblaze

import java.awt.image.BufferedImage

sealed class Response

//JSON Responses

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
) : Response()

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
) : Response()

data class Peer(
    val id: Int,
    val ipAddress: String,
    val name: String,
    val version: String,
    val isFollowing: Boolean,
    val nodeId: Int,
    val followerCount: UInt,
)

data class PeerResponse(
    val peers: List<Peer>
) : Response()

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
) : Response()

data class PlaylistUpdate(
    val id: String,
    val patterns: List<PixelblazePattern>,
) : Response()

object Ack

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
    val patternId: String,
    val img: BufferedImage
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