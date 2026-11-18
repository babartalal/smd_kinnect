package com.ahmadtalal.i210734_i210819

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmadtalal.i210734_i210819.databinding.ActivityYourStoryBinding

class YourStoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityYourStoryBinding

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

        // Round the story's corners (the XML attribute only works on Android 12+).
        binding.storyFrame.clipToOutline = true

        binding.closeMyStoryBtn.setOnClickListener { finish() }

        if (!Session.isLoggedIn) return

        showMyInitials()

        // With a story id we open that story; without one, my latest story.
        val storyId = intent.getStringExtra("storyId").orEmpty()
        val params = if (storyId.isNotEmpty()) mapOf("story_id" to storyId) else emptyMap()

        Api.get("stories/get.php", params) { result ->
            if (!result.ok || isFinishing || isDestroyed) return@get
            val story = Story.fromJson(result.obj())
            if (story.image.isNotEmpty()) {
                binding.myStoryMedia.loadRemote(story.image)
            }
            binding.myStoryTextLabel.text = story.textLabel
            binding.myStoryTextLabel.visibility = if (story.textLabel.isEmpty()) View.GONE else View.VISIBLE
            val audience = if (story.audience == "close_friends") "Close friends" else "Public"
            binding.myStoryMeta.text =
                if (story.timestamp > 0) "${storyAge(story.timestamp)} · $audience" else audience
        }
    }

    /** Shows my initials in the header avatar. */
    private fun showMyInitials() {
        Api.get("users/get.php", mapOf("user_id" to Session.userId)) { result ->
            if (!result.ok || isFinishing || isDestroyed) return@get
            val name = User.fromJson(result.obj()).displayName()
            if (name.isNotEmpty()) binding.myStoryInitials.text = initialsOf(name)
        }
    }

    /** "11h"-style age of a story; the timestamp may be in seconds or milliseconds. */
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
