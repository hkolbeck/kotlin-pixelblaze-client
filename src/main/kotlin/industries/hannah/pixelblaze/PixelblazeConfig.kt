package industries.hannah.pixelblaze

import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI

class PixelblazeConfig(
    val address: URI,
    port: UInt = 81u,
    requestQueueDepth: UInt = 50u,
    awaitingResponseQueueDepth: UInt = 50u,
    inboundBufferQueueDepth: UInt = 10u,
    sleepMsIfNoTasks: UInt = 25u,
    ) {
    
    val inboundBufferQueueDepth: Int = inboundBufferQueueDepth.toInt()
    val port: Int = port.toInt()
    val requestQueueDepth: Int = requestQueueDepth.toInt()
    val awaitingResponseQueueDepth: Int = awaitingResponseQueueDepth.toInt()
    val sleepMsIfNoTasks: Int = sleepMsIfNoTasks.toInt()
}