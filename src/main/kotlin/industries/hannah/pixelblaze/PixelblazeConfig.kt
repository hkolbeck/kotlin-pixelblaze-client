package industries.hannah.pixelblaze

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class PixelblazeConfig(
    requestQueueDepth: UInt = 50u,
    inboundBufferQueueDepth: UInt =  10u,
    outboundFrameSize: UInt = 1024u,
    saveAfterWriteBufferSize: UInt = 10u,
    val sleepOnNothingToDo: Duration = 10.toDuration(DurationUnit.MILLISECONDS),
    val sleepStrategyOnDisconnect: (UInt) -> Duration = { retry ->
        (retry.toLong()).toDuration(DurationUnit.MILLISECONDS)
    },
) {
    val inboundBufferQueueDepth: Int = inboundBufferQueueDepth.toInt()
    val outboundQueueDepth: Int = requestQueueDepth.toInt()
    val outboundFrameSize: Int = outboundFrameSize.toInt()
    val saveAfterWriteBufferSize: Int = saveAfterWriteBufferSize.toInt()

    companion object {
        fun default(): PixelblazeConfig = PixelblazeConfig()
    }
}