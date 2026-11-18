package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ahmadtalal.i210734_i210819.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Handler(Looper.getMainLooper()).postDelayed({
            checkAuthAndRoute()
        }, 5000)
    }

    private fun checkAuthAndRoute() {
        if (!Session.isLoggedIn) {
            navigateTo(LoginActivity::class.java)
            return
        }

        // Works offline too: the profile comes from the SQLite cache then.
        Api.get("users/get.php") { result ->
            if (isFinishing || isDestroyed) return@get
            when {
                result.ok -> {
                    val username = User.fromJson(result.obj()).username
                    if (username.isNotEmpty()) {
                        navigateTo(HomeFeedActivity::class.java)
                    } else {
                        navigateTo(EditProfileActivity::class.java)
                    }
                }
                // The server rejected our token; Api already opened the login screen.
                !Session.isLoggedIn -> finish()
                else -> {
                    Toast.makeText(this, result.error, Toast.LENGTH_SHORT).show()
                    navigateTo(HomeFeedActivity::class.java)
                }
            }
        }
    }

    private fun navigateTo(destination: Class<*>) {
        val intent = Intent(this, destination)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
