package industries.hannah.pixelblaze

import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ChannelResult
import java.io.InputStream
import java.net.URI
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class WebsocketPixelblazeClient internal constructor(
    private val address: String,
    private val port: Int,
    private val watchers: MutableMap<Inbound<InboundMessage>, (InboundMessage) -> Unit>,
    private val textParsers: PriorityBlockingQueue<TextParser<*>>,
    private val binaryParsers: MutableMap<InboundBinary<*>, SerialParser<*>>,
    private val connectionWatcher: (ConnectionStatus, String?, Throwable?) -> Unit,
    private val config: PixelblazeConfig,
    private val gson: Gson,
    private val httpClient: HttpClient,
    private val errorLog: (String?, Throwable?) -> Unit = { _, _ -> },
    private val infoLog: (String) -> Unit = { _ -> },
    private val debugLog: (String) -> Unit = { _ -> }
) : PixelblazeClient {
    private val machineryRWLock: ReadWriteLock = ReentrantReadWriteLock()
    private val outboundQueue: BlockingQueue<OutboundMessageWrapper<*>> =
        ArrayBlockingQueue(config.outboundQueueDepth)
    private val scheduledMessages: PriorityBlockingQueue<ScheduledMessage<*>> =
        PriorityBlockingQueue(10) { a, b -> a.runAt.compareTo(b.runAt) }

    private val binaryFrames: ArrayDeque<InputStream> = ArrayDeque(config.inboundBufferQueueDepth)
    private var activeMessageType: InboundBinary<*>? = null

    internal data class TextParser<Message : InboundMessage>(
        val id: ParserID,
        val priority: Int,
        val type: InboundText<Message>,
        val parser: (Gson, String) -> Message?
    )

    internal data class SerialParser<Message : InboundMessage>(
        val id: ParserID,
        val type: InboundBinary<Message>,
        val parser: (InputStream) -> Message?
    )

    internal data class OutboundMessageWrapper<Message>(
        val type: Outbound<Message>,
        val message: OutboundMessage<*, Message>
    )

    internal data class ScheduledMessage<Message>(
        val message: OutboundMessageWrapper<Message>,
        val runAt: Instant
    )

    enum class ConnectionStatus {
        Connected,
        ScheduledMessageEnqueueFailure,
        WatchThreadException,
        ClientDisconnect
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val connectionHandler: Job = coroutineScope.async {
        var retry = 1u
        while (isActive) {
            try {
                if (watchConnection(this)) {
                    retry = 1u
                } else {
                    delay(config.sleepStrategyOnDisconnect(retry))
                    retry++
                }
            } catch (t: Throwable) {
                connectionWatcher(
                    ConnectionStatus.WatchThreadException,
                    "Unexpected error from connection handler, reconnecting",
                    t
                )
                delay(config.sleepStrategyOnDisconnect(retry))
            }
        }
    }

    private val scheduleHandler: Job = coroutineScope.async {
        while (isActive) {
            try {
                var messageDispatched = false
                scheduledMessages.peek()?.run {
                    if (this.runAt.isBefore(Instant.now())) {
                        if (outboundQueue.offer(
                                this.message,
                                config.scheduledTaskDispatchWait.inWholeMilliseconds,
                                TimeUnit.MILLISECONDS
                            )
                        ) {
                            messageDispatched = true
                        } else {
                            connectionWatcher(
                                ConnectionStatus.ScheduledMessageEnqueueFailure,
                                "Dispatch of scheduled message failed, queue full",
                                null
                            )
                        }
                    }
                }

                if (!messageDispatched) {
                    delay(100.toDuration(DurationUnit.MILLISECONDS))
                }
            } catch (t: Throwable) {
                errorLog("Unexpected exception in scheduler coroutine", t)
                delay(1.toDuration(DurationUnit.SECONDS)) //This would be unexpected, don't spam
            }
        }
    }

    private suspend fun watchConnection(scope: CoroutineScope): Boolean {
        var unclosed = false
        httpClient.webSocket(method = HttpMethod.Get, host = address, port = port, path = "/") {
            while (scope.isActive) {
                var workDone = false
                val receiveResult: ChannelResult<Frame> = incoming.tryReceive()
                if (receiveResult.isClosed) {
                    if (unclosed) {
                        connectionWatcher(
                            ConnectionStatus.ClientDisconnect,
                            "Client disconnected, will attempt to reconnect",
                            null
                        )
                    }
                    errorLog("Connection closed, will retry", null)
                    break
                } else if (receiveResult.isSuccess) {
                    if (!unclosed) {
                        connectionWatcher(
                            ConnectionStatus.Connected,
                            "Client received first message on new connection",
                            null
                        )
                    }

                    readFrame(receiveResult.getOrThrow())?.run { dispatchInboundFrame(this) }
                    workDone = true //True even if we discarded the read, don't want to sleep
                    unclosed = true
                } else {
                    // else receiveResult.isFailure => nothing to read but mark conn as healthy
                    unclosed = true
                }

                outboundQueue.poll()?.run {
                    sendOutboundMessage(this)
                    workDone = true
                }

                if (!workDone) {
                    delay(config.sleepOnNothingToDo)
                }
            }
        }

        return unclosed
    }

    private fun readFrame(frame: Frame): Pair<Inbound<*>, InboundMessage>? {
        return when (frame.frameType) {
            FrameType.TEXT -> {

            }

            FrameType.BINARY -> {

            }

            else -> null
        }
    }

    private fun dispatchInboundFrame(message: Pair<Inbound<*>, InboundMessage>) {
        TODO()
    }

    private fun sendOutboundMessage(message: Pair<Outbound<*>, OutboundMessage<*, *>>) {
        TODO()
    }

    override fun close() {
        runBlocking {
            scheduleHandler.cancel()
            connectionHandler.cancel()
            scheduleHandler.join()
            connectionHandler.join()
        }
    }

    /**
     *         msgType: InboundJson<ParsedType>,
    parser: (JsonObject) -> ParsedType?,
    priority: Int
     */
    class Builder {
        private val watchers: MutableMap<Inbound<InboundMessage>, (InboundMessage) -> Unit> = mutableMapOf()
        private val textParsers: PriorityBlockingQueue<TextParser<*>> =
            PriorityBlockingQueue(16) { a, b -> a.priority - b.priority }
        private val binaryParsers: MutableMap<InboundBinary<*>, SerialParser<*>> = mutableMapOf()

        private var httpClient: HttpClient? = null
        private var address: URI? = null
        private var port: Int = 81
        private var config: PixelblazeConfig = PixelblazeConfig()
        private var errorLog: (String?, Throwable?) -> Unit = { _, _ -> }
        private var infoLog: (String) -> Unit = { _ -> }
        private var debugLog: (String) -> Unit = { _ -> }

        fun build(): WebsocketPixelblazeClient {

        }
    }

    companion object {
        fun builder(): Builder {
            return Builder()
        }
    }
}
