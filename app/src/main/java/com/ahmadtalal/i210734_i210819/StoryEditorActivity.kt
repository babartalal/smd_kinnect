package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
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
    private var selectedBitmap: Bitmap? = null


    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processImageUri(it) }
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

        binding.closeStoryBtn.setOnClickListener { finish() }

        binding.storyPreviewImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.shareToStoryBtn.setOnClickListener {
            publishStory("public")
        }

        binding.shareToCloseFriendsBtn.setOnClickListener {
            publishStory("close_friends")
        }

        binding.sendStoryBtn.setOnClickListener {
            publishStory("public")
        }

        // Auto open gallery picker on start if no image loaded
        pickImageLauncher.launch("image/*")
    }

    private fun processImageUri(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                selectedBitmap = scaleBitmap(originalBitmap, 800)
                Glide.with(this).load(selectedBitmap).into(binding.storyPreviewImage)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun publishStory(audience: String) {
        if (!Session.isLoggedIn) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedBitmap == null) {
            Toast.makeText(this, "Please select an image for your story", Toast.LENGTH_SHORT).show()
            return
        }

        binding.sendStoryBtn.isEnabled = false

        // Compress Bitmap heavily (max 800px width, 50% JPEG quality)
        val outputStream = ByteArrayOutputStream()
        selectedBitmap!!.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        val byteArray = outputStream.toByteArray()
        val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)

        val textLabel = binding.storyTextInput.text.toString().trim()
        val body = mapOf(
            "image" to base64String,
            "textLabel" to textLabel,
            "audience" to audience,
            "clientId" to newClientId()
        )

        Api.post("stories/create.php", body, queueIfOffline = true) { result ->
            if (isFinishing || isDestroyed) return@post
            binding.sendStoryBtn.isEnabled = true
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
    }
}
