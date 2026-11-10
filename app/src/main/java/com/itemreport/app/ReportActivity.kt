package com.itemreport.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors

class ReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REPORT = "report_id"
        private const val STATE_EXPORTED = "exported"
    }

    private lateinit var db: Db
    private lateinit var emptyView: TextView
    private var reportId = 0L
    private var report: Report? = null
    private var items: List<Item> = emptyList()
    private val adapter = ItemAdapter()
    private val io = Executors.newSingleThreadExecutor()
    private var exportedPath: String? = null

    private val savePdf =
        registerForActivityResult(ActivityResultContracts.CreateDocument(Exporter.MIME_PDF)) { uri ->
            copyExportTo(uri)
        }
    private val saveXlsx =
        registerForActivityResult(ActivityResultContracts.CreateDocument(Exporter.MIME_XLSX)) { uri ->
            copyExportTo(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        db = Db.get(this)
        reportId = intent.getLongExtra(EXTRA_REPORT, 0L)
        exportedPath = savedInstanceState?.getString(STATE_EXPORTED)

        emptyView = findViewById(R.id.empty)
        emptyView.text = "No items yet.\nTap “Add item” to record the first one."

        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fab)
        fab.text = "Add item"
        fab.setOnClickListener {
            val intent = Intent(this, ItemActivity::class.java)
            intent.putExtra(ItemActivity.EXTRA_REPORT, reportId)
            startActivity(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_EXPORTED, exportedPath)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdown()
    }

    private fun reload() {
        val r = db.report(reportId)
        if (r == null) {
            finish()
            return
        }
        report = r
        items = db.items(reportId)
        title = r.title
        val count = if (items.size == 1) "1 item" else items.size.toString() + " items"
        supportActionBar?.subtitle =
            count + "  •  next ID " + Db.formatUid(r.prefix, r.nextSeq)
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_report, menu)
        return true
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_export -> {
                chooseExport()
                return true
            }
            R.id.action_edit_report -> {
                val r = report ?: return true
                ReportDialog.show(this, r) { newTitle, prefix, seq ->
                    db.updateReport(r.id, newTitle, prefix, seq)
                    reload()
                }
                return true
            }
            R.id.action_delete_report -> {
                confirmDelete()
                return true
            }
        }
        return super.onOptionsItemSelected(menuItem)
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete this report?")
            .setMessage("The report, its items and their photos will be removed from this phone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val doomed = items
                db.deleteReport(reportId)
                for (item in doomed) deletePhotos(item)
                finish()
            }
            .show()
    }

    private fun deletePhotos(item: Item) {
        val paths = listOf(item.photoOrig, item.photoFinal)
        for (p in paths) {
            if (p != null) {
                File(p).delete()
                Photos.thumbFor(p).delete()
            }
        }
    }

    // ---------- export ----------

    private fun chooseExport() {
        if (items.isEmpty()) {
            Toast.makeText(this, "Add at least one item first", Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf<CharSequence>(
            "PDF – compact (about 3 items per page)",
            "PDF – large photos (1 item per page)",
            "Excel (.xlsx) with photos"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Export report")
            .setItems(options) { _, which -> startExport(which) }
            .show()
    }

    private fun startExport(kind: Int) {
        val r = report ?: return
        val snapshot = items
        val progress: AlertDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Exporting…")
            .setMessage("Adding full-resolution photos. This can take a moment.")
            .setCancelable(false)
            .show()
        io.execute {
            var file: File? = null
            var error: String? = null
            try {
                file = Exporter.export(applicationContext, r, snapshot, kind)
            } catch (t: Throwable) {
                error = t.message ?: t.javaClass.simpleName
            }
            val result = file
            val problem = error
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    progress.dismiss()
                    if (result != null) {
                        exportedPath = result.absolutePath
                        showExported(result)
                    } else {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Export failed")
                            .setMessage(problem ?: "Unknown error")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun showExported(file: File) {
        val mb = file.length() / (1024.0 * 1024.0)
        val size = String.format(java.util.Locale.US, "%.1f MB", mb)
        MaterialAlertDialogBuilder(this)
            .setTitle("Report ready")
            .setMessage(file.name + "\n" + size)
            .setPositiveButton("Share") { _, _ -> Exporter.share(this, file) }
            .setNegativeButton("Save to phone") { _, _ ->
                if (file.name.endsWith(".pdf")) savePdf.launch(file.name)
                else saveXlsx.launch(file.name)
            }
            .setNeutralButton("Open") { _, _ -> Exporter.open(this, file) }
            .show()
    }

    private fun copyExportTo(uri: Uri?) {
        val path = exportedPath
        if (uri == null || path == null) return
        val source = File(path)
        io.execute {
            var ok = false
            try {
                contentResolver.openOutputStream(uri)?.use { os ->
                    FileInputStream(source).use { input -> input.copyTo(os, 1 shl 16) }
                    ok = true
                }
            } catch (e: Exception) {
                ok = false
            }
            val saved = ok
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (saved) "Saved" else "Could not save the file",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---------- list ----------

    private inner class ItemAdapter : RecyclerView.Adapter<ItemHolder>() {

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.row_item, parent, false)
            return ItemHolder(v)
        }

        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            val item = items[position]
            holder.uid.text = item.uid
            holder.title.text = item.name
            val parts = ArrayList<String>()
            if (item.quantity.isNotBlank()) parts.add("Qty " + item.quantity)
            if (item.condition.isNotBlank()) parts.add(item.condition)
            val location = Exporter.locationText(item)
            if (location.isNotEmpty()) parts.add(location)
            holder.subtitle.text = parts.joinToString("  •  ")

            val photo = item.photoFinal
            val thumb = if (photo != null) Photos.thumbFor(photo) else null
            if (thumb != null && thumb.isFile) {
                holder.thumb.setImageBitmap(BitmapFactory.decodeFile(thumb.absolutePath))
            } else {
                holder.thumb.setImageDrawable(null)
            }
            holder.itemView.setOnClickListener {
                val intent = Intent(this@ReportActivity, ItemActivity::class.java)
                intent.putExtra(ItemActivity.EXTRA_REPORT, reportId)
                intent.putExtra(ItemActivity.EXTRA_ITEM, item.id)
                startActivity(intent)
            }
        }
    }

    private class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val uid: TextView = view.findViewById(R.id.uid)
        val title: TextView = view.findViewById(R.id.title)
        val subtitle: TextView = view.findViewById(R.id.subtitle)
    }
}
