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
import android.util.Base64
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

    private lateinit var binding: ActivityChatBinding
    private lateinit var messageAdapter: MessageAdapter

    private var recipientId: String = ""
    private var currentUserName: String = "Someone"

    private var activeReplyToMsg: Message? = null
    private var selectedImageBase64: String? = null

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
        uri?.let { processImageUri(it) }
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
            binding.replyingMsgPreviewTxt.text = replyMsg.text.ifEmpty { "📷 Photo" }
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

        binding.chatGalleryBtn.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.chatCameraBtn.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.chatSendBtn.setOnClickListener {
            sendMessage()
        }

        // Like icon while the input is empty (as in the design), send icon once there is text.
        binding.chatMessageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.chatSendBtn.setImageResource(
                    if (s.isNullOrBlank()) R.drawable.k_ic_like else R.drawable.k_ic_send
                )
            }
        })
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

    private fun processImageUri(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                val scaledBitmap = scaleBitmap(originalBitmap, 800)
                val outputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
                val byteArray = outputStream.toByteArray()
                selectedImageBase64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                Toast.makeText(this, "Photo attached. Tap send to deliver.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
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
        val text = binding.chatMessageInput.text.toString().trim()
        val imageBase64 = selectedImageBase64
        if (text.isEmpty() && imageBase64.isNullOrEmpty()) {
            return
        }

        // The server stores the message and sends the push notification
        // to the recipient.
        val body = mapOf(
            "recipient_id" to recipientId,
            "text" to text,
            "image" to imageBase64,
            "replyToMsgId" to activeReplyToMsg?.messageId,
            "clientId" to newClientId()
        )
        val replyText = activeReplyToMsg?.let { it.text.ifEmpty { "📷 Photo" } }.orEmpty()

        binding.chatMessageInput.setText("")
        selectedImageBase64 = null
        resetReplyState()

        Api.post("messages/send.php", body, queueIfOffline = true) { result ->
            if (isFinishing || isDestroyed) return@post
            when {
                result.ok -> messagesQuery?.refresh()
                result.queued -> {
                    // Saved in pending_actions; show it now from SQLite with a "Sending..." label.
                    LocalDb.get().addPendingMessage(
                        result.clientId, recipientId, text.ifEmpty { "📷 Photo" }, replyText
                    )
                    messagesQuery?.showLocal()
                    Toast.makeText(this, "You're offline. The message will be sent when you reconnect.", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // Give the text back so it is not lost.
                    binding.chatMessageInput.setText(text)
                    selectedImageBase64 = imageBase64
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
