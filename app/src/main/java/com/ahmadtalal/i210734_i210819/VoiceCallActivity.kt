package com.ahmadtalal.i210734_i210819

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmadtalal.i210734_i210819.databinding.ActivityVoiceCallBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas

class VoiceCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceCallBinding
    private var mRtcEngine: RtcEngine? = null

    private var channelName: String = ""
    private var callType: String = "audio" // "audio" or "video"
    private var isCaller: Boolean = false
    private var recipientId: String = ""

    private var isMuted = false
    private var isSpeakerOn = true

    private var callDurationSeconds = 0
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val dbRef = FirebaseDatabase.getInstance().reference

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: true
        if (audioGranted && cameraGranted) {
            initAgoraEngineAndJoinChannel()
        } else {
            Toast.makeText(this, "Permissions required for call", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                binding.callTimerTxt.text = "Connected"
                startTimer()
                if (callType == "video") {
                    setupRemoteVideo(uid)
                }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                Toast.makeText(this@VoiceCallActivity, "User left the call", Toast.LENGTH_SHORT).show()
                endCall()
            }
        }

        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            runOnUiThread {
                binding.callTimerTxt.text = "Ringing..."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityVoiceCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        channelName = intent.getStringExtra("channelName").orEmpty()
        callType = intent.getStringExtra("type") ?: "audio"
        isCaller = intent.getBooleanExtra("isCaller", false)
        recipientId = intent.getStringExtra("recipientId").orEmpty()

        setupUI()
        setupClickListeners()
        listenToCallStatus()
        checkPermissionsAndStart()
    }

    private fun setupUI() {
        if (callType == "video") {
            binding.localVideoContainer.visibility = View.VISIBLE
            binding.switchCameraBtn.visibility = View.VISIBLE
            binding.centerContactCard.visibility = View.GONE
        } else {
            binding.localVideoContainer.visibility = View.GONE
            binding.remoteVideoContainer.visibility = View.GONE
            binding.switchCameraBtn.visibility = View.GONE
            binding.centerContactCard.visibility = View.VISIBLE
        }

        if (recipientId.isNotEmpty()) {
            dbRef.child("users").child(recipientId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val user = snapshot.getValue(User::class.java)
                        val name = user?.name.orEmpty().ifEmpty {
                            "${user?.firstName.orEmpty()} ${user?.lastName.orEmpty()}".trim().ifEmpty { user?.username.orEmpty() }
                        }
                        if (name.isNotEmpty()) {
                            binding.callContactName.text = name
                            val initials = name.split(" ")
                                .mapNotNull { it.firstOrNull()?.toString() }
                                .take(2)
                                .joinToString("")
                                .uppercase()
                            binding.callContactAvatar.text = initials.ifEmpty { "U" }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

    private fun setupClickListeners() {
        binding.endCallBtn.setOnClickListener {
            endCall()
        }

        binding.muteMicBtn.setOnClickListener {
            isMuted = !isMuted
            mRtcEngine?.muteLocalAudioStream(isMuted)
            binding.muteMicBtn.alpha = if (isMuted) 0.5f else 1.0f
        }

        binding.toggleSpeakerBtn.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            mRtcEngine?.setEnableSpeakerphone(isSpeakerOn)
            binding.toggleSpeakerBtn.alpha = if (isSpeakerOn) 1.0f else 0.5f
        }

        binding.switchCameraBtn.setOnClickListener {
            mRtcEngine?.switchCamera()
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (callType == "video") {
            permissions.add(Manifest.permission.CAMERA)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            initAgoraEngineAndJoinChannel()
        } else {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun initAgoraEngineAndJoinChannel() {
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = BuildConfig.AGORA_APP_ID
            config.mEventHandler = mRtcEventHandler

            mRtcEngine = RtcEngine.create(config)
            mRtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            mRtcEngine?.enableAudio()
            mRtcEngine?.setEnableSpeakerphone(true)

            if (callType == "video") {
                mRtcEngine?.enableVideo()
                setupLocalVideo()
            }

            // Join channel using Testing Mode (token = null, channelName = exact chatId)
            mRtcEngine?.joinChannel(null, channelName, "", 0)
        } catch (e: Exception) {
            Log.e("VoiceCallActivity", "Agora init error: ${e.message}")
            Toast.makeText(this, "Failed to initialize call SDK: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupLocalVideo() {
        val localView: SurfaceView = RtcEngine.CreateRendererView(baseContext)
        binding.localVideoContainer.removeAllViews()
        binding.localVideoContainer.addView(localView)
        mRtcEngine?.setupLocalVideo(VideoCanvas(localView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
        mRtcEngine?.startPreview()
    }

    private fun setupRemoteVideo(uid: Int) {
        runOnUiThread {
            val remoteView: SurfaceView = RtcEngine.CreateRendererView(baseContext)
            binding.remoteVideoContainer.removeAllViews()
            binding.remoteVideoContainer.addView(remoteView)
            mRtcEngine?.setupRemoteVideo(VideoCanvas(remoteView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
            binding.remoteVideoContainer.visibility = View.VISIBLE
            binding.centerContactCard.visibility = View.GONE
        }
    }

    private fun listenToCallStatus() {
        val targetId = if (isCaller && recipientId.isNotEmpty()) recipientId else currentUserId
        if (targetId.isEmpty()) return

        dbRef.child("calls").child(targetId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        if (isCaller) {
                        }
                    } else {
                        val status = snapshot.child("status").getValue(String::class.java)
                        if (status == "ended") {
                            endCall()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun startTimer() {
        callDurationSeconds = 0
        timerRunnable = object : Runnable {
            override fun run() {
                callDurationSeconds++
                val minutes = callDurationSeconds / 60
                val seconds = callDurationSeconds % 60
                binding.callTimerTxt.text = String.format("%02d:%02d", minutes, seconds)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun endCall() {
        timerRunnable?.let { handler.removeCallbacks(it) }

        mRtcEngine?.leaveChannel()
        RtcEngine.destroy()
        mRtcEngine = null

        if (recipientId.isNotEmpty()) {
            dbRef.child("calls").child(recipientId).child("status").setValue("ended")
            dbRef.child("calls").child(recipientId).removeValue()
        }
        if (currentUserId.isNotEmpty()) {
            dbRef.child("calls").child(currentUserId).child("status").setValue("ended")
            dbRef.child("calls").child(currentUserId).removeValue()
        }

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerRunnable?.let { handler.removeCallbacks(it) }
        mRtcEngine?.leaveChannel()
        RtcEngine.destroy()
        mRtcEngine = null
    }
}
