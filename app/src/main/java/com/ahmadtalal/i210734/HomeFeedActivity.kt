package com.ahmadtalal.i210734

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class HomeFeedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_feed)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        val likeBtn = findViewById<Button>(R.id.likeBtn)
        val reactionPicker = findViewById<LinearLayout>(R.id.reactionPicker)
        val clickShield = findViewById<View>(R.id.clickShield)

        reactionPicker.visibility = View.GONE

        likeBtn.setOnClickListener {

            reactionPicker.visibility = View.VISIBLE

            true
        }


        clickShield.setOnClickListener {
            reactionPicker.visibility = View.GONE
            clickShield.visibility = View.GONE
        }

        val happyReactionBtn = findViewById<ImageButton>(R.id.happyReaction)

        happyReactionBtn.setOnClickListener {
            reactionPicker.visibility = View.GONE
        }

        val wowReactionBtn = findViewById<ImageButton>(R.id.wowReaction)

        wowReactionBtn.setOnClickListener {
            reactionPicker.visibility = View.GONE
        }

        val sadReactionBtn = findViewById<ImageButton>(R.id.sadReaction)

        sadReactionBtn.setOnClickListener {
            reactionPicker.visibility = View.GONE
        }

        val angryReactionBtn = findViewById<ImageButton>(R.id.angryReaction)

        angryReactionBtn.setOnClickListener {
            reactionPicker.visibility = View.GONE
        }
    }
}