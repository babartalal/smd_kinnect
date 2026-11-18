package com.ahmadtalal.i210734_i210819

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.ahmadtalal.i210734_i210819.databinding.ActivityStoryViewerBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class StoryViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoryViewerBinding
    private val dbRef = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityStoryViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.closeStoryBtn.setOnClickListener { finish() }

        val storyId = intent.getStringExtra("storyId").orEmpty()
        val userId = intent.getStringExtra("userId").orEmpty()

        if (userId.isNotEmpty() && storyId.isNotEmpty()) {
            loadStory(userId, storyId)
        } else if (userId.isNotEmpty()) {
            loadLatestStory(userId)
        }
    }

    private fun loadStory(userId: String, storyId: String) {
        dbRef.child("stories").child(userId).child(storyId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val story = snapshot.getValue(Story::class.java)
                    if (story != null) {
                        renderStory(story, userId)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadLatestStory(userId: String) {
        dbRef.child("stories").child(userId)
            .limitToLast(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val story = child.getValue(Story::class.java)
                        if (story != null) {
                            renderStory(story, userId)
                            return
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun renderStory(story: Story, userId: String) {
        if (story.imageBase64.isNotEmpty()) {
            try {
                val imageBytes = Base64.decode(story.imageBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                Glide.with(this).load(bitmap).into(binding.storyMedia)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
