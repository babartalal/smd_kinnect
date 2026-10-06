package com.ahmadtalal.i210734

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
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

        val marketplaceSearchBtn = findViewById<ImageButton>(R.id.marketplaceSearchBtn)

        marketplaceSearchBtn.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
        }

        val marketplaceHomeBtn = findViewById<ImageButton>(R.id.marketplaceHomeBtn)

        marketplaceHomeBtn.setOnClickListener {
            val intent = Intent(this, HomeFeedActivity::class.java)
            startActivity(intent)
        }


        val marketplaceFriendsBtn = findViewById<ImageButton>(R.id.marketplaceFriendsBtn)

        marketplaceFriendsBtn.setOnClickListener {
            val intent = Intent(this, FriendsActivity::class.java)
            startActivity(intent)
        }
    }
}