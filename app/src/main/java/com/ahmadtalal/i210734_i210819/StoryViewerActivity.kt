package com.ahmadtalal.i210734_i210819

import android.os.Bundle
import android.view.View
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

        // Round the story's corners (the XML attribute only works on Android 12+).
        binding.storyFrame.clipToOutline = true

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
            binding.storyTextLabel.text = story.textLabel
            binding.storyTextLabel.visibility = if (story.textLabel.isEmpty()) View.GONE else View.VISIBLE
            if (story.timestamp > 0) binding.storyTime.text = storyAge(story.timestamp)
            if (story.userId.isNotEmpty()) showAuthor(story.userId)
        }
    }

    /** Shows the story author's name and initials in the header. */
    private fun showAuthor(userId: String) {
        Api.get("users/get.php", mapOf("user_id" to userId)) { result ->
            if (!result.ok || isFinishing || isDestroyed) return@get
            val name = User.fromJson(result.obj()).displayName()
            if (name.isNotEmpty()) {
                binding.storyAuthorName.text = name
                binding.storyAuthorInitials.text = initialsOf(name)
            }
        }
    }

    /** "5h"-style age of a story; the timestamp may be in seconds or milliseconds. */
    private fun storyAge(timestamp: Long): String {
        val millis = if (timestamp < 100_000_000_000L) timestamp * 1000 else timestamp
        val minutes = ((System.currentTimeMillis() - millis) / 60_000).coerceAtLeast(0)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m"
            minutes < 60 * 24 -> "${minutes / 60}h"
            else -> "${minutes / (60 * 24)}d"
        }
    }
}
