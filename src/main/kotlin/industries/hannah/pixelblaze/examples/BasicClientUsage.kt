package industries.hannah.pixelblaze.examples

import industries.hannah.pixelblaze.*
import java.lang.Thread.sleep

/**
 * A very basic example of using the client, in this case just to send a ping every
 * three seconds and print a message when an ack comes in. Note that setting the watcher
 * and sending the messages are disconnected. If you need to send and await a response,
 * issueOutboundAndWait() provides that functionality, however its
 */
fun main() {
    /**
     * All settings have defaults, including the default Pixelblaze address of 192.168.4.1,
     * if you need to tweak any, use WebsocketPixelblaze.defaultBuilder() to still include some defaults
     * or WebsocketPixelblaze.bareBuilder() to have nothing set except noop logging functions
     */
    val pixelblaze = Pixelblaze.default()

    /**
     * Add a watcher, a function to be invoked every time a message of some type is received.
     * It will be passed a parsed version of the message. Note that unless a coroutine scope is provided
     * invocation is on the main handler thread, so blocking or heavy computation aren't advisable.
     */
    pixelblaze.addWatcher(InboundAck) { _ -> println("Got an ack!") }

    while (true) {
        pixelblaze.issueOutbound(OutboundPing, Ping)

        sleep(3000)
    }
}