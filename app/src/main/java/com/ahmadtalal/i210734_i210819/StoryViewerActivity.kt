package com.ahmadtalal.i210734_i210819

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmadtalal.i210734_i210819.databinding.ActivityStoryViewerBinding

class StoryViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoryViewerBinding

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

        // With a story id we open that story; with only a user id, their latest one.
        when {
            storyId.isNotEmpty() -> loadStory(mapOf("story_id" to storyId))
            userId.isNotEmpty() -> loadStory(mapOf("user_id" to userId))
        }
    }

    private fun loadStory(params: Map<String, Any?>) {
        Api.get("stories/get.php", params) { result ->
            if (!result.ok || isFinishing || isDestroyed) return@get
            val story = Story.fromJson(result.obj())
            if (story.image.isNotEmpty()) {
                binding.storyMedia.loadRemote(story.image)
            }
        }
    }
}
