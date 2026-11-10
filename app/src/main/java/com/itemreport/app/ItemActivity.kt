package com.itemreport.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.concurrent.Executors

class ItemActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REPORT = "report_id"
        const val EXTRA_ITEM = "item_id"
        private const val PREFS = "prefs"
        private const val STATE_PHOTO = "photo_orig"
        private const val STATE_PHOTO_AT = "photo_at"
        private const val STATE_CONDITION_AT = "condition_at"
        private const val STATE_CAPTURE = "capture_path"
        private const val STATE_CREATED = "created_files"
    }

    private lateinit var db: Db
    private var reportId = 0L
    private var editing: Item? = null

    private var photoOrig: String? = null
    private var photoAt = 0L
    private var conditionAt = 0L
    private var capturePath: String? = null
    private var createdFiles = ArrayList<String>()
    private var saved = false
    private var busy = false

    private val io = Executors.newSingleThreadExecutor()

    private lateinit var uidLabel: TextView
    private lateinit var photoView: ImageView
    private lateinit var photoHint: TextView
    private lateinit var stampPreview: TextView
    private lateinit var stampSwitch: MaterialSwitch
    private lateinit var nameLayout: TextInputLayout
    private lateinit var nameEt: EditText
    private lateinit var descriptionEt: EditText
    private lateinit var areaEt: EditText
    private lateinit var quantityEt: EditText
    private lateinit var lengthEt: EditText
    private lateinit var widthEt: EditText
    private lateinit var heightEt: EditText
    private lateinit var dimUnitEt: MaterialAutoCompleteTextView
    private lateinit var weightEt: EditText
    private lateinit var weightUnitEt: MaterialAutoCompleteTextView
    private lateinit var aisleEt: EditText
    private lateinit var rackEt: EditText
    private lateinit var shelfEt: EditText
    private lateinit var conditionEt: MaterialAutoCompleteTextView
    private lateinit var btnDate: MaterialButton
    private lateinit var btnTime: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var qrView: ImageView
    private lateinit var qrCaption: TextView
    private var uidText = ""

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val path = capturePath
            capturePath = null
            if (path != null) {
                val file = File(path)
                if (ok && file.length() > 0) {
                    importPhoto(file, false)
                } else {
                    file.delete()
                }
            }
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) copyAndImport(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        db = Db.get(this)
        reportId = intent.getLongExtra(EXTRA_REPORT, 0L)
        val itemId = intent.getLongExtra(EXTRA_ITEM, 0L)
        val report = db.report(reportId)
        if (report == null) {
            finish()
            return
        }
        val existing = if (itemId != 0L) db.item(itemId) else null
        editing = existing

        uidLabel = findViewById(R.id.uidLabel)
        photoView = findViewById(R.id.photo)
        photoHint = findViewById(R.id.photoHint)
        stampPreview = findViewById(R.id.stampPreview)
        stampSwitch = findViewById(R.id.stampSwitch)
        nameLayout = findViewById(R.id.nameLayout)
        nameEt = findViewById(R.id.name)
        descriptionEt = findViewById(R.id.description)
        areaEt = findViewById(R.id.area)
        quantityEt = findViewById(R.id.quantity)
        lengthEt = findViewById(R.id.length)
        widthEt = findViewById(R.id.width)
        heightEt = findViewById(R.id.height)
        dimUnitEt = findViewById(R.id.dimUnit)
        weightEt = findViewById(R.id.weight)
        weightUnitEt = findViewById(R.id.weightUnit)
        aisleEt = findViewById(R.id.aisle)
        rackEt = findViewById(R.id.rack)
        shelfEt = findViewById(R.id.shelf)
        conditionEt = findViewById(R.id.condition)
        btnDate = findViewById(R.id.btnDate)
        btnTime = findViewById(R.id.btnTime)
        btnSave = findViewById(R.id.btnSave)
        qrView = findViewById(R.id.qrImage)
        qrCaption = findViewById(R.id.qrCaption)

        dimUnitEt.setSimpleItems(arrayOf("cm", "mm", "m", "in", "ft"))
        weightUnitEt.setSimpleItems(arrayOf("kg", "g", "lb", "t"))
        conditionEt.setSimpleItems(
            arrayOf("New", "Good", "Fair", "Poor", "Damaged", "Needs repair", "Scrap")
        )

        if (existing != null) {
            title = "Edit item"
            uidText = existing.uid
            uidLabel.text = "ID  " + existing.uid
        } else {
            title = "New item"
            uidText = Db.formatUid(report.prefix, report.nextSeq)
            uidLabel.text = "ID  " + uidText
        }

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (savedInstanceState == null) {
            if (existing != null) {
                photoOrig = existing.photoOrig
                photoAt = existing.photoAt
                conditionAt = existing.conditionAt
                stampSwitch.isChecked = existing.stamped
                nameEt.setText(existing.name)
                descriptionEt.setText(existing.description)
                areaEt.setText(existing.area)
                quantityEt.setText(existing.quantity)
                lengthEt.setText(existing.length)
                widthEt.setText(existing.width)
                heightEt.setText(existing.height)
                dimUnitEt.setText(existing.dimUnit, false)
                weightEt.setText(existing.weight)
                weightUnitEt.setText(existing.weightUnit, false)
                aisleEt.setText(existing.aisle)
                rackEt.setText(existing.rack)
                shelfEt.setText(existing.shelf)
                conditionEt.setText(existing.condition, false)
            } else {
                conditionAt = System.currentTimeMillis()
                stampSwitch.isChecked = prefs.getBoolean("stamp", true)
                quantityEt.setText("1")
                dimUnitEt.setText(prefs.getString("dim_unit", "cm") ?: "cm", false)
                weightUnitEt.setText(prefs.getString("weight_unit", "kg") ?: "kg", false)
                conditionEt.setText("Good", false)
            }
        } else {
            photoOrig = savedInstanceState.getString(STATE_PHOTO)
            photoAt = savedInstanceState.getLong(STATE_PHOTO_AT)
            conditionAt = savedInstanceState.getLong(STATE_CONDITION_AT)
            capturePath = savedInstanceState.getString(STATE_CAPTURE)
            createdFiles = savedInstanceState.getStringArrayList(STATE_CREATED) ?: ArrayList()
        }

        findViewById<View>(R.id.btnCamera).setOnClickListener { launchCamera() }
        findViewById<View>(R.id.btnGallery).setOnClickListener {
            if (!busy) {
                try {
                    pickImage.launch("image/*")
                } catch (e: ActivityNotFoundException) {
                    toast("No gallery app found")
                }
            }
        }
        stampSwitch.setOnCheckedChangeListener { _, _ -> refreshStampPreview() }
        btnDate.setOnClickListener { pickDate() }
        btnTime.setOnClickListener { pickTime() }
        btnSave.setOnClickListener { save() }
        nameEt.doAfterTextChanged { refreshQr() }

        refreshConditionButtons()
        refreshPhoto()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PHOTO, photoOrig)
        outState.putLong(STATE_PHOTO_AT, photoAt)
        outState.putLong(STATE_CONDITION_AT, conditionAt)
        outState.putString(STATE_CAPTURE, capturePath)
        outState.putStringArrayList(STATE_CREATED, createdFiles)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !saved) {
            // Left without saving: remove photos taken during this visit.
            val leftovers = ArrayList(createdFiles)
            io.execute {
                for (p in leftovers) File(p).delete()
            }
        }
        io.shutdown()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (editing != null) menuInflater.inflate(R.menu.menu_item, menu)
        return true
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.action_delete_item) {
            confirmDelete()
            return true
        }
        return super.onOptionsItemSelected(menuItem)
    }

    // ---------- photo ----------

    private fun launchCamera() {
        if (busy) return
        val file = File(Photos.captureDir(this), "cap_" + System.currentTimeMillis() + ".jpg")
        capturePath = file.absolutePath
        val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", file)
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            capturePath = null
            toast("No camera app found")
        }
    }

    private fun copyAndImport(uri: Uri) {
        setBusy(true)
        val temp = File(Photos.captureDir(this), "pick_" + System.currentTimeMillis() + ".jpg")
        io.execute {
            var ok = false
            try {
                val input = contentResolver.openInputStream(uri)
                if (input != null) {
                    input.use { ins ->
                        FileOutputStream(temp).use { os -> ins.copyTo(os, 1 shl 16) }
                    }
                    ok = true
                }
            } catch (e: Exception) {
                ok = false
            }
            val copied = ok
            runOnUiThread {
                setBusy(false)
                if (copied) {
                    importPhoto(temp, true)
                } else {
                    temp.delete()
                    toast("Could not open that picture")
                }
            }
        }
    }

    private fun importPhoto(source: File, useExifTime: Boolean) {
        setBusy(true)
        photoHint.text = "Processing photo…"
        photoHint.visibility = View.VISIBLE
        val appContext = applicationContext
        io.execute {
            var imported: Photos.Imported? = null
            var error: String? = null
            try {
                imported = Photos.importImage(appContext, source, useExifTime)
            } catch (t: Throwable) {
                error = t.message ?: "Could not read the photo"
            }
            val result = imported
            val problem = error
            runOnUiThread {
                setBusy(false)
                if (result != null) {
                    val path = result.file.absolutePath
                    createdFiles.add(path)
                    photoOrig = path
                    photoAt = result.takenAt
                } else {
                    toast(problem ?: "Could not read the photo")
                }
                refreshPhoto()
            }
        }
    }

    private fun refreshPhoto() {
        val path = photoOrig
        if (path == null) {
            photoView.setImageDrawable(null)
            photoHint.text = "No photo yet"
            photoHint.visibility = View.VISIBLE
            refreshStampPreview()
            return
        }
        refreshStampPreview()
        io.execute {
            var bmp: Bitmap? = null
            try {
                bmp = Photos.loadScaled(File(path), 1400)
            } catch (t: Throwable) {
                bmp = null
            }
            val preview = bmp
            runOnUiThread {
                if (path == photoOrig) {
                    if (preview != null) {
                        photoView.setImageBitmap(preview)
                        photoHint.visibility = View.GONE
                    } else {
                        photoView.setImageDrawable(null)
                        photoHint.text = "Photo file is missing"
                        photoHint.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun refreshQr() {
        val captured = Qr.captureTime(photoOrig != null, photoAt, conditionAt)
        val text = Qr.text(uidText, nameEt.text.toString().trim(), captured)
        val matrix = Qr.matrix(text)
        if (matrix != null) {
            qrView.setImageBitmap(Qr.bitmap(matrix, 8))
        } else {
            qrView.setImageDrawable(null)
        }
        qrCaption.text = text
    }

    private fun refreshStampPreview() {
        refreshQr()
        if (photoOrig != null && stampSwitch.isChecked) {
            stampPreview.text = Exporter.formatTime(photoAt)
            stampPreview.visibility = View.VISIBLE
        } else {
            stampPreview.visibility = View.GONE
        }
    }

    // ---------- condition date and time ----------

    private fun refreshConditionButtons() {
        refreshQr()
        btnDate.text = Exporter.formatDate(conditionAt)
        btnTime.text = Exporter.formatClock(conditionAt)
    }

    private fun pickDate() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = conditionAt
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val c = Calendar.getInstance()
                c.timeInMillis = conditionAt
                c.set(Calendar.YEAR, year)
                c.set(Calendar.MONTH, month)
                c.set(Calendar.DAY_OF_MONTH, day)
                conditionAt = c.timeInMillis
                refreshConditionButtons()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickTime() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = conditionAt
        TimePickerDialog(
            this,
            { _, hour, minute ->
                val c = Calendar.getInstance()
                c.timeInMillis = conditionAt
                c.set(Calendar.HOUR_OF_DAY, hour)
                c.set(Calendar.MINUTE, minute)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                conditionAt = c.timeInMillis
                refreshConditionButtons()
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    // ---------- save and delete ----------

    private fun save() {
        if (busy) return
        val name = nameEt.text.toString().trim()
        if (name.isEmpty()) {
            nameLayout.error = "Enter the item name"
            nameEt.requestFocus()
            return
        }
        nameLayout.error = null

        val existing = editing
        val stampOn = stampSwitch.isChecked
        val origPath = photoOrig

        val item = Item()
        if (existing != null) {
            item.id = existing.id
            item.uid = existing.uid
        }
        item.reportId = reportId
        item.photoOrig = origPath
        item.photoAt = photoAt
        item.stamped = stampOn && origPath != null
        item.name = name
        item.description = descriptionEt.text.toString().trim()
        item.area = areaEt.text.toString().trim()
        item.quantity = quantityEt.text.toString().trim()
        item.length = lengthEt.text.toString().trim()
        item.width = widthEt.text.toString().trim()
        item.height = heightEt.text.toString().trim()
        item.dimUnit = dimUnitEt.text.toString().trim()
        item.weight = weightEt.text.toString().trim()
        item.weightUnit = weightUnitEt.text.toString().trim()
        item.aisle = aisleEt.text.toString().trim()
        item.rack = rackEt.text.toString().trim()
        item.shelf = shelfEt.text.toString().trim()
        item.condition = conditionEt.text.toString().trim()
        item.conditionAt = conditionAt

        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("stamp", stampOn)
            .putString("dim_unit", item.dimUnit)
            .putString("weight_unit", item.weightUnit)
            .apply()

        setBusy(true)
        btnSave.text = "Saving…"
        val stampText = Exporter.formatTime(photoAt)
        val created = ArrayList(createdFiles)

        io.execute {
            var error: String? = null
            try {
                var finalPath: String? = null
                if (origPath != null) {
                    if (stampOn) {
                        val oldFinal = existing?.photoFinal
                        val reusable = existing != null && existing.stamped &&
                            existing.photoOrig == origPath && existing.photoAt == item.photoAt &&
                            oldFinal != null && File(oldFinal).isFile
                        if (reusable) {
                            finalPath = oldFinal
                        } else {
                            val stamped = File(
                                origPath.removeSuffix(".jpg") + "_s" +
                                    System.currentTimeMillis() + ".jpg"
                            )
                            Photos.stamp(File(origPath), stamped, stampText)
                            finalPath = stamped.absolutePath
                        }
                    } else {
                        finalPath = origPath
                    }
                }
                val finalFile = finalPath
                if (finalFile != null) {
                    Photos.makeThumb(File(finalFile), Photos.thumbFor(finalFile))
                }
                item.photoFinal = finalFile

                if (existing == null) db.insertItem(item) else db.updateItem(item)

                // Remove photo files this item no longer uses.
                val stale = ArrayList<String>()
                if (existing != null) {
                    val a = existing.photoOrig
                    val b = existing.photoFinal
                    if (a != null) stale.add(a)
                    if (b != null) stale.add(b)
                }
                stale.addAll(created)
                for (p in stale) {
                    if (p != item.photoOrig && p != item.photoFinal) {
                        File(p).delete()
                        Photos.thumbFor(p).delete()
                    }
                }
            } catch (t: Throwable) {
                error = t.message ?: t.javaClass.simpleName
            }
            val problem = error
            runOnUiThread {
                if (problem == null) {
                    saved = true
                    finish()
                } else {
                    setBusy(false)
                    btnSave.text = "Save item"
                    toast("Could not save: $problem")
                }
            }
        }
    }

    private fun confirmDelete() {
        val existing = editing ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete " + existing.uid + "?")
            .setMessage("This item and its photo will be removed. Its ID will not be reused.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                db.deleteItem(existing.id)
                val paths = listOf(existing.photoOrig, existing.photoFinal)
                for (p in paths) {
                    if (p != null) {
                        File(p).delete()
                        Photos.thumbFor(p).delete()
                    }
                }
                finish()
            }
            .show()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        btnSave.isEnabled = !value
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
