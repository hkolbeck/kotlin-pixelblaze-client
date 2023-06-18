package industries.hannah.pixelblaze

import com.google.gson.JsonObject
import kotlinx.coroutines.channels.Channel
import java.io.Closeable
import java.io.InputStream
import java.time.Duration
import java.util.*

typealias WatcherID = UUID
typealias ParserID = UUID

interface PixelblazeClient : Closeable {

    ////// Write
    fun <Out, Wrapper : OutboundMessage<*, Out>> issueOutbound(type: Outbound<Out>, msg: Wrapper): Boolean

    suspend fun <Out, Wrapper : OutboundMessage<*, Out>, Resp : InboundMessage> issueOutboundAndWait(
        type: Outbound<Out>,
        msg: Wrapper,
        inboundType: Inbound<Resp>,
        maxWait: Duration
    ): Resp?

    suspend fun <Out, Wrapper : OutboundMessage<*, Out>> repeatOutbound(
        type: Outbound<Out>,
        msg: Wrapper,
        interval: Duration
    )

    fun <T, Out> saveAfter(wrapperBuilder: (T, Boolean) -> OutboundMessage<*, Out>, saveAfter: Duration): Channel<T>


    /////// Read

    fun <ParsedType : InboundMessage> addWatcher(
        type: Inbound<ParsedType>,
        handler: (ParsedType) -> Unit
    ): WatcherID

    fun removeWatcher(id: WatcherID): Boolean


    fun <ParsedType : InboundMessage> addJsonParser(
        msgType: InboundText<ParsedType>,
        parser: (JsonObject) -> ParsedType?,
        priority: Int
    ): ParserID

    fun <ParsedType : InboundMessage> setBinaryParser(
        msgType: InboundBinary<ParsedType>,
        parser: (InputStream) -> ParsedType?
    ): ParserID

    fun removeParser(id: ParserID): Boolean
    fun removeBinaryParser(msgType: InboundBinary<*>): ParserID?

    ///// Write TODO

    companion object {
        const val DEFAULT_PLAYLIST = "_defaultplaylist_"

        fun humanizeVarName(varName: String): String {
            TODO()
        }
    }
}



