package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Outcome of one web service call, always delivered on the main thread. */
class ApiResult(
    val ok: Boolean,
    private val envelope: JSONObject? = null,
    val error: String = "",
    /** The server was unreachable and this is the copy saved in SQLite. */
    val fromCache: Boolean = false,
    /** The server was unreachable; the write is saved and will be sent later. */
    val queued: Boolean = false,
    /** The data is the same as the etag the caller already has. */
    val notModified: Boolean = false
) {
    val etag: String get() = envelope?.optString("etag").orEmpty()
    fun obj(): JSONObject = envelope?.optJSONObject("data") ?: JSONObject()
    fun arr(): JSONArray = envelope?.optJSONArray("data") ?: JSONArray()
    val hasData: Boolean get() = envelope != null && !envelope.isNull("data")
}

/**
 * Client for the Kinnect PHP web services (see backend/kinnect_api).
 * Replaces FirebaseDatabase: reads go through [get], writes through [post].
 */
object Api {

    /** Set API_BASE_URL in local.properties; the default reaches XAMPP from the emulator. */
    val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/') + "/"

    private const val OFFLINE_MESSAGE = "Can't reach the server. Check your connection."
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val executor = Executors.newFixedThreadPool(4)
    private val syncExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val syncing = AtomicBoolean(false)
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Full URL for an image path returned by the server ("uploads/abc.jpg"). */
    fun imageUrl(path: String): String =
        if (path.isEmpty() || path.startsWith("http")) path else baseUrl + path

    /**
     * Read data. A successful response is saved in SQLite; if the server
     * cannot be reached, the saved copy is returned with fromCache = true.
     */
    fun get(
        endpoint: String,
        params: Map<String, Any?> = emptyMap(),
        etag: String = "",
        callback: (ApiResult) -> Unit
    ) {
        val userId = Session.userId
        val cacheKey = userId + "|" + endpoint + "?" +
            params.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }

        executor.execute {
            val result = try {
                val url = (baseUrl + endpoint).toHttpUrl().newBuilder().apply {
                    params.forEach { (key, value) -> if (value != null) addQueryParameter(key, value.toString()) }
                    if (etag.isNotEmpty()) addQueryParameter("etag", etag)
                }.build()
                val response = execute(Request.Builder().url(url).get())
                if (response.ok && !response.notModified) {
                    LocalDb.get().putCache(cacheKey, response.raw)
                }
                response.toResult()
            } catch (e: IOException) {
                Log.w("KinnectApi", "$endpoint unreachable: $e")
                val cached = LocalDb.get().getCache(cacheKey)
                if (cached != null) {
                    ApiResult(ok = true, envelope = JSONObject(cached), fromCache = true)
                } else {
                    ApiResult(ok = false, error = OFFLINE_MESSAGE)
                }
            } catch (e: Exception) {
                ApiResult(ok = false, error = e.message ?: "Unexpected error")
            }
            deliver(result, callback)
        }
    }

    /**
     * Write data. With [queueIfOffline], a call that cannot reach the server
     * is stored in SQLite and re-sent automatically later; the callback then
     * receives queued = true.
     */
    fun post(
        endpoint: String,
        body: Map<String, Any?> = emptyMap(),
        queueIfOffline: Boolean = false,
        callback: (ApiResult) -> Unit = {}
    ) {
        val json = JSONObject(body.filterValues { it != null }).toString()
        executor.execute {
            val result = try {
                postNow(endpoint, json).toResult()
            } catch (e: IOException) {
                Log.w("KinnectApi", "$endpoint unreachable: $e")
                if (queueIfOffline && Session.isLoggedIn) {
                    LocalDb.get().enqueue(endpoint, json)
                    ApiResult(ok = false, queued = true, error = "You're offline. This will be sent when you're back online.")
                } else {
                    ApiResult(ok = false, error = OFFLINE_MESSAGE)
                }
            } catch (e: Exception) {
                ApiResult(ok = false, error = e.message ?: "Unexpected error")
            }
            deliver(result, callback)
        }
    }

    /** Re-send writes that were queued while offline, oldest first. */
    fun syncPending() {
        if (!Session.isLoggedIn || !syncing.compareAndSet(false, true)) return
        syncExecutor.execute {
            try {
                val db = LocalDb.get()
                while (Session.isLoggedIn) {
                    val pending = db.nextPending() ?: break
                    try {
                        // Whatever the server answers, it received the request: drop it.
                        postNow(pending.endpoint, pending.body)
                        db.deletePending(pending.id)
                    } catch (e: IOException) {
                        break // still offline; try again on the next sync
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                syncing.set(false)
            }
        }
    }

    // ---------- internals ----------

    private class RawResponse(val code: Int, val raw: String) {
        val json: JSONObject? = try { JSONObject(raw) } catch (e: Exception) { null }
        val ok: Boolean get() = code in 200..299 && json?.optBoolean("ok") == true
        val notModified: Boolean get() = json?.optBoolean("notModified") == true

        fun toResult(): ApiResult = when {
            ok -> ApiResult(ok = true, envelope = json, notModified = notModified)
            else -> ApiResult(ok = false, error = json?.optString("error").orEmpty().ifEmpty { "Server error ($code)" })
        }
    }

    private fun postNow(endpoint: String, json: String): RawResponse =
        execute(Request.Builder().url(baseUrl + endpoint).post(json.toRequestBody(JSON_TYPE)))

    private fun execute(builder: Request.Builder): RawResponse {
        val hadSession = Session.isLoggedIn
        val request = builder.header("X-Auth-Token", Session.token).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code == 401 && hadSession) {
                mainHandler.post { onSessionExpired() }
            }
            return RawResponse(response.code, raw)
        }
    }

    private fun deliver(result: ApiResult, callback: (ApiResult) -> Unit) {
        if (result.ok && !result.fromCache && LocalDb.get().pendingCount() > 0) {
            syncPending()
        }
        mainHandler.post { callback(result) }
    }

    /** The server no longer accepts our token: sign out and return to the login screen. */
    private fun onSessionExpired() {
        if (!Session.isLoggedIn) return
        Session.clear()
        val intent = Intent(appContext, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        appContext.startActivity(intent)
    }
}

/**
 * Keeps a screen up to date by polling a read web service. This stands in
 * for Firebase's addValueEventListener: [onData] runs when the data first
 * loads and again whenever it changes on the server. Unchanged polls are
 * answered with a tiny "not modified" reply thanks to the etag.
 *
 * Call [start] in onStart and [stop] in onStop.
 */
class LiveQuery(
    private val endpoint: String,
    private val params: Map<String, Any?> = emptyMap(),
    private val intervalMs: Long = 4000L,
    private val onData: (ApiResult) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable { fetch() }
    private var running = false
    private var inFlight = false
    private var refreshRequested = false
    private var delivered = false
    private var etag = ""

    fun start() {
        if (running) return
        running = true
        fetch()
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    /** Fetch again right now, for example straight after a write. */
    fun refresh() {
        if (!running) return
        if (inFlight) {
            refreshRequested = true
        } else {
            handler.removeCallbacks(tick)
            fetch()
        }
    }

    private fun fetch() {
        if (inFlight) return
        inFlight = true
        Api.get(endpoint, params, etag) { result ->
            inFlight = false
            if (!running) return@get
            if (result.ok && !result.notModified) {
                if (!result.fromCache) {
                    etag = result.etag
                    delivered = true
                    onData(result)
                } else if (!delivered) {
                    // Offline: show the saved copy once, then keep trying the server.
                    delivered = true
                    onData(result)
                }
            }
            handler.removeCallbacks(tick)
            if (refreshRequested) {
                refreshRequested = false
                fetch()
            } else {
                handler.postDelayed(tick, intervalMs)
            }
        }
    }
}
