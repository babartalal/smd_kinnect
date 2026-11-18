package com.ahmadtalal.i210734_i210819

import android.os.Bundle
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

        binding.closeMyStoryBtn.setOnClickListener { finish() }

        if (!Session.isLoggedIn) return

        // With a story id we open that story; without one, my latest story.
        val storyId = intent.getStringExtra("storyId").orEmpty()
        val params = if (storyId.isNotEmpty()) mapOf("story_id" to storyId) else emptyMap()

        Api.get("stories/get.php", params) { result ->
            if (!result.ok || isFinishing || isDestroyed) return@get
            val story = Story.fromJson(result.obj())
            if (story.image.isNotEmpty()) {
                binding.myStoryMedia.loadRemote(story.image)
            }
        }
    }
}
