package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ahmadtalal.i210734_i210819.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    private var splashDelayDone = false
    private var permissionAnswered = false
    private var routed = false

    /**
     * From Android 17 an app needs this permission to connect to addresses on
     * the local network, which is where the XAMPP server lives.
     */
    private val localNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Kinnect needs local network access to reach its server.", Toast.LENGTH_LONG).show()
        }
        permissionAnswered = true
        routeWhenReady()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Handler(Looper.getMainLooper()).postDelayed({
            splashDelayDone = true
            routeWhenReady()
        }, 5000)

        if (Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(this, LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED
        ) {
            localNetworkPermissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
        } else {
            permissionAnswered = true
        }
    }

    private fun routeWhenReady() {
        if (routed || !splashDelayDone || !permissionAnswered) return
        routed = true
        checkAuthAndRoute()
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

    companion object {
        private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
