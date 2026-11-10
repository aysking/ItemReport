package com.itemreport.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale

data class Report(
    var id: Long = 0,
    var title: String = "",
    var prefix: String = "",
    var createdAt: Long = 0,
    var nextSeq: Int = 1,
    var itemCount: Int = 0
)

data class Item(
    var id: Long = 0,
    var reportId: Long = 0,
    var uid: String = "",
    var photoOrig: String? = null,
    var photoFinal: String? = null,
    var photoAt: Long = 0,
    var stamped: Boolean = false,
    var name: String = "",
    var description: String = "",
    var area: String = "",
    var quantity: String = "",
    var length: String = "",
    var width: String = "",
    var height: String = "",
    var dimUnit: String = "",
    var weight: String = "",
    var weightUnit: String = "",
    var aisle: String = "",
    var rack: String = "",
    var shelf: String = "",
    var condition: String = "",
    var conditionAt: Long = 0
)

class Db private constructor(context: Context) :
    SQLiteOpenHelper(context, "itemreport.db", null, 1) {

    companion object {
        @Volatile
        private var instance: Db? = null

        fun get(context: Context): Db {
            val existing = instance
            if (existing != null) return existing
            synchronized(this) {
                val again = instance
                if (again != null) return again
                val created = Db(context.applicationContext)
                instance = created
                return created
            }
        }

        fun formatUid(prefix: String, seq: Int): String =
            prefix + String.format(Locale.US, "%04d", seq)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE reports (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, " +
                "prefix TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "next_seq INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "report_id INTEGER NOT NULL, " +
                "uid TEXT NOT NULL, " +
                "photo_orig TEXT, " +
                "photo_final TEXT, " +
                "photo_at INTEGER NOT NULL, " +
                "stamped INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "description TEXT NOT NULL, " +
                "area TEXT NOT NULL, " +
                "quantity TEXT NOT NULL, " +
                "length TEXT NOT NULL, " +
                "width TEXT NOT NULL, " +
                "height TEXT NOT NULL, " +
                "dim_unit TEXT NOT NULL, " +
                "weight TEXT NOT NULL, " +
                "weight_unit TEXT NOT NULL, " +
                "aisle TEXT NOT NULL, " +
                "rack TEXT NOT NULL, " +
                "shelf TEXT NOT NULL, " +
                "condition TEXT NOT NULL, " +
                "condition_at INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX items_report ON items(report_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

    // ---------- reports ----------

    fun reports(): List<Report> {
        val out = ArrayList<Report>()
        readableDatabase.rawQuery(
            "SELECT r.*, (SELECT COUNT(*) FROM items i WHERE i.report_id = r.id) AS cnt " +
                "FROM reports r ORDER BY r.created_at DESC",
            null
        ).use { c ->
            while (c.moveToNext()) out.add(readReport(c))
        }
        return out
    }

    fun report(id: Long): Report? {
        return readableDatabase.rawQuery(
            "SELECT r.*, (SELECT COUNT(*) FROM items i WHERE i.report_id = r.id) AS cnt " +
                "FROM reports r WHERE r.id = ?",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) readReport(c) else null
        }
    }

    fun insertReport(title: String, prefix: String, startSeq: Int): Long {
        val v = ContentValues()
        v.put("title", title)
        v.put("prefix", prefix)
        v.put("created_at", System.currentTimeMillis())
        v.put("next_seq", startSeq)
        return writableDatabase.insertOrThrow("reports", null, v)
    }

    fun updateReport(id: Long, title: String, prefix: String, nextSeq: Int) {
        val v = ContentValues()
        v.put("title", title)
        v.put("prefix", prefix)
        v.put("next_seq", nextSeq)
        writableDatabase.update("reports", v, "id = ?", arrayOf(id.toString()))
    }

    fun deleteReport(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("items", "report_id = ?", arrayOf(id.toString()))
            db.delete("reports", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun readReport(c: Cursor): Report = Report(
        id = c.lng("id"),
        title = c.str("title"),
        prefix = c.str("prefix"),
        createdAt = c.lng("created_at"),
        nextSeq = c.lng("next_seq").toInt(),
        itemCount = c.lng("cnt").toInt()
    )

    // ---------- items ----------

    fun items(reportId: Long): List<Item> {
        val out = ArrayList<Item>()
        readableDatabase.rawQuery(
            "SELECT * FROM items WHERE report_id = ? ORDER BY id ASC",
            arrayOf(reportId.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(readItem(c))
        }
        return out
    }

    fun item(id: Long): Item? {
        return readableDatabase.rawQuery(
            "SELECT * FROM items WHERE id = ?",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) readItem(c) else null
        }
    }

    /** Inserts the item and gives it the next sequential ID of its report. */
    fun insertItem(item: Item): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            var prefix = ""
            var seq = 1
            db.rawQuery(
                "SELECT prefix, next_seq FROM reports WHERE id = ?",
                arrayOf(item.reportId.toString())
            ).use { c ->
                if (c.moveToFirst()) {
                    prefix = c.getString(0) ?: ""
                    seq = c.getInt(1)
                }
            }
            item.uid = formatUid(prefix, seq)
            val id = db.insertOrThrow("items", null, values(item))
            db.execSQL(
                "UPDATE reports SET next_seq = ? WHERE id = ?",
                arrayOf<Any>(seq + 1, item.reportId)
            )
            db.setTransactionSuccessful()
            item.id = id
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun updateItem(item: Item) {
        writableDatabase.update("items", values(item), "id = ?", arrayOf(item.id.toString()))
    }

    fun deleteItem(id: Long) {
        writableDatabase.delete("items", "id = ?", arrayOf(id.toString()))
    }

    private fun values(i: Item): ContentValues {
        val v = ContentValues()
        v.put("report_id", i.reportId)
        v.put("uid", i.uid)
        v.put("photo_orig", i.photoOrig)
        v.put("photo_final", i.photoFinal)
        v.put("photo_at", i.photoAt)
        v.put("stamped", if (i.stamped) 1 else 0)
        v.put("name", i.name)
        v.put("description", i.description)
        v.put("area", i.area)
        v.put("quantity", i.quantity)
        v.put("length", i.length)
        v.put("width", i.width)
        v.put("height", i.height)
        v.put("dim_unit", i.dimUnit)
        v.put("weight", i.weight)
        v.put("weight_unit", i.weightUnit)
        v.put("aisle", i.aisle)
        v.put("rack", i.rack)
        v.put("shelf", i.shelf)
        v.put("condition", i.condition)
        v.put("condition_at", i.conditionAt)
        return v
    }

    private fun readItem(c: Cursor): Item = Item(
        id = c.lng("id"),
        reportId = c.lng("report_id"),
        uid = c.str("uid"),
        photoOrig = c.strOrNull("photo_orig"),
        photoFinal = c.strOrNull("photo_final"),
        photoAt = c.lng("photo_at"),
        stamped = c.lng("stamped") != 0L,
        name = c.str("name"),
        description = c.str("description"),
        area = c.str("area"),
        quantity = c.str("quantity"),
        length = c.str("length"),
        width = c.str("width"),
        height = c.str("height"),
        dimUnit = c.str("dim_unit"),
        weight = c.str("weight"),
        weightUnit = c.str("weight_unit"),
        aisle = c.str("aisle"),
        rack = c.str("rack"),
        shelf = c.str("shelf"),
        condition = c.str("condition"),
        conditionAt = c.lng("condition_at")
    )

    private fun Cursor.str(name: String): String = getString(getColumnIndexOrThrow(name)) ?: ""

    private fun Cursor.strOrNull(name: String): String? {
        val i = getColumnIndexOrThrow(name)
        return if (isNull(i)) null else getString(i)
    }

    private fun Cursor.lng(name: String): Long = getLong(getColumnIndexOrThrow(name))
}
