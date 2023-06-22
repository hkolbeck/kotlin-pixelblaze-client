package industries.hannah.pixelblaze.examples

import com.google.gson.Gson
import industries.hannah.pixelblaze.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking


/**
 * The client ships with a subset of known inbound and outbound messages supported. If you need a non-supported
 * operation, the client supports adding new outbound messages, inbound parsers, and inbound messages
 */

/**
 * All outbound types must extend OutboundText or OutboundBinary depending on what websocket format
 * they're transmitted in
 */
object MyOutboundMessageType : OutboundText<MyOutboundMessage>(MyOutboundMessage::class)

/**
 * Outbound messages must extend OutboundJsonMessage or OutboundBinaryMessage, but a variety of helper
 * subclasses are provided. Check Outbound.kt for more information
 */
object MyOutboundMessage : StringLiteralTextMessage("""{"doTheThing": true}""") {
    override val type = MyOutboundMessageType
}

/**
 * All inbound types must implement InboundText or InboundBinary
 */
object MyInboundMessageType : InboundText<MyInboundMessage>(MyInboundMessage::class)

/**
 * There are no bounds on inbound message shape, but they must inherit the InboundMessage marker interface
 */
data class MyInboundMessage(val field: String) : InboundMessage
fun parseMyInbound(gson: Gson, raw: String): MyInboundMessage? {
    return try {
        gson.fromJson(raw, MyInboundMessage::class.java)
    } catch (t: Throwable) {
        println("Parsing MyInboundMessage failed! ${t.message}\nRaw message: $raw")
        null
    }
}

fun main() {
    /**
     * Watchers and parsers can still be added and removed on the client after it's built. Both add* functions on the
     * builder return a Pair<ID, Builder>, the ID can be used later to remove the watcher or parser, but it's safe to
     * discard if you want to keep the components in operation.
     */
    val pixelblaze = WebsocketPixelblaze.defaultBuilder()
        .addTextParser(
            500, //Parsers are executed in order, from low to high, until one succeeds. You can control the order
            MyInboundMessageType,
            ::parseMyInbound
        ).second
        .addWatcher(
            MyInboundMessageType
        ) { msg -> println("Got one of my messages! Field = '$msg'") }.second
        .build()

    runBlocking {
        while (true) {
            pixelblaze.sendOutbound(MyOutboundMessage)
            delay(3000)
        }
    }
}