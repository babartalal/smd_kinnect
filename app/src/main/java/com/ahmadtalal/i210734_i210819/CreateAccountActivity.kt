package com.ahmadtalal.i210734_i210819

import android.R
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmadtalal.i210734_i210819.databinding.ActivityCreateAccountBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class CreateAccountActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateAccountBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCreateAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupSpinners()
        setupPasswordWatcher()
        setupClickListeners()
    }

    private fun setupSpinners() {
        val days = (1..31).toList()
        val dayAdapter = ArrayAdapter(this, R.layout.simple_spinner_item, days)
        dayAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        binding.daySpinner.adapter = dayAdapter

        val months = arrayOf(
            "January", "February", "March", "April",
            "May", "June", "July", "August",
            "September", "October", "November", "December"
        )
        val monthAdapter = ArrayAdapter(this, R.layout.simple_spinner_item, months)
        monthAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        binding.monthSpinner.adapter = monthAdapter

        val years = (1950..2026).toList().reversed()
        val yearAdapter = ArrayAdapter(this, R.layout.simple_spinner_item, years)
        yearAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        binding.yearSpinner.adapter = yearAdapter
    }

    private fun setupPasswordWatcher() {
        binding.passwordInputText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePasswordStrength(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updatePasswordStrength(password: String) {
        val indicator = binding.passwordStrengthTxt
        when {
            password.isEmpty() -> {
                indicator.text = "Password strength: Empty"
                indicator.setTextColor(Color.GRAY)
            }
            password.length < 6 -> {
                indicator.text = "Password strength: Weak (Too short)"
                indicator.setTextColor(Color.RED)
            }
            password.length in 6..8 -> {
                if (password.any { it.isDigit() } || password.any { !it.isLetterOrDigit() }) {
                    indicator.text = "Password strength: Medium"
                    indicator.setTextColor(Color.parseColor("#F57C00")) // Orange
                } else {
                    indicator.text = "Password strength: Weak"
                    indicator.setTextColor(Color.RED)
                }
            }
            else -> { // length > 8
                val hasDigit = password.any { it.isDigit() }
                val hasLetter = password.any { it.isLetter() }
                val hasSpecial = password.any { !it.isLetterOrDigit() }
                if (hasDigit && hasLetter && hasSpecial) {
                    indicator.text = "Password strength: Strong"
                    indicator.setTextColor(Color.parseColor("#388E3C")) // Green
                } else if (hasDigit && hasLetter) {
                    indicator.text = "Password strength: Medium"
                    indicator.setTextColor(Color.parseColor("#F57C00"))
                } else {
                    indicator.text = "Password strength: Weak"
                    indicator.setTextColor(Color.RED)
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.loginLink.setOnClickListener { finish() }
        binding.backToLoginBtn.setOnClickListener { finish() }

        binding.createAccBtn.setOnClickListener {
            registerUser()
        }
    }

    private fun registerUser() {
        val firstName = binding.firstNameInput.text.toString().trim()
        val lastName = binding.lastNameInput.text.toString().trim()
        val email = binding.emailInputText.text.toString().trim()
        val password = binding.passwordInputText.text.toString().trim()

        if (firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(this, "Please enter your first and last name", Toast.LENGTH_SHORT).show()
            return
        }

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Please enter a password", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        if (!binding.termsCheckbox.isChecked) {
            Toast.makeText(this, "Please agree to Kinnect Terms and Policy", Toast.LENGTH_SHORT).show()
            return
        }

        val day = binding.daySpinner.selectedItem.toString()
        val month = binding.monthSpinner.selectedItem.toString()
        val year = binding.yearSpinner.selectedItem.toString()
        val birthday = "$day $month $year"

        val selectedGenderId = binding.genderRadioGroup.checkedRadioButtonId
        val gender = if (selectedGenderId != -1) {
            findViewById<RadioButton>(selectedGenderId)?.text?.toString() ?: "Not Specified"
        } else {
            "Not Specified"
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                    val userMap = hashMapOf<String, Any>(
                        "userId" to userId,
                        "firstName" to firstName,
                        "lastName" to lastName,
                        "name" to "$firstName $lastName",
                        "birthday" to birthday,
                        "gender" to gender,
                        "email" to email,
                        "isOnline" to true
                    )

                    FirebaseDatabase.getInstance().getReference("users")
                        .child(userId)
                        .setValue(userMap)
                        .addOnCompleteListener { dbTask ->
                            if (dbTask.isSuccessful) {
                                Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this, EditProfileActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this, "Failed to save profile: ${dbTask.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "Sign up failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}
