package industries.hannah.pixelblaze.examples

import industries.hannah.pixelblaze.Pixelblaze
import industries.hannah.pixelblaze.SetBrightness
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

fun main() {
    val pixelblaze = Pixelblaze.default()

    /**
     * saveAfter supports the message types that allow the client to specify whether to save the sent value to flash
     * memory. Saving every time would restrict the rate at which you could issue updates as flash can only sustain
     * about 10k writes before it starts failing. This provides a channel where updates can be sent, the value will
     * be saved at the specified interval whether new values are coming in or not
     */
    val channel = pixelblaze.saveAfter(3.seconds) { brightness: Float, save -> SetBrightness(brightness, save) }
    MyUI.onUpdate(handleUserInput(channel))

    runBlocking {
        while (true) {
            delay(1000)
        }
    }
}

fun handleUserInput(writeChannel: SendChannel<Float>): (Float) -> Unit {
    return { brightness ->
        //TODO: Actually check if it sent and if the channel is closed.
        //TODO: The channel is only closed if the Pixelblaze client has been closed
        writeChannel.trySend(brightness)
    }
}

/**
 * Just to make this compile. Think of any system where user activity comes in as an event to a handler system
 */
object MyUI {
    fun onUpdate(handler: (Float) -> Unit) {
    }
}