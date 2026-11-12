package com.ahmadtalal.i210734_i210819

import android.R
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmadtalal.i210734_i210819.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupLanguageSpinner()
        setupRecentLoginCard()
        setupClickListeners()
    }

    private fun setupLanguageSpinner() {
        val languages = arrayOf("English", "Urdu", "Arabic", "Spanish")
        val adapter = ArrayAdapter(
            this,
            R.layout.simple_spinner_item,
            languages
        )
        adapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        binding.languageSpinner.adapter = adapter
    }

    private fun setupRecentLoginCard() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val lastEmail = prefs.getString("last_user_email", null)
        val lastName = prefs.getString("last_user_name", null)

        if (!lastEmail.isNullOrEmpty()) {
            binding.recentLoginCard.visibility = View.VISIBLE
            binding.recentLoginTxt.visibility = View.VISIBLE
            binding.recentLoginName.text = if (!lastName.isNullOrEmpty()) lastName else lastEmail

            binding.recentLoginCard.setOnClickListener {
                binding.loginEmailInput.setText(lastEmail)
                binding.loginPasswordInput.requestFocus()
            }
        } else {
            binding.recentLoginCard.visibility = View.GONE
            binding.recentLoginTxt.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.createNewAccBtn.setOnClickListener {
            val intent = Intent(this, CreateAccountActivity::class.java)
            startActivity(intent)
        }

        binding.loginBtn.setOnClickListener {
            performLogin()
        }

        binding.forgotPasswordLink.setOnClickListener {
            val email = binding.loginEmailInput.text.toString().trim()
            if (email.isNotEmpty()) {
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Password reset email sent to $email", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Please enter your email address first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performLogin() {
        val email = binding.loginEmailInput.text.toString().trim()
        val password = binding.loginPasswordInput.text.toString().trim()

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email or mobile number", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Please enter your password", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                    saveFcmToken(userId)
                    checkUserDataAndNavigate(userId, email)
                } else {
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun saveFcmToken(userId: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful && task.result != null) {
                FirebaseDatabase.getInstance().getReference("users")
                    .child(userId)
                    .child("fcmToken")
                    .setValue(task.result)
            }
        }
    }

    private fun checkUserDataAndNavigate(userId: String, email: String) {
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)

        userRef.child("isOnline").setValue(true)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val username = snapshot.child("username").getValue(String::class.java)
                val name = snapshot.child("name").getValue(String::class.java)
                    ?: "${snapshot.child("firstName").getValue(String::class.java).orEmpty()} ${snapshot.child("lastName").getValue(String::class.java).orEmpty()}".trim()

                val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                prefs.edit()
                    .putString("last_user_email", email)
                    .putString("last_user_name", if (name.isNotEmpty()) name else email)
                    .apply()

                Toast.makeText(this@LoginActivity, "Welcome back!", Toast.LENGTH_SHORT).show()

                if (!username.isNullOrEmpty()) {
                    val intent = Intent(this@LoginActivity, HomeFeedActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    val intent = Intent(this@LoginActivity, EditProfileActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@LoginActivity, "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@LoginActivity, HomeFeedActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        })
    }
}
