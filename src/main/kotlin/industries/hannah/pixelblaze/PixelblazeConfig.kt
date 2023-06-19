package industries.hannah.pixelblaze

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class PixelblazeConfig(
    requestQueueDepth: UInt,
    inboundBufferQueueDepth: UInt,
    outboundFrameSize: UInt,
    saveAfterWriteBufferSize: UInt,
    val sleepOnNothingToDo: Duration,
    val sleepStrategyOnDisconnect: (UInt) -> Duration
) {
    val inboundBufferQueueDepth: Int = inboundBufferQueueDepth.toInt()
    val outboundQueueDepth: Int = requestQueueDepth.toInt()
    val outboundFrameSize: Int = outboundFrameSize.toInt()
    val saveAfterWriteBufferSize: Int = saveAfterWriteBufferSize.toInt()

    companion object {
        fun default(): PixelblazeConfig = PixelblazeConfig(
            requestQueueDepth = 50u,
            inboundBufferQueueDepth = 10u,
            outboundFrameSize = 1024u,
            saveAfterWriteBufferSize = 10u,
            sleepOnNothingToDo = 10.toDuration(DurationUnit.MILLISECONDS),
            sleepStrategyOnDisconnect = { retry ->
                (retry.toLong()).toDuration(DurationUnit.MILLISECONDS)
            },
        )
    }
}