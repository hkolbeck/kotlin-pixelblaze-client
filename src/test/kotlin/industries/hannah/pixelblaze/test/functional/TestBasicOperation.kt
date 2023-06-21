package industries.hannah.pixelblaze.test.functional

import industries.hannah.pixelblaze.Ack
import industries.hannah.pixelblaze.InboundAck
import industries.hannah.pixelblaze.Ping
import industries.hannah.pixelblaze.Pixelblaze
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class TestBasicOperation {
    private val pixelblaze = Pixelblaze.default(
        System.getenv("PIXELBLAZE_IP")
            ?: throw RuntimeException("Must set PIXELBLAZE_IP in test process env")
    )

    @Test
    fun testPing() {
        val mutex = Mutex(true)
        pixelblaze.addWatcher(InboundAck) { mutex.unlock() }
        pixelblaze.sendOutbound(Ping)

        runBlocking {
            withTimeout(2.seconds) {
                mutex.lock()
            }
        }
    }

    @Test
    fun testIssueAndWait() {
        runBlocking {
            val ack = pixelblaze.issueOutboundAndWait(Ping, InboundAck, 2.seconds)
            assertEquals(Ack, ack)
        }
    }
}