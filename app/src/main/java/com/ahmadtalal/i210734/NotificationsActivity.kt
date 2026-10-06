package com.ahmadtalal.i210734

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class NotificationsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notifications)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val notificationsSearchBtn = findViewById<ImageButton>(R.id.notificationsSearchBtn)

        notificationsSearchBtn.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
        }

        val notificationsHomeBtn = findViewById<ImageButton>(R.id.notificationsHomeBtn)

        notificationsHomeBtn.setOnClickListener {
            finish()
        }

        val notificationFriendsBtn = findViewById<ImageButton>(R.id.notificationFriendsBtn)

        notificationFriendsBtn.setOnClickListener {
            val intent = Intent(this, FriendsActivity::class.java)
            startActivity(intent)
            finish()
        }

        val notificationMarketplaceBtn = findViewById<ImageButton>(R.id.notificationMarketplaceBtn)

        notificationMarketplaceBtn.setOnClickListener {
            val intent = Intent(this, MarketplaceActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}