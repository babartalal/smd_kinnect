package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ahmadtalal.i210734_i210819.databinding.ActivitySplashBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

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
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            navigateTo(LoginActivity::class.java)
        } else {
            val userRef = FirebaseDatabase.getInstance().getReference("users")
                .child(user.uid)
                .child("username")

            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val username = snapshot.getValue(String::class.java)
                    if (!username.isNullOrEmpty()) {
                        navigateTo(HomeFeedActivity::class.java)
                    } else {
                        navigateTo(EditProfileActivity::class.java)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@SplashActivity, "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
                    navigateTo(LoginActivity::class.java)
                }
            })
        }
    }

    private fun navigateTo(destination: Class<*>) {
        val intent = Intent(this, destination)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
