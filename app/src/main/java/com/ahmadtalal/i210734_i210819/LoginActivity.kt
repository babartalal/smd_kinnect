package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmadtalal.i210734_i210819.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

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
        val languages = arrayOf("English (UK)", "Urdu", "Arabic", "Spanish")
        // Flat grey text for the closed spinner, as in the design.
        val adapter = object : ArrayAdapter<String>(this, R.layout.auth_spinner_item, languages) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.setTextColor(this@LoginActivity.getColor(R.color.k_text_secondary))
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.languageSpinner.adapter = adapter
    }

    private fun setupRecentLoginCard() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val lastEmail = prefs.getString("last_user_email", null)
        val lastName = prefs.getString("last_user_name", null)

        if (!lastEmail.isNullOrEmpty()) {
            binding.recentLoginCard.visibility = View.VISIBLE
            binding.recentLoginTxt.visibility = View.VISIBLE
            val shownName = if (!lastName.isNullOrEmpty()) lastName else lastEmail
            binding.recentLoginName.text = shownName
            binding.recentLoginAvatar.text = initialsOf(shownName)

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

        // Eye icon: show / hide the typed password.
        binding.loginPasswordToggle.setOnClickListener {
            val input = binding.loginPasswordInput
            val hidden = input.transformationMethod is PasswordTransformationMethod
            input.transformationMethod = if (hidden) null else PasswordTransformationMethod.getInstance()
            input.setSelection(input.text.length)
        }

        binding.forgotPasswordLink.setOnClickListener {
            val email = binding.loginEmailInput.text.toString().trim()
            if (email.isNotEmpty()) {
                requestPasswordReset(email)
            } else {
                Toast.makeText(this, "Please enter your email address first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Step 1: ask the server to email a reset code. */
    private fun requestPasswordReset(email: String) {
        Api.post("auth/request_reset.php", mapOf("email" to email)) { result ->
            if (isFinishing || isDestroyed) return@post
            if (result.ok) {
                showResetCodeDialog(email)
            } else {
                Toast.makeText(this, "Error: ${result.error}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Step 2: enter the emailed code and choose a new password. */
    private fun showResetCodeDialog(email: String) {
        val codeInput = EditText(this).apply {
            hint = "6-digit code"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val passwordInput = EditText(this).apply {
            hint = "New password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(codeInput)
            addView(passwordInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Reset password")
            .setMessage("If $email has an account, a reset code was emailed to it. Enter the code and a new password.")
            .setView(form)
            .setPositiveButton("Reset") { _, _ ->
                val body = mapOf(
                    "email" to email,
                    "code" to codeInput.text.toString().trim(),
                    "password" to passwordInput.text.toString().trim()
                )
                Api.post("auth/reset_password.php", body) { result ->
                    val message = if (result.ok) "Password changed. You can log in now." else "Error: ${result.error}"
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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

        binding.loginBtn.isEnabled = false
        Api.post("auth/login.php", mapOf("email" to email, "password" to password)) { result ->
            if (isFinishing || isDestroyed) return@post
            binding.loginBtn.isEnabled = true

            if (!result.ok) {
                Toast.makeText(this, "Login failed: ${result.error}", Toast.LENGTH_LONG).show()
                return@post
            }

            val data = result.obj()
            val user = User.fromJson(data.optJSONObject("user") ?: return@post)
            Session.save(user.userId, data.optString("token"))
            KinnectApp.sendHeartbeat() // marks us online and registers the FCM token

            onLoggedIn(user, email)
        }
    }

    private fun onLoggedIn(user: User, email: String) {
        val name = user.displayName()
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit()
            .putString("last_user_email", email)
            .putString("last_user_name", if (name.isNotEmpty()) name else email)
            .apply()

        Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()

        val destination = if (user.username.isNotEmpty()) {
            HomeFeedActivity::class.java
        } else {
            EditProfileActivity::class.java
        }
        val intent = Intent(this, destination)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
