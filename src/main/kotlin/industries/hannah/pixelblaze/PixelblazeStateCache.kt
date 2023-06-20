package industries.hannah.pixelblaze

import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Maintains data on the state of the Pixelblaze through a combination of monitoring regular updates and scheduling
 * requests for system state. Note that this generates traffic that your watchers will see as well if they're attached
 * to some inbound types.
 */
class PixelblazeStateCache(
    pixelblaze: Pixelblaze,
    refreshRates: RefreshRates = RefreshRates(),
    excludedOutboundTypes: Set<Outbound<*>> = setOf()
) {
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
            pixelblaze.addWatcher(InboundPeers) { peersHolder.set(it) }
            pixelblaze.repeatOutbound({ GetPeers }, refreshRates.peers)
        }

        if (!excludedOutboundTypes.contains(OutboundGetSystemState)) {
            pixelblaze.addWatcher(InboundSettings) { settingsHolder.set(it) }
            pixelblaze.addWatcher(InboundExpanderChannels) { expanderChannelsHolder.set(it) }
            // This also requests a sequencer state, but we want to watch that no matter what
            pixelblaze.repeatOutbound({ GetSystemState }, refreshRates.systemState)
        }

        if (!excludedOutboundTypes.contains(OutboundGetAllPrograms)) {
            pixelblaze.addWatcher(InboundAllPrograms) { it ->
                allPatternsHolder.set(it.patterns.associate {
                    Pair(it.id, it.name)
                })
            }
            pixelblaze.repeatOutbound({ GetAllPrograms }, refreshRates.allPatterns)
        }

        if (!excludedOutboundTypes.contains(OutboundGetPlaylist)) {
            pixelblaze.addWatcher(InboundPlaylist) { currPlaylistHolder.set(it) }
            pixelblaze.repeatOutbound({ GetPlaylist(Pixelblaze.DEFAULT_PLAYLIST) }, refreshRates.currPlaylist)
        }

        //Finally we record the ones that just come on their own
        pixelblaze.addWatcher(InboundSequencerState) { seqStateHolder.set(it) }
        pixelblaze.addWatcher(InboundStats) { statsHolder.set(it) }
    }

    fun allPatterns(): Map<String, String>? = allPatternsHolder.get()
    fun currentPlaylist(): Playlist? = currPlaylistHolder.get()
    fun patternName(patternId: String): String? = allPatternsHolder.get()?.get(patternId)
    fun currentPlaylistIndex(): UInt? = seqStateHolder.get()?.playlistState?.position?.toUInt()
    fun lastStats(): Stats? = statsHolder.get()
    fun sequencerState(): SequencerState? = seqStateHolder.get()
    fun settings(): Settings? = settingsHolder.get()
    fun peers(): Peers? = peersHolder.get()
}