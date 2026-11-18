package com.ahmadtalal.i210734_i210819

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * On-device SQLite database that keeps the app usable without a connection.
 *
 * Content tables (filled from the web services, read first by every screen):
 *   users, friends, posts, stories, messages
 * Each row keeps the item's JSON exactly as the server sent it, plus the
 * columns needed to find and order it.
 *
 * pending_actions: every write made while offline (send/edit/delete message,
 *   create post, react, comment, friend request ...). Each has a client-made
 *   UUID; they are replayed in order when the connection returns and the
 *   server ignores any UUID it has already handled, so nothing is lost or
 *   duplicated. Bodies can hold photos, so they are stored as files and the
 *   table keeps the file name.
 *
 * api_cache: last response of the remaining read services (comments, chat
 *   list, profiles), so those screens also open offline.
 */
class LocalDb private constructor(context: Context) :
    SQLiteOpenHelper(context, "kinnect_local.db", null, 2) {

    data class PendingAction(val id: Long, val clientId: String, val endpoint: String, val body: String)

    private val pendingDir = File(context.filesDir, "pending_actions").apply { mkdirs() }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE users (user_id TEXT PRIMARY KEY, json TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE friends (friend_id TEXT PRIMARY KEY)")
        db.execSQL(
            """CREATE TABLE posts (
                   post_id   TEXT PRIMARY KEY,
                   timestamp INTEGER NOT NULL,
                   json      TEXT NOT NULL,
                   pending   INTEGER NOT NULL DEFAULT 0
               )"""
        )
        db.execSQL(
            """CREATE TABLE stories (
                   story_id  TEXT PRIMARY KEY,
                   user_id   TEXT NOT NULL,
                   timestamp INTEGER NOT NULL,
                   json      TEXT NOT NULL
               )"""
        )
        db.execSQL(
            """CREATE TABLE messages (
                   message_id TEXT PRIMARY KEY,
                   peer_id    TEXT NOT NULL,
                   timestamp  INTEGER NOT NULL,
                   json       TEXT NOT NULL,
                   pending    INTEGER NOT NULL DEFAULT 0
               )"""
        )
        db.execSQL("CREATE INDEX idx_messages_peer ON messages (peer_id, timestamp)")
        db.execSQL(
            """CREATE TABLE pending_actions (
                   id         INTEGER PRIMARY KEY AUTOINCREMENT,
                   client_id  TEXT NOT NULL UNIQUE,
                   endpoint   TEXT NOT NULL,
                   body_file  TEXT NOT NULL,
                   created_at INTEGER NOT NULL
               )"""
        )
        db.execSQL(
            """CREATE TABLE api_cache (
                   cache_key  TEXT PRIMARY KEY,
                   response   TEXT NOT NULL,
                   updated_at INTEGER NOT NULL
               )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        for (table in listOf("users", "friends", "posts", "stories", "messages", "pending_actions", "pending_requests", "api_cache")) {
            db.execSQL("DROP TABLE IF EXISTS $table")
        }
        onCreate(db)
    }

    // ---------- saving what the server sent ----------

    /**
     * Store a successful response of a read web service. The feed, stories,
     * friends and conversations go into their own tables; everything else
     * into api_cache. Users found inside any response refresh the users table.
     */
    fun saveResponse(endpoint: String, params: Map<String, Any?>, cacheKey: String, raw: String, envelope: JSONObject) {
        val db = writableDatabase
        val items = envelope.optJSONArray("data")
        db.beginTransaction()
        try {
            when (endpoint) {
                "posts/feed.php" -> {
                    db.delete("posts", "pending = 0", null)
                    forEach(items) { post ->
                        putRow(db, "posts", "post_id", post.optString("postId"), post.optLong("timestamp"), post)
                        post.optJSONObject("author")?.let { putUser(db, it) }
                    }
                }
                "stories/feed.php" -> {
                    db.delete("stories", null, null)
                    forEach(items) { story ->
                        val values = ContentValues().apply {
                            put("story_id", story.optString("storyId"))
                            put("user_id", story.optString("userId"))
                            put("timestamp", story.optLong("timestamp"))
                            put("json", story.toString())
                        }
                        db.insertWithOnConflict("stories", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                        story.optJSONObject("author")?.let { putUser(db, it) }
                    }
                }
                "messages/list.php" -> {
                    val peerId = params["recipient_id"].toString()
                    db.delete("messages", "peer_id = ? AND pending = 0", arrayOf(peerId))
                    forEach(items) { message ->
                        putRow(db, "messages", "message_id", message.optString("messageId"), message.optLong("timestamp"), message, peerId)
                    }
                }
                "friends/list.php" -> {
                    if (params["user_id"] == null) {
                        db.delete("friends", null, null)
                        forEach(items) { friend ->
                            db.insertWithOnConflict("friends", null,
                                ContentValues().apply { put("friend_id", friend.optString("userId")) },
                                SQLiteDatabase.CONFLICT_REPLACE)
                            putUser(db, friend)
                        }
                    } else {
                        putCache(db, cacheKey, raw)
                    }
                }
                else -> {
                    putCache(db, cacheKey, raw)
                    // Profiles, search results, friend requests and chat rows all carry users.
                    envelope.optJSONObject("data")?.let { if (it.has("userId")) putUser(db, it) }
                    forEach(items) { item ->
                        if (item.has("userId") && item.has("username")) putUser(db, item)
                        item.optJSONObject("user")?.let { putUser(db, it) }
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * The locally stored answer for a read web service, in the same JSON
     * shape the server uses, or null when nothing has been saved yet.
     */
    fun loadResponse(endpoint: String, params: Map<String, Any?>, cacheKey: String): String? {
        val db = readableDatabase
        val rows: JSONArray = when (endpoint) {
            "posts/feed.php" ->
                queryJson(db, "SELECT json FROM posts ORDER BY pending DESC, timestamp DESC") ?: return null
            "stories/feed.php" -> {
                // Stories live for 24 hours; expired ones are removed from the phone too.
                writableDatabase.delete("stories", "timestamp < ?",
                    arrayOf((System.currentTimeMillis() - STORY_LIFETIME_MS).toString()))
                queryJson(db, "SELECT json FROM stories ORDER BY timestamp DESC") ?: return null
            }
            "messages/list.php" ->
                queryJson(db, "SELECT json FROM messages WHERE peer_id = ? ORDER BY pending, timestamp",
                    arrayOf(params["recipient_id"].toString())) ?: return null
            "friends/list.php" ->
                if (params["user_id"] == null) {
                    queryJson(db, "SELECT u.json FROM friends f JOIN users u ON u.user_id = f.friend_id") ?: return null
                } else {
                    return getCache(cacheKey)
                }
            else -> return getCache(cacheKey)
        }
        return JSONObject().put("ok", true).put("data", rows).toString()
    }

    fun getUser(userId: String): JSONObject? {
        readableDatabase.rawQuery("SELECT json FROM users WHERE user_id = ?", arrayOf(userId)).use { cursor ->
            return if (cursor.moveToFirst()) JSONObject(cursor.getString(0)) else null
        }
    }

    // ---------- showing offline changes straight away ----------

    /** A message typed offline: shown in the chat with a "Sending..." label until it syncs. */
    fun addPendingMessage(clientId: String, peerId: String, text: String, replyToText: String) {
        val now = System.currentTimeMillis()
        val message = JSONObject()
            .put("messageId", PENDING_PREFIX + clientId)
            .put("senderId", Session.userId)
            .put("text", text)
            .put("timestamp", now)
            .put("replyToText", replyToText)
            .put("pending", true)
        putRow(writableDatabase, "messages", "message_id", PENDING_PREFIX + clientId, now, message, peerId, pending = true)
    }

    /** A post written offline: shown at the top of the feed marked as pending. */
    fun addPendingPost(clientId: String, text: String) {
        val now = System.currentTimeMillis()
        val post = JSONObject()
            .put("postId", PENDING_PREFIX + clientId)
            .put("authorId", Session.userId)
            .put("text", text)
            .put("timestamp", now)
            .put("images", JSONArray())
            .put("pending", true)
        getUser(Session.userId)?.let { post.put("author", it) }
        putRow(writableDatabase, "posts", "post_id", PENDING_PREFIX + clientId, now, post, pending = true)
    }

    /** Apply an offline edit to a stored message or post (e.g. new text, a reaction). */
    fun patchMessage(messageId: String, change: (JSONObject) -> Unit) = patchRow("messages", "message_id", messageId, change)

    fun patchPost(postId: String, change: (JSONObject) -> Unit) = patchRow("posts", "post_id", postId, change)

    fun patchUser(userId: String, change: (JSONObject) -> Unit) = patchRow("users", "user_id", userId, change)

    private fun patchRow(table: String, idColumn: String, id: String, change: (JSONObject) -> Unit) {
        val db = writableDatabase
        db.rawQuery("SELECT json FROM $table WHERE $idColumn = ?", arrayOf(id)).use { cursor ->
            if (!cursor.moveToFirst()) return
            val json = JSONObject(cursor.getString(0))
            change(json)
            db.update(table, ContentValues().apply { put("json", json.toString()) }, "$idColumn = ?", arrayOf(id))
        }
    }

    // ---------- pending_actions queue ----------

    fun enqueue(clientId: String, endpoint: String, body: String) {
        val fileName = "$clientId.json"
        File(pendingDir, fileName).writeText(body)
        val values = ContentValues().apply {
            put("client_id", clientId)
            put("endpoint", endpoint)
            put("body_file", fileName)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("pending_actions", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** The oldest queued action, or null when the queue is empty. */
    fun nextPending(): PendingAction? {
        readableDatabase.rawQuery(
            "SELECT id, client_id, endpoint, body_file FROM pending_actions ORDER BY id LIMIT 1", null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val file = File(pendingDir, cursor.getString(3))
            val body = if (file.exists()) file.readText() else "{}"
            return PendingAction(cursor.getLong(0), cursor.getString(1), cursor.getString(2), body)
        }
    }

    /** The server has the action now: drop it and the placeholder row that stood in for it. */
    fun completePending(action: PendingAction) {
        val db = writableDatabase
        File(pendingDir, "${action.clientId}.json").delete()
        db.delete("pending_actions", "id = ?", arrayOf(action.id.toString()))
        db.delete("messages", "message_id = ?", arrayOf(PENDING_PREFIX + action.clientId))
        db.delete("posts", "post_id = ?", arrayOf(PENDING_PREFIX + action.clientId))
    }

    fun pendingCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM pending_actions", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /** Called on logout so one account never sees another account's data. */
    fun clearAll() {
        val db = writableDatabase
        for (table in listOf("users", "friends", "posts", "stories", "messages", "pending_actions", "api_cache")) {
            db.delete(table, null, null)
        }
        pendingDir.listFiles()?.forEach { it.delete() }
    }

    // ---------- helpers ----------

    private fun putRow(
        db: SQLiteDatabase, table: String, idColumn: String, id: String, timestamp: Long,
        json: JSONObject, peerId: String? = null, pending: Boolean = false
    ) {
        if (id.isEmpty()) return
        val values = ContentValues().apply {
            put(idColumn, id)
            put("timestamp", timestamp)
            put("json", json.toString())
            put("pending", if (pending) 1 else 0)
            if (peerId != null) put("peer_id", peerId)
        }
        db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun putUser(db: SQLiteDatabase, user: JSONObject) {
        val userId = user.optString("userId")
        if (userId.isEmpty()) return
        // Some responses carry only part of a user (a post's author has no
        // friendship info), so new fields are laid over what is already stored.
        val merged = db.rawQuery("SELECT json FROM users WHERE user_id = ?", arrayOf(userId)).use { cursor ->
            if (cursor.moveToFirst()) JSONObject(cursor.getString(0)) else JSONObject()
        }
        for (key in user.keys()) merged.put(key, user.get(key))
        val values = ContentValues().apply {
            put("user_id", userId)
            put("json", merged.toString())
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("users", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun putCache(db: SQLiteDatabase, key: String, response: String) {
        val values = ContentValues().apply {
            put("cache_key", key)
            put("response", response)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("api_cache", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun getCache(key: String): String? {
        readableDatabase.rawQuery("SELECT response FROM api_cache WHERE cache_key = ?", arrayOf(key)).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    /** Rows of a one-column JSON query as an array, or null when there are none. */
    private fun queryJson(db: SQLiteDatabase, sql: String, args: Array<String>? = null): JSONArray? {
        val result = JSONArray()
        db.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) result.put(JSONObject(cursor.getString(0)))
        }
        return if (result.length() == 0) null else result
    }

    private inline fun forEach(array: JSONArray?, action: (JSONObject) -> Unit) {
        if (array == null) return
        for (i in 0 until array.length()) array.optJSONObject(i)?.let(action)
    }

    companion object {
        /** Ids of rows that exist only on this phone until their action syncs. */
        const val PENDING_PREFIX = "pending-"
        private const val STORY_LIFETIME_MS = 24L * 60 * 60 * 1000

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
