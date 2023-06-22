package industries.hannah.pixelblaze.android

import android.graphics.Bitmap
import android.util.Base64InputStream
import android.util.Base64OutputStream
import industries.hannah.pixelblaze.*
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.util.*
import kotlin.test.assertTrue


class TestAndroidImageUtils {

    private val testFrameB64 =
        "BQAAAAEDAAoBAA4DAA8DAAUAAAkAAA0CAAkAAAoBAAsBABQJABEGAA8DAAQAAAQAAAQAAAQAAA0CAAsBAAkAAAECAAEDAAQAAAAAAAEBAAQAAAcAAAcAAAcAAAEDAAYAAAsBAAYAAAgAAAoBABYLABQJABIHAAcAAAgAAAoBAA0CABYLABQIABIGAAoBAAoBAAoBAAAAAAEDAAYAAAQAAAQAAAECAAUAAAkAABIGAA0CAAkAAAYAAAYAAAYAAAsBAAcAAAQAAAECAAAAAAECAAQAAA4DAAsBAAgAAAAAAAAAAAECAAAAAAECAAMAAAgAAAgAAAkAAAECAAQAAAcAAAEBAAUAAAsBABkPABMHAA4DAAoBAAoBAAsBAAsBABAFABcMABQIABEFAA8DAA=="
    private val anotherFrame = "BRkAABMKCRkJBAAAAAAAAAAAABkSABkXDBkYBAICAAAAAAAAAA4ZAAMFAgsZBAMSAA0ZDAAAAAAIAQAAAAEFAgAYDAwZFAACAgAAAAAAAAEGBwAEBQwUGQMLEwABAwAAAAMGFQAAAAYGDQcEFggAGQIBAwsDEQAAAAAAAAQABRkAGBQJEggBBgAAAAEAAQAAABkABhcLDBUDAwIAABkPDAAAAAAAAAQDAhkTBBURABkYDAAAAAAAAAAAABAZBAoYABAZDAACAAAEAAAAAAQYBwAFAQwZEQMTDAAZEAAAAAAAAAAAAAwXGQQUGQAQGQEBAgAAAAAAAAwOGQEBBQIAGQoJEgsEGQAAAAYECAAAAAQABRcLGBkEGAIAAgAAAAAAAAcAAwUCAxkECRMAAg=="
    private val testImageB64 =
        "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"

    private val pixelblaze = WebsocketPixelblaze.defaultBuilder()
        .setErrorLog { t, str ->
            println("Error: '${str()}', thrown: '${t?.message ?: "Nothing"}'")
            t?.printStackTrace()
        }.setInfoLog { str -> println("Info: '${str()}'") }
        .setDebugLog { str -> println("Debug: '${str()}'") }
        .build()

    private val testUtil = pixelblaze.testUtil()

    /**
     * Just dumping its result ATM. It works but I haven't decided what to assert
     */
    @Test
    fun testFrameToImage() {
        val rawStream = Base64InputStream(anotherFrame.byteInputStream(), 0)
        val stream = testUtil.readBinaryFrame(rawStream.readBytes())

//        val rawStream = (0 until 100).map { listOf(0xFF.toByte(), 0, 0) }.flatten().toMutableList()
//        rawStream.add(0, 0x05)
//        val stream = testUtil.readBinaryFrame(rawStream.toByteArray())

        val frame = PreviewFrame.fromBinary(stream!!.second)
        val bitmap = frame.toBitmap(100u, 50u)!!

        assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, Base64OutputStream(System.out, 0)))
    }

    private fun readInbound(path: String): Pair<InboundBinary<*>, InputStream> =
        object {}.javaClass.getResource(path)?.readText()?.run {
            val binaryFrame = testUtil.readBinaryFrame(Base64.getDecoder().decode(this))
            println(
                """
                Got type: ${binaryFrame?.first}(${BinaryTypeFlag.fromByte(binaryFrame!!.first.binaryFlag)}) for $path
                """.trim()
            )
            binaryFrame
        }!!

    private fun readExpected(path: String): ByteArray =
        object {}.javaClass.getResource(path)?.readBytes()!!
}