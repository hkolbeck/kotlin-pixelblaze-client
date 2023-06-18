package industries.hannah.pixelblaze

import io.ktor.websocket.*
import java.lang.reflect.Type

sealed interface Inbound<T : InboundMessage> {
    val frameType: FrameType
}


abstract class InboundBinary<T: InboundMessage>(val binaryFlag: Byte) : Inbound<T> {
    override val frameType = FrameType.BINARY
}

object InboundPreviewImage : InboundBinary<PreviewImage>(4)
object InboundPreviewFrame : InboundBinary<PreviewFrame>(5)
object InboundProgramList : InboundBinary<ProgramList>(7)
object InboundExpanderChannels : InboundBinary<ExpanderChannels>(9)
class InboundRawBinary<T: InboundMessage>(binaryFlag: Byte) : InboundBinary<T>(binaryFlag)


abstract class InboundJson<T: InboundMessage>(val extractedType: Type) : Inbound<T> {
    override val frameType = FrameType.TEXT
}

object InboundStats : InboundJson<Stats>(Stats::class.java)
object InboundSequencerState : InboundJson<SequencerState>(SequencerState::class.java)
object InboundPeers : InboundJson<PeerInboundMessage>(PeerInboundMessage::class.java)
object InboundPlayist : InboundJson<Playlist>(Playlist::class.java)
object InboundPlaylistUpdate : InboundJson<PlaylistUpdate>(PlaylistUpdate::class.java)
class InboundParsedJson<T: InboundMessage>(extractedType: Type) : InboundJson<T>(extractedType)
