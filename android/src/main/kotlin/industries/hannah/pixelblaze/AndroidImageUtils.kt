package industries.hannah.pixelblaze

import android.graphics.Bitmap
import kotlin.time.Duration

fun PreviewFrame.toBitmap(width: UInt, height: UInt): Bitmap? {
    val intArray = this.toIntArray()
    val bitmap = Bitmap.createBitmap(this.rawBytes.size / 3, 1, Bitmap.Config.ARGB_8888)
    for (x in 0 until bitmap.width) {
        bitmap.setPixel(x, 0, intArray[x])
    }

    return Bitmap.createScaledBitmap(bitmap, width.toInt(), height.toInt(), true)
}

fun PreviewImage.toBitmap(width: UInt, height: UInt): Bitmap? {
    TODO()
}

fun PreviewImage.toGif(width: UInt, height: UInt, frameDelay: Duration) {
    TODO()
}
