package industries.hannah.pixelblaze

import com.google.gson.JsonObject
import kotlinx.coroutines.channels.Channel
import java.io.Closeable
import java.io.InputStream
import java.time.Duration
import java.util.*

typealias WatcherID = UUID
typealias ParserID = UUID
//TODO Look for field not there to distinguish playlist from playlistupdate

interface PixelblazeClient : Closeable {

    ////// Write
    fun <Out> issueOutbound(val type: Outbound): Boolean

    suspend fun <Out, Resp : InboundMessage> issueOutboundAndWait(
        out: Out,
        inboundType: Inbound<Resp>,
        maxWait: Duration
    ): Resp?

    suspend fun <Out> repeatOutbound(out: Out, interval: Duration)

    fun <T> saveAfter(wrapper: (T, Boolean) -> Outbound, saveAfter: Duration): Channel<T>


    /////// Read

    fun <ParsedType : InboundMessage> addWatcher(
        type: Inbound<ParsedType>,
        handler: (ParsedType) -> Unit
    ): WatcherID

    fun removeWatcher(id: WatcherID)


    fun addJsonParser(parser: (JsonObject) -> InboundMessage?, priority: Int): ParserID
    fun setBinaryParser(msgType: BinaryTypeFlag, parser: (InputStream) -> InboundMessage?): ParserID
    fun removeParser(id: ParserID)


    ///// Write TODO

    fun setJsonSerializer()

    fun setBinarySerializer()

    companion object {
        const val DEFAULT_PLAYLIST = "_defaultplaylist_"

        fun humanizeVarName(varName: String): String {
            TODO()
        }
    }
}



