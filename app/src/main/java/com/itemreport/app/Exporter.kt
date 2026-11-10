package com.itemreport.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.FileProvider
import com.itemreport.app.export.ExportItem
import com.itemreport.app.export.ReportData
import com.itemreport.app.export.ReportPdf
import com.itemreport.app.export.XlsxWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Exporter {

    const val PDF_COMPACT = 0
    const val PDF_LARGE = 1
    const val XLSX = 2

    const val MIME_PDF = "application/pdf"
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun formatTime(millis: Long): String =
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.ENGLISH).format(Date(millis))

    fun formatDate(millis: Long): String =
        SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(millis))

    fun formatClock(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(millis))

    fun mimeFor(file: File): String = if (file.name.endsWith(".pdf")) MIME_PDF else MIME_XLSX

    fun dimensionsText(item: Item): String {
        if (item.length.isBlank() && item.width.isBlank() && item.height.isBlank()) return ""
        val l = item.length.ifBlank { "-" }
        val w = item.width.ifBlank { "-" }
        val h = item.height.ifBlank { "-" }
        return ("$l × $w × $h " + item.dimUnit).trim()
    }

    fun weightText(item: Item): String =
        if (item.weight.isBlank()) "" else (item.weight + " " + item.weightUnit).trim()

    fun locationText(item: Item): String {
        val parts = ArrayList<String>()
        if (item.aisle.isNotBlank()) parts.add("Aisle " + item.aisle)
        if (item.rack.isNotBlank()) parts.add("Rack " + item.rack)
        if (item.shelf.isNotBlank()) parts.add("Shelf " + item.shelf)
        return parts.joinToString(", ")
    }

    /** Builds the file in the cache folder and returns it. Call off the main thread. */
    fun export(context: Context, report: Report, items: List<Item>, kind: Int): File {
        val data = ReportData()
        data.title = report.title
        data.prefix = report.prefix
        data.created = formatTime(report.createdAt)
        data.generated = formatTime(System.currentTimeMillis())
        for (item in items) {
            val e = ExportItem()
            e.uid = item.uid
            e.name = item.name
            e.description = item.description
            e.area = item.area
            e.quantity = item.quantity
            e.length = item.length
            e.width = item.width
            e.height = item.height
            e.dimUnit = if (dimensionsText(item).isEmpty()) "" else item.dimUnit
            e.weight = item.weight
            e.weightUnit = if (item.weight.isBlank()) "" else item.weightUnit
            e.aisle = item.aisle
            e.rack = item.rack
            e.shelf = item.shelf
            e.condition = item.condition
            e.conditionAt = formatTime(item.conditionAt)
            e.dimensionsText = dimensionsText(item)
            e.weightText = weightText(item)
            e.locationText = locationText(item)
            val photo = item.photoFinal
            if (photo != null && File(photo).isFile) {
                e.photoPath = photo
                e.photoTaken = formatTime(item.photoAt)
            }
            data.items.add(e)
        }

        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val old = dir.listFiles()
        if (old != null) {
            for (f in old) f.delete()
        }

        var base = report.title.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_')
        if (base.length > 40) base = base.substring(0, 40)
        if (base.isEmpty()) base = "report"
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val ext = if (kind == XLSX) ".xlsx" else ".pdf"
        val file = File(dir, base + "_" + stamp + ext)

        try {
            FileOutputStream(file).use { os ->
                if (kind == XLSX) {
                    XlsxWriter.write(os, data)
                } else {
                    ReportPdf.write(os, data, kind == PDF_LARGE, AndroidRasterizer())
                }
            }
        } catch (t: Throwable) {
            file.delete()
            throw t
        }
        return file
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    fun share(context: Context, file: File) {
        val uri = uriFor(context, file)
        val send = Intent(Intent.ACTION_SEND)
        send.type = mimeFor(file)
        send.putExtra(Intent.EXTRA_STREAM, uri)
        send.clipData = ClipData.newRawUri(file.name, uri)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share report"))
    }

    fun open(context: Context, file: File) {
        val uri = uriFor(context, file)
        val view = Intent(Intent.ACTION_VIEW)
        view.setDataAndType(uri, mimeFor(file))
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(view)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app installed that can open this file", Toast.LENGTH_LONG)
                .show()
        }
    }

    /** Draws text in scripts the PDF's built-in fonts do not cover. */
    private class AndroidRasterizer : ReportPdf.TextRasterizer {
        override fun render(
            text: String,
            sizePt: Float,
            bold: Boolean,
            maxWidthPt: Float
        ): ReportPdf.Raster {
            val scale = 3f
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            paint.textSize = sizePt * scale
            paint.color = Color.BLACK
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val widthPx = (maxWidthPt * scale).toInt().coerceAtLeast(16)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
                .setMaxLines(12)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            val heightPx = layout.height.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            layout.draw(canvas)
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, bos)
            bmp.recycle()
            return ReportPdf.Raster(bos.toByteArray(), widthPx / scale, heightPx / scale)
        }
    }
}
