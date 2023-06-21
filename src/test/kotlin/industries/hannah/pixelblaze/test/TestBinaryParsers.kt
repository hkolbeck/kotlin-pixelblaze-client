package industries.hannah.pixelblaze.test

import com.google.gson.Gson
import industries.hannah.pixelblaze.*
import io.ktor.http.content.*
import io.ktor.websocket.*
import org.junit.jupiter.api.Test
import sun.awt.image.ToolkitImage
import java.io.InputStream
import java.util.*
import javax.imageio.ImageIO
import kotlin.streams.toList
import kotlin.test.Ignore
import kotlin.test.assertEquals

@Ignore("Need to collect inbound examples and verified expected")
class TestBinaryParsers {

    private val gson = Gson()

    // Won't be connected, but we don't need it to be. Will be a little bit chatty. We're just using the client
    // parse binary utility function to get an initial split of the frame, which takes some introspection into watcher
    // state
    private val pixelblaze = WebsocketPixelblaze.defaultBuilder()
        .setConnectionWatcher { connectionEvent, s, throwable ->
            println("Event: $connectionEvent, msg: '$s', thrown: ${throwable?.message}, stack: ${throwable?.stackTrace}")
        }
        .build()

    @Test
    fun testPreviewImageParser() {
        val (type, stream) = readInbound("/binary_samples/inbound/preview_image.b64")
        assertEquals(InboundPreviewImage, type)
        val (patternId, img) = PreviewImage.fromBinary(stream)!!

        val expectedPatternId = ""
        assertEquals(expectedPatternId, patternId)

        // It worked once, the expected image here was manually verified
        val expectedRaw = readExpected("/binary_samples/expected/preview_frame.jpg")
        val expectedImage = ImageIO.read(expectedRaw.inputStream())!!

        for (x in 0 until 10) {
            for (y in 0 until 1024) {
                assertEquals(expectedImage.getRGB(x, y), img.getRGB(x, y))
            }
        }
    }

    @Test
    fun testPreviewFrameParser() {
        val (type, stream) = readInbound("/binary_samples/inbound/preview_frame.b64")
        assertEquals(InboundPreviewFrame, type)
        val frame = PreviewFrame.fromBinary(stream)!!

        val expected = gson.fromJson<List<Triple<UByte, UByte, UByte>>>(
            readExpected("/binary_samples/expected/preview_frame.json")
                .inputStream()
                .bufferedReader(),
            List::class.java
        ).map { (r, g, b) -> Pixel(r, g, b) }

        assertEquals(expected, frame.pixels)
    }

    @Test
    fun testPreviewFrameToImage() {
        val (type, stream) = readInbound("/binary_samples/inbound/preview_frame.b64")
        assertEquals(InboundPreviewFrame, type)

        val fromBinary = PreviewFrame.fromBinary(stream)!!
        val previewImage = fromBinary.toImage(1024u, 10u)
        val bufferedImage = (previewImage as ToolkitImage).bufferedImage

        // It worked once, the expected image here was manually verified
        val expectedRaw = readExpected("/binary_samples/expected/preview_frame.jpg")
        val expectedImage = ImageIO.read(expectedRaw.inputStream())!!

        for (x in 0 until 10) {
            for (y in 0 until 1024) {
                assertEquals(expectedImage.getRGB(x, y), bufferedImage.getRGB(x, y))
            }
        }
    }

    @Test
    fun testAllPrograms() {
        val (type, stream) = readInbound("/binary_samples/inbound/all_programs.b64")
        assertEquals(InboundAllPrograms, type)
        val allPrograms = AllPrograms.fromBinary(stream)!!

        val expected = readExpected("/binary_samples/expected/qll_programs.csv")
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
        val (type, stream) = readInbound("/binary_samples/inbound/expander_channels.b64")
        assertEquals(InboundExpanderChannels, type)
        val channels = ExpanderChannels.fromBinary(stream)
    }

    private fun readInbound(path: String): Pair<InboundBinary<*>, InputStream> =
        object {}.javaClass.classLoader.getResource(path)?.readText()?.run {
            pixelblaze.readBinaryFrame(Frame.Binary(true, Base64.getDecoder().decode(this)))!!
        }!!

    private fun readExpected(path: String): ByteArray =
        object {}.javaClass.classLoader.getResource(path)?.readBytes()!!
}