package com.ahmadtalal.i210734

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
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

        val mainView = findViewById<View>(R.id.main)
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }

        // Top Header Navigation Buttons
        findViewById<ImageButton>(R.id.homeFeedSearchBtn)?.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.homeFeedChatsBtn)?.setOnClickListener {
            startActivity(Intent(this, ChatsActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.friendsBtn)?.setOnClickListener {
            startActivity(Intent(this, FriendsActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.homeFeedMarketplaceBtn)?.setOnClickListener {
            startActivity(Intent(this, MarketplaceActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.homeFeedNotificationsBtn)?.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.homeFeedMenuBtn)?.setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
        }

        // Post Creation Trigger
        findViewById<EditText>(R.id.woym)?.setOnClickListener {
            startActivity(Intent(this, CreatePostActivity::class.java))
        }

        findViewById<ImageButton>(R.id.homeFeedPhotoPickerBtn)?.setOnClickListener {
            startActivity(Intent(this, PhotoPickerActivity::class.java))
        }

        // Stories Navigation
        findViewById<View>(R.id.createStoryCard)?.setOnClickListener {
            startActivity(Intent(this, StoryEditorActivity::class.java))
        }

        findViewById<View>(R.id.storyCard2)?.setOnClickListener {
            startActivity(Intent(this, StoryViewerActivity::class.java))
        }

        findViewById<View>(R.id.viewMyStoryCard)?.setOnClickListener {
            startActivity(Intent(this, YourStoryActivity::class.java))
        }

        // Reaction Picker Popup Overlay Logic
        val reactionPicker = findViewById<LinearLayout>(R.id.reactionPicker)
        reactionPicker?.visibility = View.GONE

        val likeBtn = findViewById<Button>(R.id.likeBtn)
        likeBtn?.setOnClickListener {
            if (reactionPicker?.visibility == View.VISIBLE) {
                reactionPicker.visibility = View.GONE
            } else {
                reactionPicker?.visibility = View.VISIBLE
            }
        }

        val commentBtn = findViewById<Button>(R.id.commentBtn)
        commentBtn?.setOnClickListener {
            startActivity(Intent(this, CommentsActivity::class.java))
        }

        val hideReactionPicker = View.OnClickListener {
            reactionPicker?.visibility = View.GONE
        }

        findViewById<ImageButton>(R.id.likeReaction)?.setOnClickListener(hideReactionPicker)
        findViewById<ImageButton>(R.id.loveReaction)?.setOnClickListener(hideReactionPicker)
        findViewById<ImageButton>(R.id.happyReaction)?.setOnClickListener(hideReactionPicker)
        findViewById<ImageButton>(R.id.wowReaction)?.setOnClickListener(hideReactionPicker)
        findViewById<ImageButton>(R.id.sadReaction)?.setOnClickListener(hideReactionPicker)
        findViewById<ImageButton>(R.id.angryReaction)?.setOnClickListener(hideReactionPicker)
    }
}