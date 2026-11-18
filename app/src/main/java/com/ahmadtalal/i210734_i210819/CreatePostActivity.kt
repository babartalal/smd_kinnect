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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.io.ByteArrayOutputStream
import java.io.InputStream

class CreatePostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePostBinding
    private val selectedBitmaps = mutableListOf<Bitmap>()
    private lateinit var photoAdapter: SelectedPhotoAdapter

    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val dbRef = FirebaseDatabase.getInstance().reference

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

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        binding.postBtn.isEnabled = false

        val imageBase64List = mutableListOf<String>()
        for (bitmap in selectedBitmaps) {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            imageBase64List.add(base64String)
        }

        val postId = dbRef.child("posts").push().key ?: return

        val postMap = hashMapOf<String, Any>(
            "postId" to postId,
            "authorId" to currentUserId,
            "text" to postText,
            "timestamp" to ServerValue.TIMESTAMP,
            "images" to imageBase64List
        )

        dbRef.child("posts").child(postId).setValue(postMap)
            .addOnCompleteListener { task ->
                binding.postBtn.isEnabled = true
                if (task.isSuccessful) {
                    Toast.makeText(this, "Post published!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Failed to publish post: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
