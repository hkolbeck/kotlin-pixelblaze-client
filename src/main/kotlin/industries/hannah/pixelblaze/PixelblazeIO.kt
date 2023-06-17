package industries.hannah.pixelblaze

import java.lang.reflect.Type
import java.time.Instant

internal sealed interface PixelblazeIO {
    val expectResponse: Boolean
    val failureNotifier: FailureNotifier
    val requestAt: Instant
    val expectedType: Type

    var satisfied: Boolean
}

internal sealed interface BinaryResponseIO<Req : Request, Resp : Response> : PixelblazeIO {
    val request: Req
    val respType: BinaryMsgType

    fun parse(bytes: ByteArray): Resp?
    fun handle(response: Resp)
}

internal sealed interface TextResponseIO<Req : Request, Resp : Response> : PixelblazeIO {
    val request: Req
    fun handle(response: Resp)
}



typealias FailureNotifier = (FailureCause) -> Unit

val ignoreFailure: FailureNotifier = { _ -> }