package industries.hannah.pixelblaze

import com.google.gson.Gson
import kotlinx.coroutines.channels.Channel
import java.io.Closeable
import java.io.InputStream
import java.util.*
import kotlin.time.Duration

typealias WatcherID = UUID
typealias ParserID = UUID
typealias ScheduledMessageId = UUID

interface PixelblazeClient : Closeable {

    fun <Out, Wrapper : OutboundMessage<*, Out>> issueOutbound(type: Outbound<Wrapper>, msg: Wrapper): Boolean

    suspend fun <Out, Wrapper : OutboundMessage<*, Out>, Resp : InboundMessage> issueOutboundAndWait(
        outboundType: Outbound<Wrapper>,
        msg: Wrapper,
        inboundType: Inbound<Resp>,
        maxWait: Duration
    ): Resp?

    fun <Out, Wrapper : OutboundMessage<*, Out>> repeatOutbound(
        type: Outbound<Wrapper>,
        msgGenerator: () -> Wrapper,
        interval: Duration,
        initialDelay: Duration = interval
    ): ScheduledMessageId

    fun cancelRepeatedOutbound(id: ScheduledMessageId): Boolean

    fun <T, Out, Wrapper : OutboundMessage<*, Out>> saveAfter(
        wrapperBuilder: (T, Boolean) -> Wrapper,
        saveAfter: Duration
    ): Channel<T>

    fun <ParsedType : InboundMessage> addWatcher(
        type: Inbound<ParsedType>,
        handler: (ParsedType) -> Unit
    ): WatcherID

    fun removeWatcher(id: WatcherID): Boolean


    fun <ParsedType : InboundMessage> addTextParser(
        msgType: InboundText<ParsedType>,
        parserFn: (Gson, String) -> ParsedType?,
        priority: Int
    ): ParserID

    fun <ParsedType : InboundMessage> setBinaryParser(
        msgType: InboundBinary<ParsedType>,
        parserFn: (InputStream) -> ParsedType?
    ): ParserID

    fun removeParser(id: ParserID): Boolean

    companion object {
        const val DEFAULT_PLAYLIST = "_defaultplaylist_"

        fun humanizeVarName(varName: String): String {
            TODO()
        }
    }
}



