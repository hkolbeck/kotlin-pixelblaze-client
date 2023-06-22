package industries.hannah.pixelblaze.examples

import industries.hannah.pixelblaze.InboundPlaylist
import industries.hannah.pixelblaze.WebsocketPixelblaze
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * The Pixelblaze sends an unprompted message every time the pattern changes, this listens for those changes
 */
fun main() {
    val pixelblaze = WebsocketPixelblaze.defaultBuilder()
        .addWatcher(InboundPlaylist, CoroutineScope(Dispatchers.Default)) {
            // Note that the pattern name is not included here, getting that requires a call with GetAllPrograms
            // or using the PixelblazeMetadataCache
            println("Active pattern moved to ${it.position}, pattern id: ${it.patterns[it.position].id}")
        }.second
        .build()

    runBlocking {
        while (true) {
            delay(1000)
        }
    }
}