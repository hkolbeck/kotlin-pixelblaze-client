package industries.hannah.pixelblaze.test

import industries.hannah.pixelblaze.*
import kotlinx.coroutines.delay
import java.lang.RuntimeException
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

/**
 * Used to fetch raw frames during testing
 */
suspend fun main() {
    val pixelblaze = WebsocketPixelblaze.defaultBuilder()
        .setPixelblazeIp("10.0.0.68")
        .setErrorLog {str, t ->
            println("Error: '${str()}', thrown: '${t?.message ?: "Nothing"}'")
            t?.printStackTrace()
        }.setInfoLog { str -> println("Info: '${str()}'") }
        .setDebugLog { str -> println("Debug: '${str()}'") }
        .build()

    // We could define our own types and do dispatch in the watcher, but #yolo
    pixelblaze.setBinaryParser(InboundAllPrograms) {
        val bytes = it.readBytes()
        println("All Programs: ${Base64.getEncoder().encodeToString(bytes)}")
        AllPrograms.fromBinary(bytes.inputStream())
    }

    pixelblaze.setBinaryParser(InboundPreviewImage) {
        val bytes = it.readBytes()
        println("Preview Image: ${Base64.getEncoder().encodeToString(bytes)}")
        PreviewImage.fromBinary(bytes.inputStream())
    }

    val stateCache = pixelblaze.getStateCache(
        PixelblazeStateCache.RefreshRates(
            allPatterns = 60.seconds
        )
    ) // Will helpfully call all patterns for us, but we only need it once
    stateCache.awaitFill(10.seconds) || throw RuntimeException("Await timed out")
    stateCache.allPatterns()?.entries?.stream()?.findFirst()?.map {
        pixelblaze.sendOutbound(GetPreviewImage(it.key))
    }

    while (true) {
        delay(1000)
    }
}