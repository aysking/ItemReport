package com.itemreport.app

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR code holding an item's ID, name and capture date. */
object Qr {

    /** Capture date: when the photo was taken, or the condition date if there is no photo. */
    fun captureTime(hasPhoto: Boolean, photoAt: Long, conditionAt: Long): Long =
        if (hasPhoto && photoAt > 0) photoAt else conditionAt

    fun text(uid: String, name: String, capturedAt: Long): String =
        "ID: " + uid + "\nItem: " + name + "\nCaptured: " + Exporter.formatTime(capturedAt)

    /** Modules as [row][column], true = dark, including a quiet zone. Null if encoding fails. */
    fun matrix(text: String): Array<BooleanArray>? {
        return try {
            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
            hints[EncodeHintType.MARGIN] = 3
            val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
            Array(bits.height) { y -> BooleanArray(bits.width) { x -> bits.get(x, y) } }
        } catch (e: Exception) {
            null
        }
    }

    fun bitmap(matrix: Array<BooleanArray>, scale: Int): Bitmap {
        val n = matrix.size
        val side = n * scale
        val pixels = IntArray(side * side)
        for (y in 0 until side) {
            val row = matrix[y / scale]
            for (x in 0 until side) {
                val mx = x / scale
                pixels[y * side + x] = if (mx < row.size && row[mx]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }
}
