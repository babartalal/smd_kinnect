package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.ahmadtalal.i210734_i210819.databinding.ActivityEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream
import java.io.InputStream

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    
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
        val currentUser = auth.currentUser ?: run {
            navigateToLogin()
            return
        }

        val userId = currentUser.uid
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val name = snapshot.child("name").getValue(String::class.java)
                    ?: "${snapshot.child("firstName").getValue(String::class.java).orEmpty()} ${snapshot.child("lastName").getValue(String::class.java).orEmpty()}".trim()
                val username = snapshot.child("username").getValue(String::class.java).orEmpty()
                val bio = snapshot.child("bio").getValue(String::class.java).orEmpty()
                val website = snapshot.child("website").getValue(String::class.java).orEmpty()
                val email = snapshot.child("email").getValue(String::class.java) ?: currentUser.email.orEmpty()
                val phone = snapshot.child("phone").getValue(String::class.java).orEmpty()
                val gender = snapshot.child("gender").getValue(String::class.java).orEmpty()
                val profilePicBase64 = snapshot.child("profilePicBase64").getValue(String::class.java)
                val coverPhotoBase64 = snapshot.child("coverPhotoBase64").getValue(String::class.java)

                isFirstTimeUser = username.isEmpty()

                binding.editNameInput.setText(name)
                binding.editUsernameInput.setText(username)
                binding.editBioInput.setText(bio)
                binding.editWebsiteInput.setText(website)
                binding.editEmailInput.setText(email)
                binding.editPhoneInput.setText(phone)
                binding.editGenderInput.setText(gender)

                if (!profilePicBase64.isNullOrEmpty()) {
                    displayBase64Image(profilePicBase64, binding.profilePicImageView)
                }

                if (!coverPhotoBase64.isNullOrEmpty()) {
                    displayBase64Image(coverPhotoBase64, binding.coverImageView)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@EditProfileActivity, "Failed to load profile: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
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

    private fun displayBase64Image(base64Str: String, targetView: ImageView) {
        try {
            val imageBytes = Base64.decode(base64Str, Base64.DEFAULT)
            val decodedBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            Glide.with(this).load(decodedBitmap).into(targetView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun validateAndSaveProfile() {
        val currentUser = auth.currentUser ?: run {
            navigateToLogin()
            return
        }
        val userId = currentUser.uid

        val enteredName = binding.editNameInput.text.toString().trim()
        val enteredUsername = binding.editUsernameInput.text.toString().trim()
        val enteredBio = binding.editBioInput.text.toString().trim()
        val enteredWebsite = binding.editWebsiteInput.text.toString().trim()
        val enteredEmail = binding.editEmailInput.text.toString().trim()
        val enteredPhone = binding.editPhoneInput.text.toString().trim()
        val enteredGender = binding.editGenderInput.text.toString().trim()

        if (enteredUsername.isEmpty()) {
            Toast.makeText(this, "Username is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (enteredBio.isEmpty()) {
            Toast.makeText(this, "Bio is required", Toast.LENGTH_SHORT).show()
            return
        }

        val usersRef = FirebaseDatabase.getInstance().getReference("users")
        usersRef.orderByChild("username").equalTo(enteredUsername)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var isTakenByOther = false
                    for (child in snapshot.children) {
                        if (child.key != userId) {
                            isTakenByOther = true
                            break
                        }
                    }

                    if (isTakenByOther) {
                        Toast.makeText(this@EditProfileActivity, "Username '$enteredUsername' is already taken. Please choose another.", Toast.LENGTH_SHORT).show()
                    } else {
                        saveUserDataAndProceed(
                            userId = userId,
                            name = enteredName,
                            username = enteredUsername,
                            bio = enteredBio,
                            website = enteredWebsite,
                            email = enteredEmail,
                            phone = enteredPhone,
                            gender = enteredGender
                        )
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@EditProfileActivity, "Error checking username: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun saveUserDataAndProceed(
        userId: String,
        name: String,
        username: String,
        bio: String,
        website: String,
        email: String,
        phone: String,
        gender: String
    ) {
        val updates = mutableMapOf<String, Any>(
            "name" to name,
            "username" to username,
            "bio" to bio,
            "website" to website,
            "email" to email,
            "phone" to phone,
            "gender" to gender,
            "isOnline" to true
        )

        selectedProfilePicBase64?.let { updates["profilePicBase64"] = it }
        selectedCoverPhotoBase64?.let { updates["coverPhotoBase64"] = it }

        FirebaseDatabase.getInstance().getReference("users").child(userId)
            .updateChildren(updates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                    prefs.edit()
                        .putString("last_user_email", email)
                        .putString("last_user_name", if (name.isNotEmpty()) name else username)
                        .apply()

                    Toast.makeText(this, "Profile saved successfully!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, HomeFeedActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "Failed to save profile: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
