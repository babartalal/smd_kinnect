package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmadtalal.i210734_i210819.databinding.ActivityMenuBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        loadUserData()
        setupClickListeners()
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.uid)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val name = snapshot.child("name").getValue(String::class.java)
                    ?: "${snapshot.child("firstName").getValue(String::class.java).orEmpty()} ${snapshot.child("lastName").getValue(String::class.java).orEmpty()}".trim()

                if (name.isNotEmpty()) {
                    binding.menuUserName.text = name
                    val initials = name.split(" ")
                        .mapNotNull { it.firstOrNull()?.toString() }
                        .take(2)
                        .joinToString("")
                        .uppercase()
                    if (initials.isNotEmpty()) {
                        binding.menuUserAvatar.text = initials
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupClickListeners() {
        binding.userProfileCard.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.menuSearchBtn.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        binding.menuChatsBtn.setOnClickListener {
            startActivity(Intent(this, ChatsActivity::class.java))
        }

        binding.menuHomeBtn.setOnClickListener {
            finish()
        }

        binding.menuFriendsBtn.setOnClickListener {
            startActivity(Intent(this, FriendsActivity::class.java))
            finish()
        }

        binding.menuMarketplaceBtn.setOnClickListener {
            startActivity(Intent(this, MarketplaceActivity::class.java))
            finish()
        }

        binding.menuNotificationsBtn.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
            finish()
        }

        binding.logoutBtn.setOnClickListener {
            performLogout()
        }
    }

    private fun performLogout() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            FirebaseDatabase.getInstance().getReference("users")
                .child(userId)
                .child("isOnline")
                .setValue(false)
                .addOnCompleteListener {
                    auth.signOut()
                    navigateToLogin()
                }
        } else {
            auth.signOut()
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
