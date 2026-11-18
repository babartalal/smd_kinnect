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
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmadtalal.i210734_i210819.databinding.ActivityCreatePostBinding
import java.io.ByteArrayOutputStream
import java.io.InputStream

class CreatePostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePostBinding
    private val selectedBitmaps = mutableListOf<Bitmap>()
    private lateinit var photoAdapter: SelectedPhotoAdapter


    private val pickMultiplePhotosLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri>? ->
        uris?.forEach { uri ->
            processUri(uri)
        }
        updatePhotoPreviewVisibility()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCreatePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerView()
        setupClickListeners()
        showCurrentUser()
    }

    /** UI only: my name and initials/picture in the author row. */
    private fun showCurrentUser() {
        if (!Session.isLoggedIn) return

        Api.get("users/get.php") { result ->
            if (!result.ok || isFinishing || isDestroyed) return@get

            val me = User.fromJson(result.obj())
            val name = me.displayName()
            if (name.isEmpty()) return@get

            binding.createPostUserName.text = name
            binding.createPostAvatar.clipToOutline = true
            bindAvatar(me.profilePic, name, binding.createPostAvatar, binding.createPostInitials)
        }
    }

    private fun setupRecyclerView() {
        photoAdapter = SelectedPhotoAdapter(this, selectedBitmaps) { position ->
            if (position in selectedBitmaps.indices) {
                selectedBitmaps.removeAt(position)
                photoAdapter.notifyItemRemoved(position)
                updatePhotoPreviewVisibility()
            }
        }
        binding.selectedPhotosRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.selectedPhotosRecyclerView.adapter = photoAdapter
    }

    private fun setupClickListeners() {
        binding.createPostbackBtn.setOnClickListener { finish() }

        binding.openCamera.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        binding.pickPhotoCreatePost.setOnClickListener {
            pickMultiplePhotosLauncher.launch("image/*")
        }

        binding.postBtn.setOnClickListener {
            createPost()
        }
    }

    private fun processUri(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                val scaledBitmap = scaleBitmap(originalBitmap, 800)
                selectedBitmaps.add(scaledBitmap)
                photoAdapter.notifyItemInserted(selectedBitmaps.size - 1)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun updatePhotoPreviewVisibility() {
        if (selectedBitmaps.isNotEmpty()) {
            binding.selectedPhotosRecyclerView.visibility = View.VISIBLE
        } else {
            binding.selectedPhotosRecyclerView.visibility = View.GONE
        }
    }

    private fun createPost() {
        val postText = binding.postInput.text.toString().trim()

        if (postText.isEmpty() && selectedBitmaps.isEmpty()) {
            Toast.makeText(this, "Please write something or attach a photo", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Session.isLoggedIn) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        binding.postBtn.isEnabled = false

        // Each photo as JPEG bytes (max 800px, 50% quality).
        val photos = selectedBitmaps.map { bitmap ->
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
            outputStream.toByteArray()
        }
        // One clientId for both attempts below, so the post can only ever be stored once.
        val clientId = newClientId()

        // Online: a multipart upload, the text as a form field and every photo as a file.
        val files = photos.mapIndexed { index, bytes ->
            "images[]" to UploadPart.bytes("photo_${index + 1}.jpg", "image/jpeg", bytes)
        }
        Api.postMultipart(
            "posts/create.php", mapOf("text" to postText, "clientId" to clientId), files
        ) { result ->
            if (result.unreachable) {
                // No connection: hand the post to the offline queue instead. The queue
                // stores JSON bodies, so the photos travel as Base64 on this path.
                val body = mapOf(
                    "text" to postText,
                    "images" to photos.map { Base64.encodeToString(it, Base64.NO_WRAP) },
                    "clientId" to clientId
                )
                Api.post("posts/create.php", body, queueIfOffline = true) { queuedResult ->
                    onPostResult(queuedResult, postText)
                }
            } else {
                onPostResult(result, postText)
            }
        }
    }

    private fun onPostResult(result: ApiResult, postText: String) {
        if (result.queued) {
            // Saved in pending_actions; it is uploaded automatically once we are
            // back online. Until then the feed shows it marked as pending.
            // (Done even if this screen was closed meanwhile, so the feed still shows it.)
            LocalDb.get().addPendingPost(result.clientId, postText)
        }
        if (isFinishing || isDestroyed) return
        binding.postBtn.isEnabled = true
        when {
            result.ok -> {
                Toast.makeText(this, "Post published!", Toast.LENGTH_SHORT).show()
                finish()
            }
            result.queued -> {
                Toast.makeText(this, "You're offline. Your post will be published when you reconnect.", Toast.LENGTH_LONG).show()
                finish()
            }
            else -> Toast.makeText(this, "Failed to publish post: ${result.error}", Toast.LENGTH_SHORT).show()
        }
    }
}
