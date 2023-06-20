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

    fun <Out, Wrapper : OutboundMessage<*, Out>> issueOutbound(msg: Wrapper): Boolean

    suspend fun <Out, Wrapper : OutboundMessage<*, Out>, Resp : InboundMessage> issueOutboundAndWait(
        msg: Wrapper,
        inboundType: Inbound<Resp>,
        maxWait: Duration
    ): Resp?

    fun <Out, Wrapper : OutboundMessage<*, Out>> repeatOutbound(
        msgGenerator: () -> Wrapper,
        interval: Duration,
        initialDelay: Duration = interval
    ): ScheduledMessageId

    fun cancelRepeatedOutbound(id: ScheduledMessageId): Boolean

    fun <T, Out, Wrapper : OutboundMessage<*, Out>> saveAfter(
        saveAfter: Duration,
        wrapperBuilder: (T, Boolean) -> Wrapper
    ): SendChannel<T>

    fun <ParsedType : InboundMessage> addWatcher(
        type: Inbound<ParsedType>,
        handler: (ParsedType) -> Unit
    ): WatcherID = addWatcher(type, null, handler)

    fun <ParsedType : InboundMessage> addWatcher(
        type: Inbound<ParsedType>,
        coroutineScope: CoroutineScope?,
        handler: (ParsedType) -> Unit
    ): WatcherID

    fun removeWatcher(id: WatcherID): Boolean


    fun <ParsedType : InboundMessage> addTextParser(
        priority: Int,
        msgType: InboundText<ParsedType>,
        parserFn: (Gson, String) -> ParsedType?
    ): ParserID

    fun <ParsedType : InboundMessage> setBinaryParser(
        msgType: InboundBinary<ParsedType>,
        parserFn: (InputStream) -> ParsedType?
    ): ParserID

    fun removeParser(id: ParserID): Boolean

    companion object {
        const val DEFAULT_PLAYLIST = "_defaultplaylist_"

        fun default(): Pixelblaze = WebsocketPixelblaze.defaultBuilder().build()

        fun default(pixelblazeIp: String): Pixelblaze = WebsocketPixelblaze.defaultBuilder().build()

        fun humanizeVarName(varName: String): String {
            TODO()
        }
    }
}



