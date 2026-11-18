package com.ahmadtalal.i210734_i210819

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmadtalal.i210734_i210819.databinding.ActivityYourStoryBinding

class YourStoryActivity : AppCompatActivity() {

    /** True while a video story is on screen, so it can pause and resume with the screen. */
    private var showingVideo = false

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
            showStoryMedia(story)
            binding.myStoryTextLabel.text = story.textLabel
            binding.myStoryTextLabel.visibility = if (story.textLabel.isEmpty()) View.GONE else View.VISIBLE
            val audience = if (story.audience == "close_friends") "Close friends" else "Public"
            binding.myStoryMeta.text =
                if (story.timestamp > 0) "${storyAge(story.timestamp)} · $audience" else audience
        }
    }

    override fun onResume() {
        super.onResume()
        if (showingVideo) binding.myStoryVideo.start()
    }

    override fun onPause() {
        super.onPause()
        binding.myStoryVideo.pause()
    }

    /** A picture story fills the frame as an image; a video story plays there in a loop. */
    private fun showStoryMedia(story: Story) {
        if (story.image.isEmpty()) return
        if (!story.isVideo) {
            showingVideo = false
            binding.myStoryVideo.visibility = View.GONE
            binding.myStoryMedia.loadRemote(story.image)
            return
        }
        showingVideo = true
        binding.myStoryVideo.visibility = View.VISIBLE
        binding.myStoryVideo.setOnPreparedListener { player ->
            player.isLooping = true
            // The sample picture underneath is only needed until the video is ready.
            binding.myStoryMedia.visibility = View.INVISIBLE
            binding.myStoryVideo.start()
        }
        binding.myStoryVideo.setOnErrorListener { _, _, _ ->
            showingVideo = false
            binding.myStoryVideo.visibility = View.GONE
            binding.myStoryMedia.visibility = View.VISIBLE
            Toast.makeText(this, "This video story could not be played", Toast.LENGTH_SHORT).show()
            true
        }
        // Streamed straight from the server's uploads folder.
        binding.myStoryVideo.setVideoURI(Uri.parse(Api.imageUrl(story.image)))
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
