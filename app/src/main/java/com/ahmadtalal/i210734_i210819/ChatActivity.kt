package com.ahmadtalal.i210734_i210819

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.ahmadtalal.i210734_i210819.databinding.ActivityChatBinding
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ChatActivity : AppCompatActivity() {

    companion object {
        /** What the plus button offers: the file types media/upload.php accepts. */
        private val ATTACHABLE_TYPES = arrayOf(
            "image/*",
            "video/*",
            "application/pdf",
            "text/plain",
            "application/zip",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
    }

    private lateinit var binding: ActivityChatBinding
    private lateinit var messageAdapter: MessageAdapter

    private var recipientId: String = ""
    private var currentUserName: String = "Someone"

    private var activeReplyToMsg: Message? = null

    /** A photo, video or file the user picked; it is uploaded and sent with the next tap on send. */
    private class Attachment(val uri: Uri, val name: String, val mimeType: String, val mediaType: String)

    private var attachment: Attachment? = null
    private var uploading = false

    private val messageList = mutableListOf<Message>()
    private val currentUserId: String = Session.userId

    private val callWatcher = IncomingCallWatcher(this)
    private var messagesQuery: LiveQuery? = null
    private var recipientQuery: LiveQuery? = null

    private var screenshotObserver: ContentObserver? = null
    private var lastScreenshotTime = 0L

    private val screenCaptureCallback by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ScreenCaptureCallback {
                onScreenshotDetected()
            }
        } else null
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { attach(it) }
    }

    /** Gallery button: a photo or a video. Plus button: any supported file. */
    private val pickDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { attach(it) }
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            registerScreenshotDetection()
        } else {
            Toast.makeText(this, "Storage permission required for screenshot detection", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recipientId = intent.getStringExtra("recipientId").orEmpty()
        if (recipientId.isEmpty()) {
            Toast.makeText(this, "Recipient not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView()
        setupClickListeners()
        loadCurrentUserDetails()
        recipientQuery = createRecipientQuery()
        messagesQuery = createMessagesQuery()
    }

    override fun onStart() {
        super.onStart()
        checkAndRegisterScreenshotDetection()
        recipientQuery?.start()
        messagesQuery?.start()
        callWatcher.start()
    }

    override fun onStop() {
        super.onStop()
        unregisterScreenshotDetection()
        recipientQuery?.stop()
        messagesQuery?.stop()
        callWatcher.stop()
    }

    private fun checkAndRegisterScreenshotDetection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerScreenshotDetection()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
                registerScreenshotDetection()
            } else {
                requestStoragePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                registerScreenshotDetection()
            } else {
                requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(this, messageList, onChanged = { messagesQuery?.showLocal(); messagesQuery?.refresh() }) { replyMsg ->
            activeReplyToMsg = replyMsg
            binding.replyingMsgPreviewTxt.text = replyMsg.preview()
            binding.replyingToMessageBar.visibility = View.VISIBLE
            binding.chatMessageInput.requestFocus()
        }
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding.messagesRecyclerView.layoutManager = layoutManager
        binding.messagesRecyclerView.adapter = messageAdapter
    }

    private fun setupClickListeners() {
        binding.chatBackBtn.setOnClickListener { finish() }

        binding.voiceCallBtn.setOnClickListener {
            startOutboundCall(type = "audio")
        }

        binding.videoCallBtn.setOnClickListener {
            startOutboundCall(type = "video")
        }

        binding.cancelMessageReplyBtn.setOnClickListener {
            resetReplyState()
        }

        // Gallery: a photo or a video from the system picker (no storage permission needed).
        binding.chatGalleryBtn.setOnClickListener {
            pickDocumentLauncher.launch(arrayOf("image/*", "video/*"))
        }

        binding.chatCameraBtn.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Plus: any file the server accepts (documents, archives, photos, videos).
        binding.chatMoreBtn.setOnClickListener {
            pickDocumentLauncher.launch(ATTACHABLE_TYPES)
        }

        binding.cancelAttachmentBtn.setOnClickListener {
            if (!uploading) showAttachment(null)
        }

        binding.chatSendBtn.setOnClickListener {
            sendMessage()
        }

        // Like icon while there is nothing to send (as in the design), send icon otherwise.
        binding.chatMessageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSendIcon()
            }
        })
    }

    private fun updateSendIcon() {
        val nothingToSend = binding.chatMessageInput.text.isNullOrBlank() && attachment == null
        binding.chatSendBtn.setImageResource(if (nothingToSend) R.drawable.k_ic_like else R.drawable.k_ic_send)
    }

    /** Ask the server to ring the other person, then open the call screen. */
    private fun startOutboundCall(type: String) {
        if (recipientId.isEmpty()) return

        Api.post("calls/start.php", mapOf("recipient_id" to recipientId, "type" to type)) { result ->
            if (isFinishing || isDestroyed) return@post
            if (!result.ok) {
                Toast.makeText(this, result.error, Toast.LENGTH_SHORT).show()
                return@post
            }

            val intent = Intent(this, VoiceCallActivity::class.java)
            intent.putExtra("channelName", result.obj().optString("channelName"))
            intent.putExtra("type", type)
            intent.putExtra("isCaller", true)
            intent.putExtra("recipientId", recipientId)
            startActivity(intent)
        }
    }

    private fun resetReplyState() {
        activeReplyToMsg = null
        binding.replyingToMessageBar.visibility = View.GONE
    }

    private fun loadCurrentUserDetails() {
        if (currentUserId.isEmpty()) return

        Api.get("users/get.php") { result ->
            if (!result.ok) return@get
            val user = User.fromJson(result.obj())
            currentUserName = user.firstName.ifEmpty { user.name.ifEmpty { user.username } }
        }
    }

    /** The other person's name and online status, refreshed while the chat is open. */
    private fun createRecipientQuery(): LiveQuery =
        LiveQuery("users/get.php", mapOf("user_id" to recipientId), intervalMs = 6000L) { result ->
            val user = User.fromJson(result.obj())
            val name = user.displayName()
            binding.chatRecipientNameTxt.text = name

            if (user.isOnline && !result.fromCache) {
                binding.chatStatusTxt.text = "Active now"
                binding.chatRecipientOnlineDot.visibility = View.VISIBLE
            } else {
                binding.chatStatusTxt.text = "Offline"
                binding.chatRecipientOnlineDot.visibility = View.GONE
            }

            binding.chatRecipientAvatar.text = initialsOf(name)

            // Large centred contact intro above the messages, and the bubbles' small avatar.
            binding.chatIntroAvatar.text = initialsOf(name)
            binding.chatIntroNameTxt.text = name
            messageAdapter.peerName = name
        }

    /** The conversation, polled every few seconds so new messages appear by themselves. */
    private fun createMessagesQuery(): LiveQuery =
        LiveQuery("messages/list.php", mapOf("recipient_id" to recipientId), intervalMs = 2500L) { result ->
            val messages = result.arr()
            messageList.clear()
            for (i in 0 until messages.length()) {
                messageList.add(Message.fromJson(messages.getJSONObject(i)))
            }
            showMessages()
        }

    private fun showMessages() {
        val visible = messageList.toList()
        messageAdapter.updateData(visible)
        if (visible.isNotEmpty()) {
            binding.messagesRecyclerView.scrollToPosition(visible.size - 1)
            // The list sits under the contact intro inside one scroll view: jump to the newest message.
            binding.chatScroll.post {
                binding.chatScroll.scrollTo(0, binding.chatScrollContent.height)
            }
        }
    }

    /** Remember what the user picked and show it in the bar above the input. */
    private fun attach(uri: Uri) {
        if (uploading) return
        val mimeType = Api.mimeTypeOf(uri)
        showAttachment(Attachment(uri, Api.displayName(uri), mimeType, Api.mediaTypeOf(mimeType)))
        val kind = when (attachment?.mediaType) {
            "image" -> "Photo"
            "video" -> "Video"
            else -> "File"
        }
        Toast.makeText(this, "$kind attached. Tap send to deliver.", Toast.LENGTH_SHORT).show()
    }

    /** Show (or with null, clear) the attachment waiting to be sent. */
    private fun showAttachment(picked: Attachment?) {
        attachment = picked
        binding.attachmentBar.visibility = if (picked == null) View.GONE else View.VISIBLE
        if (picked != null) {
            binding.attachmentNameTxt.text = picked.name
            binding.attachmentIcon.setImageResource(
                when (picked.mediaType) {
                    "image" -> R.drawable.k_ic_image
                    "video" -> R.drawable.k_ic_video
                    else -> R.drawable.media_ic_file
                }
            )
        }
        updateSendIcon()
    }

    /**
     * A picked photo as JPEG bytes (max 800px, 50% quality), read through the
     * ContentResolver. Null when it cannot be decoded; it is then uploaded as it is.
     */
    private fun compressImage(uri: Uri): ByteArray? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (originalBitmap == null) return null

            val outputStream = ByteArrayOutputStream()
            scaleBitmap(originalBitmap, 800).compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (ratio > 1) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt()
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun sendMessage() {
        if (uploading) return
        val text = binding.chatMessageInput.text.toString().trim()
        val picked = attachment
        if (text.isEmpty() && picked == null) {
            return
        }
        if (picked == null) {
            deliverMessage(text, null, null)
            return
        }

        // With an attachment: first upload the file to media/upload.php, then
        // send the message that points at the stored file.
        uploading = true
        binding.chatSendBtn.isEnabled = false
        binding.attachmentNameTxt.text = "Uploading ${picked.name}..."

        val onUploaded: (ApiResult) -> Unit = { result ->
            uploading = false
            if (!isFinishing && !isDestroyed) {
                binding.chatSendBtn.isEnabled = true
                if (result.ok) {
                    deliverMessage(text, picked, result.obj())
                } else {
                    // Keep the attachment and the text so the user can try again.
                    showAttachment(picked)
                    val problem = if (result.unreachable) {
                        "You're offline. Attachments need a connection. Reconnect and tap send again."
                    } else {
                        "Upload failed: ${result.error}"
                    }
                    Toast.makeText(this, problem, Toast.LENGTH_LONG).show()
                }
            }
        }

        // Photos are scaled down and sent as JPEG; videos and files go as they are.
        val jpeg = if (picked.mediaType == "image") compressImage(picked.uri) else null
        if (jpeg != null) {
            val name = picked.name.substringBeforeLast('.').ifEmpty { "photo" } + ".jpg"
            Api.upload(UploadPart.bytes(name, "image/jpeg", jpeg), onUploaded)
        } else {
            Api.uploadUri(picked.uri, onUploaded)
        }
    }

    /**
     * Send the message itself. [uploaded] is the answer of media/upload.php
     * ({path, mediaType, fileName}) when [picked] was attached.
     */
    private fun deliverMessage(text: String, picked: Attachment?, uploaded: org.json.JSONObject?) {
        // The server stores the message and sends the push notification
        // to the recipient.
        val body = mapOf(
            "recipient_id" to recipientId,
            "text" to text,
            "media" to uploaded?.optString("path"),
            "mediaType" to uploaded?.optString("mediaType"),
            "fileName" to uploaded?.optString("fileName"),
            "replyToMsgId" to activeReplyToMsg?.messageId,
            "clientId" to newClientId()
        )
        val replyText = activeReplyToMsg?.preview().orEmpty()
        // What stands for the message in the chat while it waits in the offline queue.
        val preview = text.ifEmpty {
            when (uploaded?.optString("mediaType")) {
                "image" -> "📷 Photo"
                "video" -> "🎥 Video"
                else -> "📎 " + uploaded?.optString("fileName").orEmpty()
            }
        }

        binding.chatMessageInput.setText("")
        showAttachment(null)
        resetReplyState()

        // The file is already on the server, so even a message with an attachment
        // can wait in the offline queue if the connection drops at this point.
        Api.post("messages/send.php", body, queueIfOffline = true) { result ->
            if (isFinishing || isDestroyed) return@post
            when {
                result.ok -> messagesQuery?.refresh()
                result.queued -> {
                    // Saved in pending_actions; show it now from SQLite with a "Sending..." label.
                    LocalDb.get().addPendingMessage(result.clientId, recipientId, preview, replyText)
                    messagesQuery?.showLocal()
                    Toast.makeText(this, "You're offline. The message will be sent when you reconnect.", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // Give the text and the attachment back so they are not lost.
                    binding.chatMessageInput.setText(text)
                    if (picked != null && attachment == null) showAttachment(picked)
                    Toast.makeText(this, "Failed to send: ${result.error}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun registerScreenshotDetection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            screenCaptureCallback?.let {
                registerScreenCaptureCallback(mainExecutor, it)
            }
        } else {
            screenshotObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    if (uri != null && uri.toString().contains(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())) {
                        checkMediaForScreenshot(uri)
                    }
                }
            }
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                screenshotObserver!!
            )
        }
    }

    private fun unregisterScreenshotDetection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            screenCaptureCallback?.let {
                unregisterScreenCaptureCallback(it)
            }
        } else {
            screenshotObserver?.let {
                contentResolver.unregisterContentObserver(it)
            }
        }
    }

    private fun checkMediaForScreenshot(uri: Uri) {
        try {
            val cursor = contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED),
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val pathIndex = it.getColumnIndex(MediaStore.Images.Media.DATA)
                    val dateIndex = it.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)

                    if (pathIndex != -1 && dateIndex != -1) {
                        val path = it.getString(pathIndex).orEmpty()
                        val dateAdded = it.getLong(dateIndex)

                        val now = System.currentTimeMillis() / 1000
                        if (path.lowercase().contains("screenshot") && (now - dateAdded) <= 10) {
                            onScreenshotDetected()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onScreenshotDetected() {
        if (recipientId.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastScreenshotTime < 2000L) {
            return
        }
        lastScreenshotTime = now

        // A system message in the chat; the server also pushes a "Screenshot Alert".
        val body = mapOf(
            "recipient_id" to recipientId,
            "text" to "$currentUserName took a screenshot",
            "isSystemMessage" to true,
            "clientId" to newClientId()
        )
        Api.post("messages/send.php", body) { result ->
            if (result.ok) messagesQuery?.refresh()
        }

        Toast.makeText(this, "Screenshot detected", Toast.LENGTH_SHORT).show()
    }
}
