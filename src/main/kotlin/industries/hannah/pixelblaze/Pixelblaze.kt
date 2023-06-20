package industries.hannah.pixelblaze

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import java.io.Closeable
import java.io.InputStream
import java.util.*
import kotlin.time.Duration

typealias WatcherID = UUID
typealias ParserID = UUID
typealias ScheduledMessageId = UUID

interface Pixelblaze : Closeable {

    /**
     * Send an outbound message to the attached Pixelblaze.
     *
     * A variety of messages are included with the library, the full set can be found next to [OutboundMessage].
     * For example to send an argument-less messge like a ping:
     *
     * pixelblaze.issueOutbound(Ping)
     *
     * @return true if the message was successfully placed in the outbound queue, false otherwise
     */
    fun <Out, Wrapper : OutboundMessage<*, Out>> issueOutbound(msg: Wrapper): Boolean

    /**
     * Send an outbound message and await a response of a specified type. This is fragile because nothing prevents
     * it returning an inbound message triggered by some other request. Its use in a multithreaded environment
     * is especially discouraged
     *
     * @param msg the message to be sent
     * @param inboundType the message type to await
     * @param maxWait the maximum duration to wait
     *
     * @return a response of the specified type if one came back in time, or else null
     */
    suspend fun <Out, Wrapper : OutboundMessage<*, Out>, Resp : InboundMessage> issueOutboundAndWait(
        msg: Wrapper,
        inboundType: Inbound<Resp>,
        maxWait: Duration
    ): Resp?

    /**
     * Send a message on a schedule
     *
     * @param msgGenerator function to generate messages to be sent
     * @param interval how often to send them
     * @param initialDelay how long to wait before sending the first
     *
     * @return an id that can be used to later cancel the scheduled message
     */
    fun <Out, Wrapper : OutboundMessage<*, Out>> repeatOutbound(
        msgGenerator: () -> Wrapper,
        interval: Duration,
        initialDelay: Duration = interval
    ): ScheduledMessageId

    /**
     * Cancel an ongoing scheduled message
     *
     * @param id the ID of the message to be removed
     *
     * @return true if the schedule was found and removed, false otherwise
     */
    fun cancelRepeatedOutbound(id: ScheduledMessageId): Boolean

    /**
     * Utility method for functions like a brightness slider, where updates can come fast, but we want to
     * spare the flash memory on the pixelblaze but not saving until they settle. This provides a SendChannel
     * for updates to be sent down. They'll all be dispatched, but a save only requested at the specified
     * interval, even if no writes have continued coming in.
     *
     * @param saveAfter how often to save the inbound values
     * @param wrapperBuilder a function taking a value to be written and whether to save it, and producing a message
     *
     * @return a channel for writes to be sent down
     */
    fun <T, Out, Wrapper : OutboundMessage<*, Out>> saveAfter(
        saveAfter: Duration,
        wrapperBuilder: (T, Boolean) -> Wrapper
    ): SendChannel<T>

    /**
     * Add a watcher for some type. It will be invoked anytime a message of the specified type is received.
     * Note that evaluation of the handler is done in the main processing coroutine, if it does anything
     * computationally intensive it's recommended to specify a coroutineScope to run it in using
     * the other [addWatcher] method.
     *
     * @param type the type to watch for
     * @param handler the function to invoke with the parsed message
     */
    fun <ParsedType : InboundMessage> addWatcher(
        type: Inbound<ParsedType>,
        handler: (ParsedType) -> Unit
    ): WatcherID

    /**
     * Add a watcher for some type. It will be invoked anytime a message of the specified type is received.
     * The invocation will occur in the provided coroutine scope.
     *
     * @param type the message type to watch for
     * @param coroutineScope the scope to run handler invocations in
     * @param handler the function to be invoked
     */
    fun <ParsedType : InboundMessage> addWatcher(
        type: Inbound<ParsedType>,
        coroutineScope: CoroutineScope,
        handler: (ParsedType) -> Unit
    ): WatcherID

    /**
     * Remove a watcher
     *
     * @param id the watcher ID to remove
     * @return true if a watcher was removed, false otherwise
     */
    fun removeWatcher(id: WatcherID): Boolean

    /**
     * Remove all watchers for a given type
     *
     * @param the type to remove
     *
     * @return a list of watcher IDs removed
     */
    fun removeWatchersForType(type: Inbound<*>): List<WatcherID>

    /**
     * Adds a parser to the inbound text parser chain. They're tried in priority order, from low to high. If multiple
     * watchers have the same priority, they'll be invoked in an arbitrary order. If no watcher is assigned to the
     * parser's type, it will not be invoked.
     *
     * @param priority where in the parser chain to insert this parser
     * @param msgType the type of message the parser produces
     * @param parserFn the parse function
     *
     * @return An ID that can be used to later remove the parser
     */
    fun <ParsedType : InboundMessage> addTextParser(
        priority: Int,
        msgType: InboundText<ParsedType>,
        parserFn: (Gson, String) -> ParsedType?
    ): ParserID

    /**
     * Set the binary parser for a given binary type. Only one parser may be assigned to a given binary type.
     * If no watcher is assigned to the given type when a message with that type is received, it will not be parsed
     *
     * @param msgType the type of message to parse
     * @param parserFn the parse function
     *
     * @return An ID that can be used to later remove the parser
     */
    fun <ParsedType : InboundMessage> setBinaryParser(
        msgType: InboundBinary<ParsedType>,
        parserFn: (InputStream) -> ParsedType?
    ): ParserID

    /**
     * Remove a parser of either type
     *
     * @param id the id of the parser to remove
     *
     * @return true if a parser was removed, false otherwise
     */
    fun removeParser(id: ParserID): Boolean

    /**
     * Remove all parsers for a given text message type
     *
     * @param type the type to remove
     *
     * @return a list of parser IDs that were removed
     */
    fun removeTextParsersForType(type: InboundText<*>): List<ParserID>

    /**
     * Remove the parser for the specified binary type
     *
     * @param type the type to remove
     *
     * @return a parser ID if one was removed, otherwise null
     */
    fun removeBinaryParserForType(type: InboundBinary<*>): ParserID?

    companion object {
        const val DEFAULT_PLAYLIST = "_defaultplaylist_"

        /**
         * Get a pure default client, including the default address of 192.168.4.1
         */
        fun default(): Pixelblaze = WebsocketPixelblaze.defaultBuilder().build()

        /**
         * Get a default client, specifying only the IP
         */
        fun default(pixelblazeIp: String): Pixelblaze = WebsocketPixelblaze.defaultBuilder().build()

        /**
         * Utility method to take a camelCase variable name and return "Camel Case". If it starts with "slider"
         * that will first be removed.
         *
         * @param varName the variable name to be humanized
         *
         * @return The
         */
        fun humanizeVarName(varName: String): String {
            val stripped = varName.trim().removePrefix("slider").toCharArray()
            val result = StringBuilder()
            var currentWord = StringBuilder()

            if (stripped[0].isLowerCase()) {
                stripped[0] = stripped[0].uppercaseChar()
            }

            //TODO: Handle chunks of all capital letters
            for (char in stripped) {
                if (char.isUpperCase()) {
                    if (currentWord.isNotEmpty()) {
                        result.append(currentWord)
                        result.append(" ")
                    }
                    currentWord = StringBuilder()
                }
                currentWord.append(char)
            }

            if (currentWord.isNotEmpty()) {
                result.append(currentWord)
            }

            return result.toString()
        }
    }
}



