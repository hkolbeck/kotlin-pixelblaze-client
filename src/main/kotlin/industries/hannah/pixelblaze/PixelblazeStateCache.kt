package industries.hannah.pixelblaze

import io.ktor.util.collections.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Maintains data on the state of the Pixelblaze through a combination of monitoring regular updates and scheduling
 * requests for system state. Note that this generates traffic that your watchers will see as well if they're attached
 * to some inbound types.
 *
 * Note that if the Pixelblaze instance was not constructed using the default builder, some messages may not have
 * associated parsers, and so the value exposed here will always be null.
 */
class PixelblazeStateCache(
    private val pixelblaze: Pixelblaze,
    refreshRates: RefreshRates = RefreshRates(),
    private val excludedOutboundTypes: Set<Outbound<*>> = setOf()
) : Closeable {
    private val closeCalled = AtomicBoolean(false)

    private val scheduleIds: MutableSet<ScheduledMessageId> = ConcurrentSet()
    private val watcherIds: MutableSet<WatcherID> = ConcurrentSet()

    private val allPatternsHolder: AtomicReference<Map<String, String>?> = AtomicReference(null)
    private val currPlaylistHolder: AtomicReference<Playlist?> = AtomicReference(null)
    private val statsHolder: AtomicReference<Stats?> = AtomicReference(null)
    private val seqStateHolder: AtomicReference<SequencerState?> = AtomicReference(null)
    private val peersHolder: AtomicReference<Peers?> = AtomicReference(null)
    private val settingsHolder: AtomicReference<Settings?> = AtomicReference(null)
    private val expanderChannelsHolder: AtomicReference<ExpanderChannels?> = AtomicReference(null)

    /**
     * All others are sent unprompted by the pixelblaze on changes
     */
    data class RefreshRates(
        val allPatterns: Duration = 30.seconds,
        val systemState: Duration = 15.seconds,
        val currPlaylist: Duration = 10.seconds,
        val peers: Duration = 5.seconds
    )

    init {
        //First, we need to both set watchers and arrange scheduled requests for those endpoints that require it
        if (!excludedOutboundTypes.contains(OutboundGetPeers)) {
            watcherIds.add(pixelblaze.addWatcher(InboundPeers) { peersHolder.set(it) })
            scheduleIds.add(pixelblaze.repeatOutbound({ GetPeers }, refreshRates.peers, Duration.ZERO))
        }

        if (!excludedOutboundTypes.contains(OutboundGetSystemState)) {
            watcherIds.add(pixelblaze.addWatcher(InboundSettings) { settingsHolder.set(it) })
            watcherIds.add(pixelblaze.addWatcher(InboundExpanderChannels) { expanderChannelsHolder.set(it) })
            // This also requests a sequencer state, but we want to watch that no matter what
            scheduleIds.add(pixelblaze.repeatOutbound({ GetSystemState }, refreshRates.systemState, Duration.ZERO))
        }

        if (!excludedOutboundTypes.contains(OutboundGetAllPrograms)) {
            watcherIds.add(pixelblaze.addWatcher(InboundAllPrograms) { it ->
                allPatternsHolder.set(it.patterns.associate {
                    Pair(it.id, it.name)
                })
            })
            scheduleIds.add(pixelblaze.repeatOutbound({ GetAllPrograms }, refreshRates.allPatterns, Duration.ZERO))
        }

        if (!excludedOutboundTypes.contains(OutboundGetPlaylist)) {
            watcherIds.add(pixelblaze.addWatcher(InboundPlaylist) { currPlaylistHolder.set(it) })
            scheduleIds.add(
                pixelblaze.repeatOutbound(
                    { GetPlaylist(Pixelblaze.DEFAULT_PLAYLIST) },
                    refreshRates.currPlaylist,
                    Duration.ZERO
                )
            )
        }

        //Finally we record the ones that just come on their own
        watcherIds.add(pixelblaze.addWatcher(InboundSequencerState) { seqStateHolder.set(it) })
        watcherIds.add(pixelblaze.addWatcher(InboundStats) { statsHolder.set(it) })
    }

    suspend fun awaitFill(maxWait: Duration, awaitExpanders: Boolean = false): Boolean {
        return withTimeoutOrNull(maxWait) {
            while (
                (!excludedOutboundTypes.contains(OutboundGetAllPrograms) && allPatternsHolder.get() == null) &&
                (!excludedOutboundTypes.contains(OutboundGetPlaylist) && currPlaylistHolder.get() == null) &&
                (!excludedOutboundTypes.contains(OutboundGetSystemState) && seqStateHolder.get() == null) &&
                (!excludedOutboundTypes.contains(OutboundGetPeers) && peersHolder.get() == null) &&
                (!excludedOutboundTypes.contains(OutboundGetSystemState) && settingsHolder.get() == null) &&
                statsHolder.get() == null &&
                (awaitExpanders && expanderChannelsHolder.get() == null)
            ) {
                delay(100.milliseconds)
            }
            true
        } != null
    }

    fun allPatterns(): Map<String, String>? = throwIfClosed(allPatternsHolder.get())
    fun currentPlaylist(): Playlist? = throwIfClosed(currPlaylistHolder.get())
    fun patternName(patternId: String): String? = throwIfClosed(allPatternsHolder.get()?.get(patternId))
    fun lastStats(): Stats? = throwIfClosed(statsHolder.get())
    fun sequencerState(): SequencerState? = throwIfClosed(seqStateHolder.get())
    fun settings(): Settings? = throwIfClosed(settingsHolder.get())
    fun peers(): Peers? = throwIfClosed(peersHolder.get())
    fun currentPlaylistIndex(): UInt? = throwIfClosed(seqStateHolder.get()?.playlistState?.position?.toUInt())
    fun nextPlaylistIndex(): UInt? = throwIfClosed(calcPositionChange { position, len -> (position + 1) % len })
    fun prevPlaylistIndex(): UInt? = throwIfClosed(calcPositionChange { position, len -> (position + len - 1) % len })

    fun patternAtPosition(position: UInt): NamedPattern? {
        return throwIfClosed(when (val playlist = currPlaylistHolder.get()) {
            null -> null
            else -> when (val allPatterns = allPatternsHolder.get()) {
                null -> null
                else -> {
                    playlist.patterns.getOrNull(position.toInt())?.run {
                        val patternAtPosition = this
                        allPatterns[patternAtPosition.id]?.run { NamedPattern(patternAtPosition.id, this) }
                    }
                }
            }
        })
    }

    private fun calcPositionChange(calc: (Int, Int) -> Int): UInt? {
        return when (val playlist = currPlaylistHolder.get()) {
            null -> null
            else -> when (val seqState = seqStateHolder.get()) {
                null -> null
                else -> {
                    try {
                        calc(seqState.playlistState.position, playlist.patterns.size).toUInt()
                    } catch (ae: ArithmeticException) {
                        //Playlist was empty
                        null
                    }
                }
            }
        }
    }

    private fun <T> throwIfClosed(t: T): T {
        if (isClosed()) {
            throw IllegalStateException("Attempt to read from a closed state cache instance")
        }
        
        return t
    }
    
    fun isClosed(): Boolean {
        return closeCalled.get()
    }

    override fun close() {
        closeCalled.set(true)
        scheduleIds.forEach { pixelblaze.cancelRepeatedOutbound(it) }
        watcherIds.forEach { pixelblaze.removeWatcher(it) }
    }
}