package com.ahmadtalal.i210734_i210819

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.ahmadtalal.i210734_i210819.databinding.ActivityYourStoryBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class YourStoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityYourStoryBinding
    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val dbRef = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityYourStoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.closeMyStoryBtn.setOnClickListener { finish() }

        val storyId = intent.getStringExtra("storyId").orEmpty()
        if (storyId.isNotEmpty()) {
            loadSpecificStory(storyId)
        } else {
            loadMyLatestStory()
        }
    }

    private fun loadSpecificStory(storyId: String) {
        if (currentUserId.isEmpty()) return

        dbRef.child("stories").child(currentUserId).child(storyId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val story = snapshot.getValue(Story::class.java)
                    if (story != null) {
                        renderMyStory(story)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadMyLatestStory() {
        if (currentUserId.isEmpty()) return

        dbRef.child("stories").child(currentUserId)
            .limitToLast(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val story = child.getValue(Story::class.java)
                        if (story != null) {
                            renderMyStory(story)
                            return
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun renderMyStory(story: Story) {
        if (story.imageBase64.isNotEmpty()) {
            try {
                val imageBytes = Base64.decode(story.imageBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                Glide.with(this).load(bitmap).into(binding.myStoryMedia)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
