package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Outcome of one web service call, always delivered on the main thread. */
class ApiResult(
    val ok: Boolean,
    private val envelope: JSONObject? = null,
    val error: String = "",
    /** This is the copy stored in SQLite, not a fresh answer from the server. */
    val fromCache: Boolean = false,
    /** The server was unreachable; the write is in pending_actions and will be sent later. */
    val queued: Boolean = false,
    /** The data is the same as the etag the caller already has. */
    val notModified: Boolean = false,
    /** The UUID sent with a write; the server uses it to ignore repeats. */
    val clientId: String = "",
    /** An upload could not reach the server at all (no connection), as opposed to being refused. */
    val unreachable: Boolean = false
) {
    val etag: String get() = envelope?.optString("etag").orEmpty()
    fun obj(): JSONObject = envelope?.optJSONObject("data") ?: JSONObject()
    fun arr(): JSONArray = envelope?.optJSONArray("data") ?: JSONArray()
    val hasData: Boolean get() = envelope != null && !envelope.isNull("data")
}

/** One file of a multipart upload: the name and type sent to the server, and the content. */
class UploadPart private constructor(
    val fileName: String,
    val mimeType: String,
    internal val body: RequestBody
) {
    companion object {
        private fun type(mimeType: String) =
            mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()

        /** Content already in memory, e.g. a JPEG-compressed photo. */
        fun bytes(fileName: String, mimeType: String, bytes: ByteArray): UploadPart =
            UploadPart(fileName, mimeType, bytes.toRequestBody(type(mimeType)))

        /** Content streamed from a file, e.g. a video copied into the cache folder. */
        fun file(fileName: String, mimeType: String, file: File): UploadPart =
            UploadPart(fileName, mimeType, file.asRequestBody(type(mimeType)))
    }
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

    /** Full URL for an image, video or file path returned by the server ("uploads/abc.jpg"). */
    fun imageUrl(path: String): String =
        if (path.isEmpty() || path.startsWith("http")) path else baseUrl + path

    private fun cacheKey(endpoint: String, params: Map<String, Any?>): String =
        Session.userId + "|" + endpoint + "?" +
            params.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }

    /**
     * What SQLite already holds for a read web service, or null. Screens show
     * this first, then refresh from the server.
     */
    fun local(endpoint: String, params: Map<String, Any?> = emptyMap()): ApiResult? {
        val stored = try {
            LocalDb.get().loadResponse(endpoint, params, cacheKey(endpoint, params))
        } catch (e: Exception) {
            null
        } ?: return null
        return ApiResult(ok = true, envelope = JSONObject(stored), fromCache = true)
    }

    /**
     * Read data from the server. A successful answer is stored in SQLite; if
     * the server cannot be reached, the stored copy is returned instead with
     * fromCache = true.
     */
    fun get(
        endpoint: String,
        params: Map<String, Any?> = emptyMap(),
        etag: String = "",
        callback: (ApiResult) -> Unit
    ) {
        val key = cacheKey(endpoint, params)

        executor.execute {
            val result = try {
                val url = (baseUrl + endpoint).toHttpUrl().newBuilder().apply {
                    params.forEach { (name, value) -> if (value != null) addQueryParameter(name, value.toString()) }
                    if (etag.isNotEmpty()) addQueryParameter("etag", etag)
                }.build()
                val response = execute(Request.Builder().url(url).get())
                if (response.ok && !response.notModified && response.json != null) {
                    LocalDb.get().saveResponse(endpoint, params, key, response.raw, response.json)
                }
                response.toResult()
            } catch (e: IOException) {
                Log.w("KinnectApi", "$endpoint unreachable: $e")
                local(endpoint, params) ?: ApiResult(ok = false, error = OFFLINE_MESSAGE)
            } catch (e: Exception) {
                ApiResult(ok = false, error = e.message ?: "Unexpected error")
            }
            deliver(result, callback)
        }
    }

    /**
     * Write data. Every write carries a clientId (UUID). With [queueIfOffline],
     * a call that cannot reach the server is saved in pending_actions and sent
     * automatically later; the callback then receives queued = true.
     */
    fun post(
        endpoint: String,
        body: Map<String, Any?> = emptyMap(),
        queueIfOffline: Boolean = false,
        callback: (ApiResult) -> Unit = {}
    ) {
        val clientId = (body["clientId"] as? String).orEmpty().ifEmpty { UUID.randomUUID().toString() }
        val json = JSONObject(body.filterValues { it != null }).put("clientId", clientId).toString()

        executor.execute {
            val result = try {
                postNow(endpoint, json).toResult(clientId)
            } catch (e: IOException) {
                Log.w("KinnectApi", "$endpoint unreachable: $e")
                if (queueIfOffline && Session.isLoggedIn) {
                    LocalDb.get().enqueue(clientId, endpoint, json)
                    ApiResult(
                        ok = false, queued = true, clientId = clientId,
                        error = "You're offline. This will be sent when you're back online."
                    )
                } else {
                    ApiResult(ok = false, error = OFFLINE_MESSAGE, clientId = clientId)
                }
            } catch (e: Exception) {
                ApiResult(ok = false, error = e.message ?: "Unexpected error", clientId = clientId)
            }
            deliver(result, callback)
        }
    }

    /**
     * Background sync: send the queued offline actions, oldest first. Runs
     * when the network comes back, on the heartbeat, and after any successful
     * call. An action leaves the queue only once the server has answered it;
     * repeats are harmless because the server remembers each clientId.
     */
    fun syncPending() {
        if (!Session.isLoggedIn || !syncing.compareAndSet(false, true)) return
        syncExecutor.execute {
            try {
                val db = LocalDb.get()
                while (Session.isLoggedIn) {
                    val action = db.nextPending() ?: break
                    val response = try {
                        postNow(action.endpoint, action.body)
                    } catch (e: IOException) {
                        break // still offline; try again on the next sync
                    }
                    if (response.code >= 500) break // server trouble: keep it and retry later
                    db.completePending(action)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                syncing.set(false)
            }
        }
    }

    // ---------- multipart uploads ----------

    /** media/upload.php refuses anything larger; checked here first to save the upload. */
    const val MAX_UPLOAD_BYTES = 25L * 1024 * 1024

    /**
     * Send a multipart/form-data request: plain [fields] plus [files], each a
     * pair of form field name ("file", "images[]") and content. Like [post] it
     * carries the X-Auth-Token header and a clientId, runs on the background
     * executor and answers on the main thread. Uploads are never queued: when
     * the server cannot be reached the result has unreachable = true and the
     * caller decides what to do (fall back to a queued JSON write, or tell
     * the user that attachments need a connection).
     */
    fun postMultipart(
        endpoint: String,
        fields: Map<String, String?> = emptyMap(),
        files: List<Pair<String, UploadPart>> = emptyList(),
        callback: (ApiResult) -> Unit
    ) {
        val clientId = fields["clientId"].orEmpty().ifEmpty { UUID.randomUUID().toString() }
        executor.execute {
            deliver(multipartNow(endpoint, fields, files, clientId), callback)
        }
    }

    /**
     * Upload one file to media/upload.php. On success the result's obj() holds
     * {path, mediaType, fileName, size}; pass "path" as `media` to
     * messages/send.php or stories/create.php.
     */
    fun upload(part: UploadPart, callback: (ApiResult) -> Unit) {
        postMultipart("media/upload.php", emptyMap(), listOf("file" to part), callback)
    }

    /**
     * Upload something the user picked (a content:// uri) to media/upload.php.
     * The content is read through the ContentResolver and copied to a file in
     * the cache folder first, so no storage permission is needed; the copy is
     * deleted afterwards.
     */
    fun uploadUri(uri: Uri, callback: (ApiResult) -> Unit) {
        val clientId = UUID.randomUUID().toString()
        executor.execute {
            var copy: File? = null
            val result = try {
                val name = displayName(uri)
                val type = mimeTypeOf(uri)
                val file = copyToCache(uri)
                copy = file
                when {
                    file == null -> ApiResult(ok = false, error = "Could not read the selected file")
                    file.length() == 0L -> ApiResult(ok = false, error = "The selected file is empty")
                    file.length() > MAX_UPLOAD_BYTES ->
                        ApiResult(ok = false, error = "This file is too large. The limit is 25 MB.")
                    else -> multipartNow(
                        "media/upload.php", emptyMap(),
                        listOf("file" to UploadPart.file(withExtension(name, type), type, file)), clientId
                    )
                }
            } catch (e: Exception) {
                ApiResult(ok = false, error = "Could not read the selected file")
            } finally {
                copy?.delete()
            }
            deliver(result, callback)
        }
    }

    /** The name the user knows a picked file by ("Report.pdf"). */
    fun displayName(uri: Uri): String {
        val fromProvider = try {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
        return fromProvider.orEmpty()
            .ifEmpty { uri.lastPathSegment.orEmpty().substringAfterLast('/') }
            .ifEmpty { "file" }
    }

    /** MIME type of a picked uri, from its provider or else from its file name. */
    fun mimeTypeOf(uri: Uri): String {
        appContext.contentResolver.getType(uri)?.let { return it }
        val extension = displayName(uri).substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    /** image, video or file: how the app treats content of this MIME type. */
    fun mediaTypeOf(mimeType: String): String = when {
        mimeType.startsWith("image/") -> "image"
        mimeType.startsWith("video/") -> "video"
        else -> "file"
    }

    /** Gallery items are often named without an extension; the server needs one to recognise the type. */
    private fun withExtension(fileName: String, mimeType: String): String {
        if (fileName.substringAfterLast('.', "").length in 2..5) return fileName
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: return fileName
        return "$fileName.$extension"
    }

    /** Copy picked content into the cache folder; null when it cannot be opened. */
    private fun copyToCache(uri: Uri): File? {
        val input = appContext.contentResolver.openInputStream(uri) ?: return null
        val folder = File(appContext.cacheDir, "uploads").apply { mkdirs() }
        val copy = File(folder, UUID.randomUUID().toString())
        input.use { source ->
            copy.outputStream().use { target ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (total <= MAX_UPLOAD_BYTES) {
                    // Stops early on a file the server would refuse anyway.
                    val read = source.read(buffer)
                    if (read < 0) break
                    target.write(buffer, 0, read)
                    total += read
                }
            }
        }
        return copy
    }

    private fun multipartNow(
        endpoint: String,
        fields: Map<String, String?>,
        files: List<Pair<String, UploadPart>>,
        clientId: String
    ): ApiResult = try {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (name, value) -> if (value != null && name != "clientId") addFormDataPart(name, value) }
            addFormDataPart("clientId", clientId)
            files.forEach { (field, part) -> addFormDataPart(field, part.fileName, part.body) }
        }.build()
        execute(Request.Builder().url(baseUrl + endpoint).post(body)).toResult(clientId)
    } catch (e: IOException) {
        Log.w("KinnectApi", "$endpoint unreachable: $e")
        ApiResult(ok = false, error = OFFLINE_MESSAGE, clientId = clientId, unreachable = true)
    } catch (e: Exception) {
        ApiResult(ok = false, error = e.message ?: "Unexpected error", clientId = clientId)
    }

    // ---------- internals ----------

    private class RawResponse(val code: Int, val raw: String) {
        val json: JSONObject? = try { JSONObject(raw) } catch (e: Exception) { null }
        val ok: Boolean get() = code in 200..299 && json?.optBoolean("ok") == true
        val notModified: Boolean get() = json?.optBoolean("notModified") == true

        fun toResult(clientId: String = ""): ApiResult = when {
            ok -> ApiResult(ok = true, envelope = json, notModified = notModified, clientId = clientId)
            else -> ApiResult(
                ok = false, clientId = clientId,
                error = json?.optString("error").orEmpty().ifEmpty { "Server error ($code)" }
            )
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
 * Keeps a screen up to date. It first shows what SQLite already holds, then
 * polls the web service so the screen follows the server (this stands in for
 * Firebase's addValueEventListener). Unchanged polls are answered with a tiny
 * "not modified" reply thanks to the etag.
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
    private var etag = ""

    fun start() {
        if (running) return
        running = true
        showLocal()
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

    /**
     * Show the SQLite copy now. Used on start, and after an offline action
     * changed the local data (a pending message, an edited message ...).
     */
    fun showLocal() {
        if (!running) return
        Api.local(endpoint, params)?.let(onData)
    }

    private fun fetch() {
        if (inFlight) return
        inFlight = true
        Api.get(endpoint, params, etag) { result ->
            inFlight = false
            if (!running) return@get
            // Offline answers are the same SQLite copy already on screen.
            if (result.ok && !result.notModified && !result.fromCache) {
                etag = result.etag
                onData(result)
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
