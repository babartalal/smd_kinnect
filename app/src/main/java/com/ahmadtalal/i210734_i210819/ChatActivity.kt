package com.ahmadtalal.i210734_i210819

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var messageAdapter: MessageAdapter

    private var recipientId: String = ""
    private var chatId: String = ""
    private var currentUserName: String = "Someone"

    private var activeReplyToMsg: Message? = null
    private var selectedImageBase64: String? = null
    private var incomingCallDialog: AlertDialog? = null

    private val messageList = mutableListOf<Message>()
    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val dbRef = FirebaseDatabase.getInstance().reference

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

        chatId = getChatId(currentUserId, recipientId)

        setupRecyclerView()
        setupClickListeners()
        loadCurrentUserDetails()
        loadRecipientDetails()
        loadMessages()
        listenForIncomingCalls()
    }

    override fun onStart() {
        super.onStart()
        checkAndRegisterScreenshotDetection()
    }

    override fun onStop() {
        super.onStop()
        unregisterScreenshotDetection()
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

    private fun getChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(this, messageList) { replyMsg ->
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
    }

    private fun startOutboundCall(type: String) {
        if (recipientId.isEmpty()) return

        val callMap = hashMapOf<String, Any>(
            "callerId" to currentUserId,
            "callerName" to currentUserName,
            "channelName" to chatId,
            "type" to type,
            "status" to "ringing"
        )

        dbRef.child("calls").child(recipientId).setValue(callMap)

        val intent = Intent(this, VoiceCallActivity::class.java)
        intent.putExtra("channelName", chatId)
        intent.putExtra("type", type)
        intent.putExtra("isCaller", true)
        intent.putExtra("recipientId", recipientId)
        startActivity(intent)
    }

    private fun listenForIncomingCalls() {
        if (currentUserId.isEmpty()) return

        dbRef.child("calls").child(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        incomingCallDialog?.dismiss()
                        incomingCallDialog = null
                        return
                    }

                    val callerName = snapshot.child("callerName").getValue(String::class.java) ?: "Someone"
                    val channelName = snapshot.child("channelName").getValue(String::class.java).orEmpty()
                    val callType = snapshot.child("type").getValue(String::class.java) ?: "audio"
                    val status = snapshot.child("status").getValue(String::class.java) ?: "ringing"
                    val callerId = snapshot.child("callerId").getValue(String::class.java).orEmpty()

                    if (status == "ringing" && incomingCallDialog == null) {
                        incomingCallDialog = AlertDialog.Builder(this@ChatActivity)
                            .setTitle("Incoming ${callType.replaceFirstChar { it.uppercase() }} Call")
                            .setMessage("$callerName is calling you...")
                            .setPositiveButton("Accept") { _, _ ->
                                dbRef.child("calls").child(currentUserId).child("status").setValue("accepted")
                                incomingCallDialog = null

                                val intent = Intent(this@ChatActivity, VoiceCallActivity::class.java)
                                intent.putExtra("channelName", channelName)
                                intent.putExtra("type", callType)
                                intent.putExtra("isCaller", false)
                                intent.putExtra("recipientId", callerId)
                                startActivity(intent)
                            }
                            .setNegativeButton("Decline") { _, _ ->
                                dbRef.child("calls").child(currentUserId).child("status").setValue("ended")
                                dbRef.child("calls").child(currentUserId).removeValue()
                                incomingCallDialog = null
                            }
                            .setCancelable(false)
                            .create()
                        incomingCallDialog?.show()
                    } else if (status == "ended") {
                        incomingCallDialog?.dismiss()
                        incomingCallDialog = null
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun resetReplyState() {
        activeReplyToMsg = null
        binding.replyingToMessageBar.visibility = View.GONE
    }

    private fun loadCurrentUserDetails() {
        if (currentUserId.isEmpty()) return
        dbRef.child("users").child(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) {
                        currentUserName = user.firstName.ifEmpty { user.name.ifEmpty { user.username } }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadRecipientDetails() {
        dbRef.child("users").child(recipientId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return

                    val user = snapshot.getValue(User::class.java) ?: return
                    val name = user.name.ifEmpty {
                        "${user.firstName} ${user.lastName}".trim().ifEmpty { user.username }
                    }

                    binding.chatRecipientNameTxt.text = name

                    if (user.isOnline) {
                        binding.chatStatusTxt.text = "Active now"
                        binding.chatRecipientOnlineDot.visibility = View.VISIBLE
                    } else {
                        binding.chatStatusTxt.text = "Offline"
                        binding.chatRecipientOnlineDot.visibility = View.GONE
                    }

                    val initials = name.split(" ")
                        .mapNotNull { it.firstOrNull()?.toString() }
                        .take(2)
                        .joinToString("")
                        .uppercase()
                    binding.chatRecipientAvatar.text = initials.ifEmpty { "U" }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadMessages() {
        dbRef.child("messages").child(chatId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    messageList.clear()
                    for (child in snapshot.children) {
                        val msg = child.getValue(Message::class.java)
                        if (msg != null) {
                            messageList.add(msg)
                        }
                    }
                    messageList.sortBy { it.timestamp }
                    messageAdapter.updateData(messageList)
                    if (messageList.isNotEmpty()) {
                        binding.messagesRecyclerView.scrollToPosition(messageList.size - 1)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
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

        if (text.isEmpty() && selectedImageBase64.isNullOrEmpty()) {
            return
        }

        val messageId = dbRef.child("messages").child(chatId).push().key ?: return

        val messageMap = hashMapOf<String, Any>(
            "messageId" to messageId,
            "chatId" to chatId,
            "senderId" to currentUserId,
            "text" to text,
            "timestamp" to ServerValue.TIMESTAMP,
            "isEdited" to false,
            "heartReaction" to false,
            "isSystemMessage" to false
        )

        selectedImageBase64?.let { messageMap["imageBase64"] = it }
        activeReplyToMsg?.let { reply ->
            messageMap["replyToMsgId"] = reply.messageId
            messageMap["replyToText"] = reply.text.ifEmpty { "📷 Photo" }
        }

        dbRef.child("messages").child(chatId).child(messageId).setValue(messageMap)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val previewText = if (!selectedImageBase64.isNullOrEmpty()) "📷 Photo" else text
                    updateChatSummary(previewText)
                    sendNotificationToRecipient(currentUserName, previewText)

                    binding.chatMessageInput.setText("")
                    selectedImageBase64 = null
                    resetReplyState()
                } else {
                    Toast.makeText(this, "Failed to send: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun updateChatSummary(lastText: String) {
        val summaryMap = hashMapOf<String, Any>(
            "recipientId" to recipientId,
            "lastMessage" to lastText,
            "timestamp" to ServerValue.TIMESTAMP,
            "unread" to true
        )
        dbRef.child("chats").child(currentUserId).child(recipientId).setValue(summaryMap)

        val recipientSummaryMap = hashMapOf<String, Any>(
            "recipientId" to currentUserId,
            "lastMessage" to lastText,
            "timestamp" to ServerValue.TIMESTAMP,
            "unread" to true
        )
        dbRef.child("chats").child(recipientId).child(currentUserId).setValue(recipientSummaryMap)
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

    private fun sendNotificationToRecipient(title: String, messageText: String) {
        if (recipientId.isEmpty()) return
        dbRef.child("users").child(recipientId).child("fcmToken")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val token = snapshot.getValue(String::class.java).orEmpty()
                    if (token.isNotEmpty()) {
                        NotificationUtil.sendPushNotification(token, title, messageText)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun onScreenshotDetected() {
        if (chatId.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastScreenshotTime < 2000L) {
            return
        }
        lastScreenshotTime = now

        val systemMsgId = dbRef.child("messages").child(chatId).push().key ?: return
        val text = "$currentUserName took a screenshot"

        val systemMsgMap = hashMapOf<String, Any>(
            "messageId" to systemMsgId,
            "chatId" to chatId,
            "senderId" to currentUserId,
            "text" to text,
            "timestamp" to ServerValue.TIMESTAMP,
            "isSystemMessage" to true
        )

        dbRef.child("messages").child(chatId).child(systemMsgId).setValue(systemMsgMap)
        sendNotificationToRecipient("Screenshot Alert", text)
        Toast.makeText(this, "Screenshot detected", Toast.LENGTH_SHORT).show()
    }
}
