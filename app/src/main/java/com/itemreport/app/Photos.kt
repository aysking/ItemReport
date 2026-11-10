package com.itemreport.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ExifInterface
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object Photos {

    /** Photos larger than this on the long side are halved until they fit. */
    private const val MAX_EDGE = 4800
    private const val JPEG_QUALITY = 93

    class Imported(val file: File, val takenAt: Long)

    fun dir(context: Context): File {
        val d = File(context.filesDir, "photos")
        d.mkdirs()
        return d
    }

    fun captureDir(context: Context): File {
        val d = File(context.cacheDir, "capture")
        d.mkdirs()
        return d
    }

    fun thumbFor(path: String): File = File(path.removeSuffix(".jpg") + "_t.jpg")

    /**
     * Turns a camera or gallery file into an upright, full-resolution JPEG in the
     * app's photo folder. The source file is deleted afterwards.
     */
    fun importImage(context: Context, src: File, useExifTime: Boolean): Imported {
        var orientation = ExifInterface.ORIENTATION_NORMAL
        var takenAt = System.currentTimeMillis()
        try {
            val exif = ExifInterface(src.absolutePath)
            orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            if (useExifTime) {
                val text = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (text != null) {
                    val parsed = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(text)
                    if (parsed != null) takenAt = parsed.time
                }
            }
        } catch (e: Exception) {
            // No readable EXIF data: keep the defaults.
        }

        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            src.delete()
            throw IOException("This file is not a readable image")
        }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE) sample *= 2

        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        var bmp = BitmapFactory.decodeFile(src.absolutePath, opts)
            ?: throw IOException("Could not read the photo")

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (rotated !== bmp) {
                bmp.recycle()
                bmp = rotated
            }
        }

        val name = "p_" + System.currentTimeMillis() + "_" + (1000..9999).random() + ".jpg"
        val out = File(dir(context), name)
        try {
            writeJpeg(bmp, out, JPEG_QUALITY)
        } finally {
            bmp.recycle()
        }
        src.delete()
        return Imported(out, takenAt)
    }

    /** Writes a copy of [src] with [text] printed in the bottom-right corner. */
    fun stamp(src: File, dst: File, text: String) {
        val opts = BitmapFactory.Options()
        opts.inMutable = true
        val bmp = BitmapFactory.decodeFile(src.absolutePath, opts)
            ?: throw IOException("Could not read the photo")
        try {
            val canvas = Canvas(bmp)
            val shortEdge = min(bmp.width, bmp.height).toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.WHITE
            paint.textSize = shortEdge * 0.04f
            paint.typeface = Typeface.DEFAULT_BOLD
            val pad = paint.textSize * 0.45f
            val margin = paint.textSize * 0.6f
            val fm = paint.fontMetrics
            val textW = paint.measureText(text)
            val textH = fm.descent - fm.ascent
            val right = bmp.width - margin
            val bottom = bmp.height - margin
            val left = right - textW - 2 * pad
            val top = bottom - textH - 2 * pad
            val bg = Paint()
            bg.color = Color.argb(160, 0, 0, 0)
            canvas.drawRect(left, top, right, bottom, bg)
            canvas.drawText(text, left + pad, top + pad - fm.ascent, paint)
            writeJpeg(bmp, dst, JPEG_QUALITY)
        } finally {
            bmp.recycle()
        }
    }

    fun makeThumb(src: File, dst: File) {
        val bmp = loadScaled(src, 360) ?: return
        try {
            writeJpeg(bmp, dst, 82)
        } finally {
            bmp.recycle()
        }
    }

    /** Decodes [file] to roughly [maxEdge] pixels on the long side. */
    fun loadScaled(file: File, maxEdge: Int): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun writeJpeg(bmp: Bitmap, file: File, quality: Int) {
        // Drop any HDR gain map so the file is a plain JPEG that PDF and Excel readers accept.
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                bmp.setGainmap(null)
            } catch (e: Exception) {
                // Not supported for this bitmap: save it as it is.
            }
        }
        FileOutputStream(file).use { os ->
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, quality, os)) {
                throw IOException("Could not save the photo")
            }
        }
    }
}
