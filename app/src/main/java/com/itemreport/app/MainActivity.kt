package com.itemreport.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

/** Asks for the report name, ID prefix and next item number. */
object ReportDialog {
    fun show(
        activity: Activity,
        existing: Report?,
        onDone: (title: String, prefix: String, nextSeq: Int) -> Unit
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_report, null)
        val titleEt = view.findViewById<EditText>(R.id.reportTitle)
        val prefixEt = view.findViewById<EditText>(R.id.reportPrefix)
        val startEt = view.findViewById<EditText>(R.id.reportStart)
        if (existing != null) {
            titleEt.setText(existing.title)
            prefixEt.setText(existing.prefix)
            startEt.setText(existing.nextSeq.toString())
        } else {
            startEt.setText("1")
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(if (existing == null) "New report" else "Report details")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (existing == null) "Create" else "Save") { _, _ ->
                var title = titleEt.text.toString().trim()
                if (title.isEmpty()) {
                    title = "Report " + Exporter.formatDate(System.currentTimeMillis())
                }
                val prefix = prefixEt.text.toString().trim()
                var seq = startEt.text.toString().trim().toIntOrNull() ?: 1
                if (seq < 1) seq = 1
                onDone(title, prefix, seq)
            }
            .show()
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var db: Db
    private lateinit var emptyView: TextView
    private val adapter = ReportAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        title = "Item Report"
        db = Db.get(this)

        emptyView = findViewById(R.id.empty)
        emptyView.text = "No reports yet.\nTap “New report” to start one."

        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fab)
        fab.text = "New report"
        fab.setOnClickListener {
            ReportDialog.show(this, null) { title, prefix, seq ->
                val id = db.insertReport(title, prefix, seq)
                openReport(id)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val reports = db.reports()
        adapter.submit(reports)
        emptyView.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openReport(id: Long) {
        val intent = Intent(this, ReportActivity::class.java)
        intent.putExtra(ReportActivity.EXTRA_REPORT, id)
        startActivity(intent)
    }

    private inner class ReportAdapter : RecyclerView.Adapter<ReportHolder>() {
        private var rows: List<Report> = emptyList()

        fun submit(list: List<Report>) {
            rows = list
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.row_report, parent, false)
            return ReportHolder(v)
        }

        override fun onBindViewHolder(holder: ReportHolder, position: Int) {
            val r = rows[position]
            holder.title.text = r.title
            val count = if (r.itemCount == 1) "1 item" else r.itemCount.toString() + " items"
            val prefix = if (r.prefix.isEmpty()) "" else "  •  IDs " + r.prefix + "0001…"
            holder.subtitle.text = count + "  •  " + Exporter.formatDate(r.createdAt) + prefix
            holder.itemView.setOnClickListener { openReport(r.id) }
        }
    }

    private class ReportHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
        val subtitle: TextView = view.findViewById(R.id.subtitle)
    }
}
