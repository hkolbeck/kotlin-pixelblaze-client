package industries.hannah.pixelblaze.test

import com.google.gson.Gson
import industries.hannah.pixelblaze.*
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.Ignore
import kotlin.test.assertEquals

/**
 * Test playlist parse functions. Raw json objects were sniffed from the interactions between the standard UI and
 * the Pixelblaze. At present none use Gson adapters because the parse code was copied over from C++.
 */
@Ignore("Need to collect raw requests and fill out the expected fields")
class TestTextParsers {

    private val gson = Gson()

    @Test
    fun testParseSequencerState() {
        val raw = """
{"activeProgram":{"name":"color bands","activeProgramId":"vmCXgwbox8nCBqQig","controls":{}},"sequencerMode":2,"runSequencer":true,"playlist":{"position":1,"id":"_defaultplaylist_","ms":30000,"remainingMs":6549}}
        """
        val expected = SequencerState(
            activeProgram = ActiveProgram(
                name = "",
                id = "",
                controls = listOf()
            ),
            playlistState = PlaylistState(
                position = 0,
                id = "",
                ttlMs = 0,
                remainingMs = 0,
            ),
            sequencerMode = SequencerMode.Off,
            runSequencer = false
        )
        assertEquals(expected, SequencerState.fromText(gson, raw))
    }

    @Test
    fun testParseSettings() {
        val raw = """
{"name":"Pixelblaze_682855","brandName":"","pixelCount":100,"brightness":1,"maxBrightness":10,"colorOrder":"BGR","dataSpeed":3500000,"ledType":2,"sequenceTimer":15,"transitionDuration":0,"sequencerMode":2,"runSequencer":true,"simpleUiMode":false,"learningUiMode":false,"discoveryEnable":true,"timezone":"","autoOffEnable":false,"autoOffStart":"00:00","autoOffEnd":"00:00","cpuSpeed":240,"networkPowerSave":false,"mapperFit":0,"leaderId":0,"nodeId":0,"soundSrc":0,"accelSrc":0,"lightSrc":0,"analogSrc":0,"exp":0,"ver":"3.40","chipId":6826069}
        """
        val expected = Settings(
            typeName = "",
            brandName = "",
            pixelCount = 0,
            brightness = 0.0f,
            maxBrightness = 0.0f,
            colorOrder = ColorOrder.BGR,
            dataSpeedHz = 0,
            ledType = LedType.WS2812_SK6812_NEOPIXEL,
            sequenceTimerMs = 0,
            transitionDurationMs = 0,
            sequencerMode = SequencerMode.ShuffleAll,
            runSequencer = true,
            simpleUiMode = false,
            learningUiMode = false,
            discoveryEnabled = true,
            timezone = "",
            autoOffEnable = true,
            autoOffStart = "23:00",
            autoOffEnd = "00:00",
            cpuSpeedMhz = 0,
            networkPowerSave = false,
            mapperFit = 0,
            leaderId = 0,
            nodeId = 0,
            soundSrc = InputSource.Local,
            lightSrc = InputSource.Local,
            accelSrc = InputSource.Remote,
            analogSrc = InputSource.Remote,
            exp = 0,
            version = "",
            chipId = 0,
        )

        assertEquals(expected, Settings.fromText(gson, raw))
    }

    @Test
    @Ignore("No raw example on hand")
    fun testParsePeers() {
        val raw = """
        """

        val expected = Peers(
            listOf(
                Peer(
                    id = 0,
                    ipAddress = "",
                    name = "",
                    version = "",
                    isFollowing = false,
                    nodeId = 0,
                    followerCount = 0u,
                )
            )
        )

        assertEquals(expected, Peers.fromText(gson, raw))
    }

    @Test
    fun testParseStats() {
        val raw = """
            {"fps":177.8222,"vmerr":0,"vmerrpc":-1,"mem":10239,"exp":0,"renderType":1,"uptime":159889,"storageUsed":804706,"storageSize":1378241,"rr0":1,"rr1":14,"rebootCounter":0}
        """
        val expected = Stats(
            fps = 0F,
            vmerr = 0,
            vmerrpc = 0,
            memBytes = 0,
            expansions = 0,
            renderType = RenderType.Invalid,
            uptimeMs = 0,
            storageBytesUsed = 0,
            storageBytesSize = 0,
            rr0 = 0,
            rr1 = 0,
            rebootCounter = 0,
        )
        assertEquals(expected, Stats.fromText(gson, raw))
    }

    @Test
    fun testParsePlaylist() {
        val raw = """
{"playlist":{"position":1,"id":"_defaultplaylist_","ms":30000,"remainingMs":6535,"items":[{"id":"QqZqNsLmk2CAvheRR","ms":30000},{"id":"vmCXgwbox8nCBqQig","ms":30000},{"id":"p49GpaZRpfGHPNGuX","ms":30000},{"id":"wPnJGj5d5hzgeLbZD","ms":30000},{"id":"LR4KPE7qNStxaFMaA","ms":30000},{"id":"Ktjben4j36Wqxnk8N","ms":30000},{"id":"BxgkrQTHPkhcM6a24","ms":30000},{"id":"36h5YYz2jeCqkiZjj","ms":30000},{"id":"pxH7PgGxoM7WPrFWs","ms":30000},{"id":"iYDPEfGzNLkCiHdnG","ms":30000},{"id":"aE5YEvSnbXWsu5ob2","ms":30000},{"id":"9YNBonyhXfyFjpYzJ","ms":30000},{"id":"Qvqmzcuci43MSBYij","ms":30000},{"id":"D2xuondCcLLi2jvMr","ms":30000},{"id":"QqZqNsLmk2CAvheRR","ms":30000}]}}
        """
        val expected = Playlist(
            id = "",
            position = 0,
            currentDurationMs = 0,
            remainingCurrentMs = 0,
            patterns = listOf(),
        )
        assertEquals(expected, Playlist.fromText(gson, raw))
    }

    @Test
    fun testParsePlaylistUpdate() {
        val raw = """
{"playlist":{"id":"_defaultplaylist_","items":[{"id":"QqZqNsLmk2CAvheRR","ms":30000},{"id":"vmCXgwbox8nCBqQig","ms":30000},{"id":"p49GpaZRpfGHPNGuX","ms":30000},{"id":"wPnJGj5d5hzgeLbZD","ms":30000},{"id":"LR4KPE7qNStxaFMaA","ms":30000},{"id":"Ktjben4j36Wqxnk8N","ms":30000},{"id":"BxgkrQTHPkhcM6a24","ms":30000},{"id":"36h5YYz2jeCqkiZjj","ms":30000},{"id":"pxH7PgGxoM7WPrFWs","ms":30000},{"id":"iYDPEfGzNLkCiHdnG","ms":30000},{"id":"aE5YEvSnbXWsu5ob2","ms":30000},{"id":"9YNBonyhXfyFjpYzJ","ms":30000},{"id":"Qvqmzcuci43MSBYij","ms":30000},{"id":"D2xuondCcLLi2jvMr","ms":30000},{"id":"QqZqNsLmk2CAvheRR","ms":30000},{"id":"vkpGsR88dcJmXfevp","ms":30000}]},"save":true}
        """

        val expected = PlaylistUpdate(
            id = "",
            patterns = listOf()
        )

        assertEquals(expected, PlaylistUpdate.fromText(gson, raw))
    }

    @Test
    fun testParseAck() {
        val raw = """
            {"ack": 1}
        """
        val expected = Ack
        assertEquals(expected, Ack.fromText(gson, raw))
    }

    @Test
    fun testParseDiscovery() {
        val raw = """
            [{
                "version": "3.40",
                "boardType": "pico32",
                "ip": "::ffff:71.236.195.4",
                "createdAt": "2023-06-14T19:43:59.578Z",
                "arch": "esp32",
                "id": "pixelblaze_pico32_68285560a124",
                "localIp": "10.0.0.68",
                "name": "Pixelblaze_682855"
            }]
        """

        val expected = listOf(
            Discovered(
                id = "pixelblaze_pico32_68285560a124",
                name = "Pixelblaze_682855",
                lastSeen = Instant.parse("2023-06-14T19:43:59.578Z"),
                version = "3.40",
                remoteIp = "::ffff:71.236.195.4",
                localIp = "10.0.0.68",
                boardType = "pico32",
                arch = "esp32",
            )
        )

        assertEquals(expected, Discovery.discoveredListFromText(gson, raw))
    }
}