package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SearchActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        val searchBackBtn = findViewById<ImageButton>(R.id.searchBackBtn)
        searchBackBtn.setOnClickListener {
            finish()
        }

        val searchResultPerson1 = findViewById<View>(R.id.searchResultPerson1)
        searchResultPerson1?.setOnClickListener {
            val intent = Intent(this, OtherProfileActivity::class.java)
            startActivity(intent)
        }

        val searchResultPerson2 = findViewById<View>(R.id.searchResultPerson2)
        searchResultPerson2?.setOnClickListener {
            val intent = Intent(this, OtherProfileActivity::class.java)
            startActivity(intent)
        }
    }
}