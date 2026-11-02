package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class FriendsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_friends)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val friendsSearchBtn = findViewById<ImageButton>(R.id.friendsSearchBtn)

        friendsSearchBtn.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
        }

        val homeFeedBtn = findViewById<ImageButton>(R.id.homeFeedBtn)
        homeFeedBtn.setOnClickListener {
            finish()
        }

        val friendsMarketplaceBtn = findViewById<ImageButton>(R.id.friendsMarketplaceBtn)
        friendsMarketplaceBtn.setOnClickListener {
            val intent = Intent(this, MarketplaceActivity::class.java)
            startActivity(intent)
            finish()
        }

        val friendsNotificationsBtn = findViewById<ImageButton>(R.id.friendsNotificationsBtn)

        friendsNotificationsBtn.setOnClickListener {
            val intent = Intent(this, NotificationsActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}