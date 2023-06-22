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
class TestTextParsers {

    private val gson = Gson()

    @Test
    fun testParseSequencerState() {
        val raw = """
            {
                "activeProgram": {
                    "name":"color bands",
                    "activeProgramId":"vmCXgwbox8nCBqQig",
                    "controls":{
                        "sliderSomeVar": 0.75
                    }
                },
                "sequencerMode":2,
                "runSequencer":true,
                "playlist":{
                    "position":1,
                    "id":"_defaultplaylist_",
                    "ms":30000,
                    "remainingMs":6549
                }
            }
        """
        val expected = SequencerState(
            activeProgram = ActiveProgram(
                name = "color bands",
                id = "vmCXgwbox8nCBqQig",
                controls = listOf(
                    Control("sliderSomeVar", 0.75F)
                )
            ),
            playlistState = PlaylistState(
                position = 1,
                id = "_defaultplaylist_",
                ttlMs = 30000,
                remainingMs = 6549,
            ),
            sequencerMode = SequencerMode.Playlist,
            runSequencer = true
        )
        assertEquals(expected, SequencerState.fromText(gson, raw))
    }

    @Test
    fun testParseSettings() {
        val raw = """
            {
                "name":"Pixelblaze_682855",
                "brandName":"",
                "pixelCount":100,
                "brightness":1,
                "maxBrightness":10,
                "colorOrder":"BGR",
                "dataSpeed":3500000,
                "ledType":2,
                "sequenceTimer":15,
                "transitionDuration":0,
                "sequencerMode":2,
                "runSequencer":true,
                "simpleUiMode":false,
                "learningUiMode":false,
                "discoveryEnable":true,
                "timezone":"",
                "autoOffEnable":true,
                "autoOffStart":"23:00",
                "autoOffEnd":"00:00",
                "cpuSpeed":240,
                "networkPowerSave":false,
                "mapperFit":0,
                "leaderId":0,
                "nodeId":0,
                "soundSrc":0,
                "accelSrc":1,
                "lightSrc":0,
                "analogSrc":1,
                "exp":0,
                "ver":"3.40",
                "chipId":6826069
            }
        """
        val expected = Settings(
            name = "Pixelblaze_682855",
            brandName = "",
            pixelCount = 100,
            brightness = 1.0F,
            maxBrightness = 0.1F,
            colorOrder = ColorOrder.BGR,
            dataSpeedHz = 3500000,
            ledType = LedType.WS2812_SK6812_NEOPIXEL,
            sequenceTimerSeconds = 15,
            transitionDurationMs = 0,
            sequencerMode = SequencerMode.Playlist,
            runSequencer = true,
            simpleUiMode = false,
            learningUiMode = false,
            discoveryEnabled = true,
            timezone = "",
            autoOffEnable = true,
            autoOffStart = "23:00",
            autoOffEnd = "00:00",
            cpuSpeedMhz = 240,
            networkPowerSave = false,
            mapperFit = 0,
            leaderId = 0,
            nodeId = 0,
            soundSrc = InputSource.Remote,
            lightSrc = InputSource.Remote,
            accelSrc = InputSource.Local,
            analogSrc = InputSource.Local,
            exp = 0,
            version = "3.40",
            chipId = 6826069,
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
            {
                "fps":177.8222,
                "vmerr":0,
                "vmerrpc":-1,
                "mem":10239,
                "exp":0,
                "renderType":1,
                "uptime":159889,
                "storageUsed":804706,
                "storageSize":1378241,
                "rr0":1,
                "rr1":14,
                "rebootCounter":0
            }
        """
        val expected = Stats(
            fps = 177.8222F,
            vmerr = 0,
            vmerrpc = -1,
            memBytes = 10239,
            expansions = 0,
            renderType = RenderType._1D,
            uptimeMs = 159889,
            storageBytesUsed = 804706,
            storageBytesSize = 1378241,
            rr0 = 1,
            rr1 = 14,
            rebootCounter = 0,
        )
        assertEquals(expected, Stats.fromText(gson, raw))
    }

    @Test
    fun testParsePlaylist() {
        val raw = """
            {
                "playlist":{
                    "position":1,
                    "id":"_defaultplaylist_",
                    "ms":30000,
                    "remainingMs":6535,
                    "items":[
                        {
                            "id":"QqZqNsLmk2CAvheRR",
                            "ms":30000
                        },{
                            "id":"vmCXgwbox8nCBqQig",
                            "ms":30000
                        }
                    ]
                }
            }
        """
        val expected = Playlist(
            id = "_defaultplaylist_",
            position = 1,
            currentDurationMs = 30000,
            remainingMs = 6535,
            patterns = listOf(
                PixelblazePattern("QqZqNsLmk2CAvheRR", 30000),
                PixelblazePattern("vmCXgwbox8nCBqQig", 30000)
            )
        )
        assertEquals(expected, Playlist.fromText(gson, raw))
    }

    @Test
    fun testParsePlaylistUpdate() {
        val raw = """
            {
                "playlist":{
                    "id":"_defaultplaylist_",
                    "items":[
                        {
                            "id":"QqZqNsLmk2CAvheRR",
                            "ms":30000
                        },{
                            "id":"vmCXgwbox8nCBqQig",
                            "ms":30000
                        }
                    ]
                }
            }
        """

        val expected = PlaylistUpdate(
            id = "_defaultplaylist_",
            patterns = listOf(
                PixelblazePattern("QqZqNsLmk2CAvheRR", 30000),
                PixelblazePattern("vmCXgwbox8nCBqQig", 30000)
            )
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