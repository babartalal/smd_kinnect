package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class CameraActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_camera)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Round the preview's corners (the XML attribute only works on Android 12+).
        findViewById<View>(R.id.cameraFrame).clipToOutline = true

        val cancelCameraBtn =findViewById<ImageButton>(R.id.cancelCameraBtn)
        cancelCameraBtn.setOnClickListener {
            finish()
        }

        val shutterBtn = findViewById<View>(R.id.shutterBtn)
        shutterBtn?.setOnClickListener {
            val intent = Intent(this, StoryEditorActivity::class.java)
            startActivity(intent)
        }
    }
}