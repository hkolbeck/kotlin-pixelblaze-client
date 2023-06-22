package industries.hannah.pixelblaze.test.functional

import industries.hannah.pixelblaze.Pixelblaze
import industries.hannah.pixelblaze.PixelblazeStateCache
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

//TODO: Is there a simple way to test finer points of this given that getting expected values isn't simple?
class TestStateCache {

    @Test
    fun testAwaitFill() {
        Pixelblaze.default(
            System.getenv("PIXELBLAZE_IP")
                ?: throw RuntimeException("Must set PIXELBLAZE_IP in test process env")
        ).use { pixelblaze ->
            val cache = PixelblazeStateCache(pixelblaze)
            runBlocking {
                assertTrue(cache.awaitFill(5.seconds))
            }
        }
    }
}