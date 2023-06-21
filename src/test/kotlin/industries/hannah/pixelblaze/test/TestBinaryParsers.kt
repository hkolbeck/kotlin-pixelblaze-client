package industries.hannah.pixelblaze.test

import com.google.gson.Gson
import industries.hannah.pixelblaze.*
import io.ktor.http.content.*
import io.ktor.websocket.*
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.util.*
import kotlin.streams.toList
import kotlin.test.Ignore
import kotlin.test.assertEquals

class TestBinaryParsers {

    private val gson = Gson()

    // Won't be connected, but we don't need it to be. Will be a little bit chatty. We're just using the client
    // parse binary utility function to get an initial split of the frame, which takes some introspection into watcher
    // state
    private val pixelblaze = WebsocketPixelblaze.defaultBuilder()
        .setErrorLog { t, str ->
            println("Error: '${str()}', thrown: '${t?.message ?: "Nothing"}'")
            t?.printStackTrace()
        }.setInfoLog { str -> println("Info: '${str()}'") }
        .setDebugLog { str -> println("Debug: '${str()}'") }
        .build()

    @Test
    fun testPreviewImageParser() {
        val (_, stream) = readInbound("/binary_samples/inbound/preview_image.b64")
        val parsed = PreviewImage.fromBinary(stream)!!
        assertEquals("vkpGsR88dcJmXfevp", parsed.patternId)

        // It worked once, the expected image here was manually verified
        val expectedRaw = readExpected("/binary_samples/expected/preview_image.jpg").inputStream()
        while (true) {
            val parsedStreamRead = parsed.imgBytes.read()
            val expectedRead = expectedRaw.read()
            assertEquals(expectedRead, parsedStreamRead)

            if (parsedStreamRead < 0) {
                break
            }
        }
    }

    @Test
    fun testPreviewFrameParser() {
        val (_, stream) = readInbound("/binary_samples/inbound/preview_frame.b64")
        val frame = PreviewFrame.fromBinary(stream)!!

        val expected = readExpected("/binary_samples/expected/preview_frame.txt")
            .inputStream()
            .bufferedReader()
            .lines()
            .map { it.toInt() }
            .toList()

        assertEquals(expected, frame.pixels)
    }

    @Test
    @Ignore
    fun testPreviewFrameToImage() {
//        val (_, stream) = readInbound("/binary_samples/inbound/preview_frame.b64")
//
//        val fromBinary = PreviewFrame.fromBinary(stream)!!
//        val previewImage = fromBinary.toImage(1024u, 10u)
//        val bufferedImage = (previewImage as ToolkitImage).bufferedImage
//
//        // It worked once, the expected image here was manually verified
//        val expectedRaw = readExpected("/binary_samples/expected/preview_frame.jpg")
//        val expectedImage = ImageIO.read(expectedRaw.inputStream())!!
//
//        for (x in 0 until 10) {
//            for (y in 0 until 1024) {
//                assertEquals(expectedImage.getRGB(x, y), bufferedImage.getRGB(x, y))
//            }
//        }
    }

    @Test
    fun testAllPrograms() {
        val (_, stream) = readInbound("/binary_samples/inbound/all_programs.b64")
        val allPrograms = AllPrograms.fromBinary(stream)!!

        val expected = readExpected("/binary_samples/expected/all_programs.csv")
            .inputStream()
            .bufferedReader()
            .lines()
            .map { line ->
                val (id, name) = line.split(Regex(","), 2)
                NamedPattern(id, name)
            }.toList()

        assertEquals(expected, allPrograms.patterns)
    }

    @Test
    @Ignore("Don't currently have an example raw request")
    fun testExpanderChannels() {
        val (_, stream) = readInbound("/binary_samples/inbound/expander_channels.b64")
        val ignored = ExpanderChannels.fromBinary(stream)
    }

    private fun readInbound(path: String): Pair<InboundBinary<*>, InputStream> =
        object {}.javaClass.getResource(path)?.readText()?.run {
            val binaryFrame = pixelblaze.readBinaryFrame(Frame.Binary(true, Base64.getDecoder().decode(this)))
            println("Got type: ${binaryFrame?.first}(${BinaryTypeFlag.fromByte(binaryFrame!!.first.binaryFlag)}) for file $path")
            binaryFrame
        }!!

    private fun readExpected(path: String): ByteArray =
        object {}.javaClass.getResource(path)?.readBytes()!!
}