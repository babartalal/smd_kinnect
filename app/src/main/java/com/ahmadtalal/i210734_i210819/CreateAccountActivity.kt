package com.ahmadtalal.i210734_i210819

import android.R
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmadtalal.i210734_i210819.databinding.ActivityCreateAccountBinding
import com.ahmadtalal.i210734_i210819.R as AppR

class CreateAccountActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateAccountBinding

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
        val dayAdapter = ArrayAdapter(this, AppR.layout.auth_spinner_item, days)
        dayAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        binding.daySpinner.adapter = dayAdapter

        val months = arrayOf(
            "January", "February", "March", "April",
            "May", "June", "July", "August",
            "September", "October", "November", "December"
        )
        // The narrow card shows the short month ("Mar"); the list and the saved value keep the full name.
        val monthAdapter = object : ArrayAdapter<String>(this, AppR.layout.auth_spinner_item, months) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.text = months[position].take(3)
                return view
            }
        }
        monthAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        binding.monthSpinner.adapter = monthAdapter

        val years = (1950..2026).toList().reversed()
        val yearAdapter = ArrayAdapter(this, AppR.layout.auth_spinner_item, years)
        yearAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        binding.yearSpinner.adapter = yearAdapter

        // Tapping anywhere on a birthday card opens its dropdown.
        binding.dayCard.setOnClickListener { binding.daySpinner.performClick() }
        binding.monthCard.setOnClickListener { binding.monthSpinner.performClick() }
        binding.yearCard.setOnClickListener { binding.yearSpinner.performClick() }
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
        val hasDigit = password.any { it.isDigit() }
        val hasLetter = password.any { it.isLetter() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }

        // Same rules as before: 0 = empty, 1 = weak, 2 = medium, 3 = strong
        val level = when {
            password.isEmpty() -> 0
            password.length < 6 -> 1
            password.length in 6..8 -> if (hasDigit || hasSpecial) 2 else 1
            hasDigit && hasLetter && hasSpecial -> 3
            hasDigit && hasLetter -> 2
            else -> 1
        }
        val label = when (level) {
            1 -> "Weak"
            2 -> "Medium"
            3 -> "Strong"
            else -> ""
        }
        val color = getColor(
            when (level) {
                1 -> AppR.color.k_call_red
                2 -> AppR.color.k_badge_red
                3 -> AppR.color.k_online_green
                else -> AppR.color.k_divider
            }
        )
        val off = getColor(AppR.color.k_divider)

        // Label plus the three bar segments under the password card.
        binding.passwordStrengthTxt.text = label
        binding.passwordStrengthTxt.setTextColor(color)
        val segments = listOf(binding.strengthSegment1, binding.strengthSegment2, binding.strengthSegment3)
        segments.forEachIndexed { index, segment ->
            segment.backgroundTintList = ColorStateList.valueOf(if (index < level) color else off)
        }
    }

    private fun setupClickListeners() {
        binding.loginLink.setOnClickListener { finish() }
        binding.backToLoginBtn.setOnClickListener { finish() }

        // Eye icon: show / hide the typed password.
        binding.passwordToggle.setOnClickListener {
            val input = binding.passwordInputText
            val hidden = input.transformationMethod is PasswordTransformationMethod
            input.transformationMethod = if (hidden) null else PasswordTransformationMethod.getInstance()
            input.setSelection(input.text.length)
        }

        // The selected gender card shows its label in bold.
        binding.genderRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            for (i in 0 until group.childCount) {
                val option = group.getChildAt(i) as? RadioButton ?: continue
                option.typeface = if (option.id == checkedId) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }

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

        val body = mapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "email" to email,
            "password" to password,
            "birthday" to birthday,
            "gender" to gender
        )

        binding.createAccBtn.isEnabled = false
        Api.post("auth/signup.php", body) { result ->
            if (isFinishing || isDestroyed) return@post
            binding.createAccBtn.isEnabled = true

            if (!result.ok) {
                Toast.makeText(this, "Sign up failed: ${result.error}", Toast.LENGTH_LONG).show()
                return@post
            }

            val data = result.obj()
            val user = User.fromJson(data.optJSONObject("user") ?: return@post)
            Session.save(user.userId, data.optString("token"))
            KinnectApp.sendHeartbeat() // marks us online and registers the FCM token

            Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, EditProfileActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
