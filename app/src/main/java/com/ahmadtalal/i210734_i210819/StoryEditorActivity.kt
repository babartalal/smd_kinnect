package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.ahmadtalal.i210734_i210819.databinding.ActivityStoryEditorBinding
import java.io.ByteArrayOutputStream
import java.io.InputStream

class StoryEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoryEditorBinding

    /** The picked photo, scaled down; null when a video (or nothing) is picked. */
    private var selectedBitmap: Bitmap? = null

    /** The picked video; null when a photo (or nothing) is picked. */
    private var selectedVideoUri: Uri? = null

    private var publishing = false

    /** System picker for a photo or a video; the content is read through the ContentResolver. */
    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            if (Api.mediaTypeOf(Api.mimeTypeOf(it)) == "video") processVideoUri(it) else processImageUri(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityStoryEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Round the preview's corners (the XML attribute only works on Android 12+).
        binding.storyFrame.clipToOutline = true
        showMyInitials()

        binding.closeStoryBtn.setOnClickListener { finish() }

        // Tap the preview (photo or video) to choose something else.
        binding.storyPreviewImage.setOnClickListener { pickMedia() }
        binding.storyPreviewVideo.setOnClickListener { pickMedia() }

        binding.shareToStoryBtn.setOnClickListener {
            publishStory("public")
        }

        binding.shareToCloseFriendsBtn.setOnClickListener {
            publishStory("close_friends")
        }

        binding.sendStoryBtn.setOnClickListener {
            publishStory("public")
        }

        // Auto open the picker on start if nothing is loaded
        if (savedInstanceState == null) pickMedia()
    }

    override fun onResume() {
        super.onResume()
        if (selectedVideoUri != null) binding.storyPreviewVideo.start()
    }

    override fun onPause() {
        super.onPause()
        binding.storyPreviewVideo.pause()
    }

    private fun pickMedia() {
        if (!publishing) pickMediaLauncher.launch(arrayOf("image/*", "video/*"))
    }

    /** Shows my initials in the "Your story" pill. */
    private fun showMyInitials() {
        if (!Session.isLoggedIn) return
        Api.get("users/get.php", mapOf("user_id" to Session.userId)) { result ->
            if (!result.ok || isFinishing || isDestroyed) return@get
            val name = User.fromJson(result.obj()).displayName()
            if (name.isNotEmpty()) binding.storyOwnerInitials.text = initialsOf(name)
        }
    }

    private fun processImageUri(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                selectedBitmap = scaleBitmap(originalBitmap, 800)
                selectedVideoUri = null
                binding.storyPreviewVideo.stopPlayback()
                binding.storyPreviewVideo.visibility = View.GONE
                Glide.with(this).load(selectedBitmap).into(binding.storyPreviewImage)
            } else {
                Toast.makeText(this, "This picture could not be opened", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Preview the picked video, looping, in the story frame. */
    private fun processVideoUri(uri: Uri) {
        selectedVideoUri = uri
        selectedBitmap = null
        binding.storyPreviewVideo.visibility = View.VISIBLE
        binding.storyPreviewVideo.setOnPreparedListener { player ->
            player.isLooping = true
            binding.storyPreviewVideo.start()
        }
        binding.storyPreviewVideo.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, "This video cannot be previewed, but you can still share it", Toast.LENGTH_SHORT).show()
            true
        }
        binding.storyPreviewVideo.setVideoURI(uri)
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

    private fun publishStory(audience: String) {
        if (publishing) return
        if (!Session.isLoggedIn) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = selectedBitmap
        val videoUri = selectedVideoUri
        if (bitmap == null && videoUri == null) {
            Toast.makeText(this, "Please select a photo or a video for your story", Toast.LENGTH_SHORT).show()
            return
        }

        setPublishing(true)
        val textLabel = binding.storyTextInput.text.toString().trim()
        // One clientId for every attempt below, so the story can only ever be stored once.
        val clientId = newClientId()

        if (videoUri != null) {
            // A video goes to media/upload.php first; the story then points at the stored file.
            Toast.makeText(this, "Uploading video...", Toast.LENGTH_SHORT).show()
            Api.uploadUri(videoUri) { upload ->
                when {
                    upload.ok -> createStory(upload.obj().optString("path"), "video", textLabel, audience, clientId)
                    upload.unreachable -> onPublishFailed("You're offline. Video stories need a connection. Reconnect and try again.")
                    else -> onPublishFailed("Failed to upload video: ${upload.error}")
                }
            }
            return
        }

        // A photo: compressed heavily (max 800px width, 50% JPEG quality), then uploaded the same way.
        val outputStream = ByteArrayOutputStream()
        bitmap!!.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        val jpeg = outputStream.toByteArray()

        Api.upload(UploadPart.bytes("story.jpg", "image/jpeg", jpeg)) { upload ->
            when {
                upload.ok -> createStory(upload.obj().optString("path"), "image", textLabel, audience, clientId)
                upload.unreachable -> {
                    // No connection: queue the story instead. The offline queue stores
                    // JSON bodies, so the photo travels as Base64 on this path.
                    val body = mapOf(
                        "image" to Base64.encodeToString(jpeg, Base64.NO_WRAP),
                        "textLabel" to textLabel,
                        "audience" to audience,
                        "clientId" to clientId
                    )
                    Api.post("stories/create.php", body, queueIfOffline = true) { result -> onStoryResult(result) }
                }
                else -> onPublishFailed("Failed to upload photo: ${upload.error}")
            }
        }
    }

    /** Create the story from a file that media/upload.php has stored. */
    private fun createStory(mediaPath: String, mediaType: String, textLabel: String, audience: String, clientId: String) {
        val body = mapOf(
            "media" to mediaPath,
            "mediaType" to mediaType,
            "textLabel" to textLabel,
            "audience" to audience,
            "clientId" to clientId
        )
        // The file is already on the server, so this small request may wait in the offline queue.
        Api.post("stories/create.php", body, queueIfOffline = true) { result -> onStoryResult(result) }
    }

    private fun onStoryResult(result: ApiResult) {
        if (isFinishing || isDestroyed) return
        setPublishing(false)
        if (result.ok || result.queued) {
            val message = if (result.ok) "Story published!" else "You're offline. Your story will be published when you reconnect."
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            val intent = Intent(this, HomeFeedActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "Failed to publish story: ${result.error}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onPublishFailed(message: String) {
        if (isFinishing || isDestroyed) return
        setPublishing(false)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /** One publish at a time: the three share buttons are off while a story is on its way. */
    private fun setPublishing(active: Boolean) {
        publishing = active
        binding.sendStoryBtn.isEnabled = !active
        binding.shareToStoryBtn.isEnabled = !active
        binding.shareToCloseFriendsBtn.isEnabled = !active
    }
}
