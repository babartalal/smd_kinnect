package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmadtalal.i210734_i210819.databinding.ActivityMenuBinding

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

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
        if (!Session.isLoggedIn) return

        Api.get("users/get.php") { result ->
            if (!result.ok || isFinishing || isDestroyed) return@get

            val name = User.fromJson(result.obj()).displayName()
            if (name.isNotEmpty()) {
                binding.menuUserName.text = name
                binding.menuUserAvatar.text = initialsOf(name)
            }
        }
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

        // The Friends and Marketplace shortcut tiles open the same screens as the tabs.
        binding.menuFriendsTile.setOnClickListener {
            startActivity(Intent(this, FriendsActivity::class.java))
            finish()
        }

        binding.menuMarketplaceTile.setOnClickListener {
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
        binding.logoutBtn.isEnabled = false
        // Tell the server (ends the session, marks us offline), then sign out
        // locally even if the server could not be reached.
        Api.post("auth/logout.php") {
            Session.clear()
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
