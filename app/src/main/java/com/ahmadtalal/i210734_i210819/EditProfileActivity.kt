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
import com.ahmadtalal.i210734_i210819.databinding.ActivityEditProfileBinding
import java.io.ByteArrayOutputStream
import java.io.InputStream

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    
    private var isFirstTimeUser: Boolean = false
    private var selectedProfilePicBase64: String? = null
    private var selectedCoverPhotoBase64: String? = null

    private val pickProfileImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageSelected(it, isProfilePic = true) }
    }

    private val pickCoverImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageSelected(it, isProfilePic = false) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupClickListeners()
        loadExistingUserData()
    }

    private fun setupClickListeners() {
        binding.cancelEditBtn.setOnClickListener {
            if (isFirstTimeUser) {
                Toast.makeText(this, "Please set up your username and bio to continue.", Toast.LENGTH_SHORT).show()
            } else {
                finish()
            }
        }

        binding.saveEditBtn.setOnClickListener {
            validateAndSaveProfile()
        }

        binding.editProfilePicBtn.setOnClickListener {
            pickProfileImageLauncher.launch("image/*")
        }

        binding.editCoverPhotoBtn.setOnClickListener {
            pickCoverImageLauncher.launch("image/*")
        }
    }

    private fun loadExistingUserData() {
        if (!Session.isLoggedIn) {
            navigateToLogin()
            return
        }

        Api.get("users/get.php") { result ->
            if (isFinishing || isDestroyed) return@get
            if (!result.ok) {
                Toast.makeText(this, "Failed to load profile: ${result.error}", Toast.LENGTH_SHORT).show()
                return@get
            }

            val user = User.fromJson(result.obj())
            isFirstTimeUser = user.username.isEmpty()

            binding.editNameInput.setText(user.displayName())
            binding.editUsernameInput.setText(user.username)
            binding.editBioInput.setText(user.bio)
            binding.editWebsiteInput.setText(user.website)
            binding.editEmailInput.setText(user.email)
            binding.editPhoneInput.setText(user.phone)
            binding.editGenderInput.setText(user.gender)

            // Don't replace a photo the user has just picked on this screen.
            if (user.profilePic.isNotEmpty() && selectedProfilePicBase64 == null) {
                binding.profilePicImageView.loadRemote(user.profilePic)
            }
            if (user.coverPhoto.isNotEmpty() && selectedCoverPhotoBase64 == null) {
                binding.coverImageView.loadRemote(user.coverPhoto)
            }
        }
    }

    private fun handleImageSelected(uri: Uri, isProfilePic: Boolean) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                val scaledBitmap = scaleBitmap(originalBitmap, 500)
                val outputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val byteArray = outputStream.toByteArray()
                val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                if (isProfilePic) {
                    selectedProfilePicBase64 = base64String
                    Glide.with(this).load(scaledBitmap).into(binding.profilePicImageView)
                } else {
                    selectedCoverPhotoBase64 = base64String
                    Glide.with(this).load(scaledBitmap).into(binding.coverImageView)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun validateAndSaveProfile() {
        if (!Session.isLoggedIn) {
            navigateToLogin()
            return
        }

        val enteredName = binding.editNameInput.text.toString().trim()
        val enteredUsername = binding.editUsernameInput.text.toString().trim()
        val enteredBio = binding.editBioInput.text.toString().trim()
        val enteredEmail = binding.editEmailInput.text.toString().trim()

        if (enteredUsername.isEmpty()) {
            Toast.makeText(this, "Username is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (enteredBio.isEmpty()) {
            Toast.makeText(this, "Bio is required", Toast.LENGTH_SHORT).show()
            return
        }

        // The server checks that the username is free and saves everything in
        // one request. Photos are only sent when a new one was picked.
        val body = mapOf(
            "name" to enteredName,
            "username" to enteredUsername,
            "bio" to enteredBio,
            "website" to binding.editWebsiteInput.text.toString().trim(),
            "email" to enteredEmail,
            "phone" to binding.editPhoneInput.text.toString().trim(),
            "gender" to binding.editGenderInput.text.toString().trim(),
            "profilePic" to selectedProfilePicBase64,
            "coverPhoto" to selectedCoverPhotoBase64
        )

        binding.saveEditBtn.isEnabled = false
        Api.post("users/update.php", body) { result ->
            if (isFinishing || isDestroyed) return@post
            binding.saveEditBtn.isEnabled = true

            if (!result.ok) {
                Toast.makeText(this, result.error, Toast.LENGTH_SHORT).show()
                return@post
            }

            val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            prefs.edit()
                .putString("last_user_email", enteredEmail)
                .putString("last_user_name", if (enteredName.isNotEmpty()) enteredName else enteredUsername)
                .apply()

            Toast.makeText(this, "Profile saved successfully!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, HomeFeedActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
