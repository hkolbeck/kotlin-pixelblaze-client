package industries.hannah.pixelblaze

import java.time.Instant

internal sealed class PixelblazeIo<Req : Outbound>(
    val request: Req,
    val failureNotifier: FailureNotifier
) {
    abstract val inboundFrame: InboundFrame?

    var requestAt: Instant? = null
    var satisfied = false
}

internal class NoResponseIo<Req : Outbound>(
    request: Req,
    failureNotifier: FailureNotifier
) : PixelblazeIo<Req>(request, failureNotifier) {
    override val inboundFrame: NoResponseManaged = NoResponseManaged
}

internal sealed class BinaryResponseIo<Req : Outbound, Resp : InboundMessage>(
    request: Req,
    msgType: BinaryTypeFlag,
    val handler: (Resp) -> Unit,
    failureNotifier: FailureNotifier
) : PixelblazeIo<Req>(
    request,
    failureNotifier
) {
    override val inboundFrame: InboundBinaryFrame = InboundBinaryFrame(msgType)
}

internal sealed class JsonResponseIo<Req : Outbound, Resp : InboundMessage>(
    request: Req,
    respClass: Class<Resp>,
    val handler: (Resp) -> Unit,
    failureNotifier: FailureNotifier
) : PixelblazeIo<Req>(
    request,
    failureNotifier
) {
    override val inboundFrame: InboundJsonFrame = InboundJsonFrame(respClass)
}

internal class RawJsonResponseIo<Req : Outbound>(
    request: Req,
    handler: (RawJsonInboundMessage) -> Unit,
    failureNotifier: FailureNotifier,
) : JsonResponseIo<Req, RawJsonInboundMessage>(
    request,
    RawJsonInboundMessage::class.java,
    handler,
    failureNotifier
)

internal class SeqStateIo<Req : Outbound>(
    request: Req,
    handler: (SequencerState) -> Unit,
    failureNotifier: FailureNotifier,
) : JsonResponseIo<Req, SequencerState>(
    request,
    SequencerState::class.java,
    handler,
    failureNotifier
)

internal class SettingIo<Req : Outbound>(
    request: Req,
    handler: (Settings) -> Unit,
    failureNotifier: FailureNotifier,
) : JsonResponseIo<Req, Settings>(
    request,
    Settings::class.java,
    handler,
    failureNotifier
)


internal class ExpanderIo<Req : Outbound>(
    request: Req,
    handler: (ExpanderChannels) -> Unit,
    failureNotifier: FailureNotifier,
) : BinaryResponseIo<Req, ExpanderChannels>(
    request,
    BinaryTypeFlag.ExpanderChannels,
    handler,
    failureNotifier
)

internal class PeersIo(
    handler: (PeerInboundMessage) -> Unit,
    failureNotifier: FailureNotifier,
) : JsonResponseIo<OutboundJson<PeersReq>, PeerInboundMessage>(
    OutboundJson(PeersReq),
    PeerInboundMessage::class.java,
    handler,
    failureNotifier
)

internal class PlaylistIo(
    playlistId: String,
    handler: (Playlist) -> Unit,
    failureNotifier: FailureNotifier,
) : JsonResponseIo<OutboundJson<PlaylistReq>, Playlist>(
    OutboundJson(PlaylistReq(playlistId)),
    Playlist::class.java,
    handler,
    failureNotifier
)

internal class PreviewImageIo(
    patternId: String,
    handler: (PreviewImage) -> Unit,
    failureNotifier: FailureNotifier,
) : BinaryResponseIo<OutboundJson<GetPreviewImageReq>, PreviewImage>(
    OutboundJson(GetPreviewImageReq(patternId)),
    BinaryTypeFlag.PreviewImage,
    handler,
    failureNotifier
)

internal class AllPatternsIo(
    handler: (ProgramList) -> Unit,
    failureNotifier: FailureNotifier,
) : BinaryResponseIo<OutboundJson<AllPatternsReq>, ProgramList>(
    OutboundJson(AllPatternsReq),
    BinaryTypeFlag.PreviewImage,
    handler,
    failureNotifier
)

class PixelblazeException(val failureCause: FailureCause, message: String = "", thrown: Throwable? = null) :
    Exception(message, thrown)

typealias FailureNotifier = (PixelblazeException) -> Unit

val ignoreFailure: FailureNotifier = { _ -> }