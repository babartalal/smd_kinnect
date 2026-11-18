package com.ahmadtalal.i210734_i210819

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmadtalal.i210734_i210819.databinding.ActivityHomeFeedBinding

class HomeFeedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeFeedBinding
    private lateinit var postAdapter: PostAdapter
    private lateinit var storyAdapter: StoryAdapter

    private val feedPostList = mutableListOf<Post>()
    private val storyItemList = mutableListOf<StoryModel>()

    private val callWatcher = IncomingCallWatcher(this)

    /** Posts by me and my friends, newest first (filtered and sorted by the server). */
    private val postsQuery = LiveQuery("posts/feed.php", intervalMs = 5000L) { result ->
        val posts = result.arr()
        feedPostList.clear()
        for (i in 0 until posts.length()) {
            feedPostList.add(Post.fromJson(posts.getJSONObject(i)))
        }
        postAdapter.updateData(feedPostList)
    }

    /** Stories from the last 24 hours by me and my friends. */
    private val storiesQuery = LiveQuery("stories/feed.php", intervalMs = 8000L) { result ->
        val stories = result.arr()
        storyItemList.clear()
        storyItemList.add(StoryModel(isCreateStory = true))
        for (i in 0 until stories.length()) {
            val json = stories.getJSONObject(i)
            storyItemList.add(
                StoryModel(
                    story = Story.fromJson(json),
                    author = json.optJSONObject("author")?.let { User.fromJson(it) },
                    isCreateStory = false
                )
            )
        }
        storyAdapter.updateData(storyItemList)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHomeFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerViews()
        setupClickListeners()
        showCurrentUser()
        checkNotificationPermission()
    }

    /** UI only: my initials/picture in the composer and on the "Create story" card. */
    private fun showCurrentUser() {
        if (!Session.isLoggedIn) return

        Api.get("users/get.php") { result ->
            if (!result.ok || isFinishing || isDestroyed) return@get

            val me = User.fromJson(result.obj())
            val name = me.displayName()
            if (name.isEmpty()) return@get

            binding.homeFeedProfileAvatar.clipToOutline = true
            bindAvatar(me.profilePic, name, binding.homeFeedProfileAvatar, binding.homeFeedProfileInitials)
            val firstName = me.firstName.ifEmpty { name.substringBefore(' ') }
            binding.woym.hint = "What's on your mind, $firstName?"
            storyAdapter.setCurrentUserName(name)
        }
    }

    override fun onStart() {
        super.onStart()
        if (!Session.isLoggedIn) return
        postsQuery.start()
        storiesQuery.start()
        callWatcher.start()
    }

    override fun onStop() {
        super.onStop()
        postsQuery.stop()
        storiesQuery.stop()
        callWatcher.stop()
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupRecyclerViews() {
        postAdapter = PostAdapter(this, feedPostList)
        binding.postsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.postsRecyclerView.adapter = postAdapter

        // The "Create Story" card is always there, even before stories load.
        storyItemList.add(StoryModel(isCreateStory = true))
        storyAdapter = StoryAdapter(this, storyItemList)
        binding.storiesRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.storiesRecyclerView.adapter = storyAdapter
    }

    private fun setupClickListeners() {
        binding.friendsBtn.setOnClickListener {
            startActivity(Intent(this, FriendsActivity::class.java))
        }

        binding.woym.setOnClickListener {
            startActivity(Intent(this, CreatePostActivity::class.java))
        }

        binding.homeFeedProfileAvatar.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.homeFeedProfileInitials.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.homeFeedSearchBtn.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        binding.homeFeedChatsBtn.setOnClickListener {
            startActivity(Intent(this, ChatsActivity::class.java))
        }

        binding.homeFeedMarketplaceBtn.setOnClickListener {
            startActivity(Intent(this, MarketplaceActivity::class.java))
        }

        binding.homeFeedNotificationsBtn.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        binding.homeFeedMenuBtn.setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
        }

        binding.homeFeedPhotoPickerBtn.setOnClickListener {
            startActivity(Intent(this, CreatePostActivity::class.java))
        }
    }
}
