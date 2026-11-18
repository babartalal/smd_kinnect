package com.ahmadtalal.i210734_i210819

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.util.UUID

/**
 * On-device SQLite database that keeps the app usable without a connection.
 *
 *  - api_cache: the last successful response of every read web service, so
 *    feeds, chats and profiles can still be shown while offline.
 *  - pending_requests: writes (messages, posts, comments, reactions) made
 *    while offline, replayed in order once the server is reachable again.
 *
 * Request bodies can contain Base64 photos that are too large for a SQLite
 * cursor row, so each pending body is stored as a file and the table keeps
 * only its name.
 */
class LocalDb private constructor(context: Context) :
    SQLiteOpenHelper(context, "kinnect_local.db", null, 1) {

    data class PendingRequest(val id: Long, val endpoint: String, val body: String)

    private val pendingDir = File(context.filesDir, "pending_requests").apply { mkdirs() }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE api_cache (
                   cache_key  TEXT PRIMARY KEY,
                   response   TEXT NOT NULL,
                   updated_at INTEGER NOT NULL
               )"""
        )
        db.execSQL(
            """CREATE TABLE pending_requests (
                   id         INTEGER PRIMARY KEY AUTOINCREMENT,
                   endpoint   TEXT NOT NULL,
                   body_file  TEXT NOT NULL,
                   created_at INTEGER NOT NULL
               )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS api_cache")
        db.execSQL("DROP TABLE IF EXISTS pending_requests")
        onCreate(db)
    }

    // ---------- response cache ----------

    fun putCache(key: String, response: String) {
        val values = ContentValues().apply {
            put("cache_key", key)
            put("response", response)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("api_cache", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getCache(key: String): String? {
        readableDatabase.rawQuery("SELECT response FROM api_cache WHERE cache_key = ?", arrayOf(key)).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    // ---------- offline write queue ----------

    fun enqueue(endpoint: String, body: String) {
        val fileName = UUID.randomUUID().toString() + ".json"
        File(pendingDir, fileName).writeText(body)
        val values = ContentValues().apply {
            put("endpoint", endpoint)
            put("body_file", fileName)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("pending_requests", null, values)
    }

    /** The oldest queued request, or null when the queue is empty. */
    fun nextPending(): PendingRequest? {
        readableDatabase.rawQuery(
            "SELECT id, endpoint, body_file FROM pending_requests ORDER BY id LIMIT 1", null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val file = File(pendingDir, cursor.getString(2))
            val body = if (file.exists()) file.readText() else "{}"
            return PendingRequest(cursor.getLong(0), cursor.getString(1), body)
        }
    }

    fun deletePending(id: Long) {
        readableDatabase.rawQuery("SELECT body_file FROM pending_requests WHERE id = ?", arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToFirst()) File(pendingDir, cursor.getString(0)).delete()
        }
        writableDatabase.delete("pending_requests", "id = ?", arrayOf(id.toString()))
    }

    fun pendingCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM pending_requests", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /** Called on logout so one account never sees another account's data. */
    fun clearAll() {
        writableDatabase.delete("api_cache", null, null)
        writableDatabase.delete("pending_requests", null, null)
        pendingDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        @Volatile
        private var instance: LocalDb? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) instance = LocalDb(context.applicationContext)
                }
            }
        }

        fun get(): LocalDb = instance ?: error("LocalDb.init() must be called from the Application first")
    }
}
