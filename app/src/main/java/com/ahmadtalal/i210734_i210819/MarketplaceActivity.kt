package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MarketplaceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_marketplace)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val marketplaceSearchBtn = findViewById<View>(R.id.marketplaceSearchBtn)

        marketplaceSearchBtn.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.marketplaceChatsBtn).setOnClickListener {
            startActivity(Intent(this, ChatsActivity::class.java))
        }

        val marketplaceHomeBtn = findViewById<View>(R.id.marketplaceHomeBtn)

        marketplaceHomeBtn.setOnClickListener {
            finish()
        }


        val marketplaceFriendsBtn = findViewById<View>(R.id.marketplaceFriendsBtn)

        marketplaceFriendsBtn.setOnClickListener {
            val intent = Intent(this, FriendsActivity::class.java)
            startActivity(intent)
            finish()
        }

        val marketplaceNotificationsBtn = findViewById<View>(R.id.marketplaceNotificationsBtn)

        marketplaceNotificationsBtn.setOnClickListener {
            val intent = Intent(this, NotificationsActivity::class.java)
            startActivity(intent)
            finish()
        }

        val marketplaceMenuBtn = findViewById<View>(R.id.marketplaceMenuBtn)
        marketplaceMenuBtn?.setOnClickListener {
            val intent = Intent(this, MenuActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}