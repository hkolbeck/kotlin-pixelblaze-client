package industries.hannah.pixelblaze

import java.net.URI

class PixelblazeConfig(
    val address: URI,

    port: UShort = 81u,
    requestQueueDepth: UInt = 50u,
    awaitingResponseQueueDepth: UInt = 50u,
    inboundBufferQueueDepth: UInt = 10u,
    outboundFrameSize: UInt = 1024u,
    maxInboundMessagesBeforeOutbound: UInt = 50u,
    maxOutboundMessagesBeforeInbound: UInt = 3u
) {
    val inboundBufferQueueDepth: Int = inboundBufferQueueDepth.toInt()
    val port: Int = port.toInt()
    val requestQueueDepth: Int = requestQueueDepth.toInt()
    val awaitingResponseQueueDepth: Int = awaitingResponseQueueDepth.toInt()
    val outboundFrameSize: Int = outboundFrameSize.toInt()
    val maxInboundMessagesBeforeOutbound: Int = maxInboundMessagesBeforeOutbound.toInt()
    val maxOutboundMessagesBeforeInbound: Int = maxOutboundMessagesBeforeInbound.toInt()

}