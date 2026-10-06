package com.ahmadtalal.i210734

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class CreatePostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_post)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val pickPhotoCreatePost = findViewById<LinearLayout>(R.id.pickPhotoCreatePost)
        pickPhotoCreatePost.setOnClickListener {
            val intent = Intent(this, PhotoPickerActivity::class.java)
            startActivity(intent)
        }

        val createPostbackBtn = findViewById<ImageButton>(R.id.createPostbackBtn)
        createPostbackBtn.setOnClickListener {
            val intent = Intent(this, HomeFeedActivity::class.java)
            startActivity(intent)
        }
    }
}