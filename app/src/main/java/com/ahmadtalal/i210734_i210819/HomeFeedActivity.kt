package com.ahmadtalal.i210734_i210819

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmadtalal.i210734_i210819.databinding.ActivityHomeFeedBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class HomeFeedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeFeedBinding
    private lateinit var postAdapter: PostAdapter
    private lateinit var storyAdapter: StoryAdapter

    private val feedPostList = mutableListOf<Post>()
    private val storyItemList = mutableListOf<StoryModel>()
    private var incomingCallDialog: AlertDialog? = null

    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val dbRef = FirebaseDatabase.getInstance().reference

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
        loadHomeFeedPosts()
        loadHomeFeedStories()
        listenForIncomingCalls()
    }

    private fun setupRecyclerViews() {
        postAdapter = PostAdapter(this, feedPostList)
        binding.postsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.postsRecyclerView.adapter = postAdapter

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

    private fun listenForIncomingCalls() {
        if (currentUserId.isEmpty()) return

        dbRef.child("calls").child(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        incomingCallDialog?.dismiss()
                        incomingCallDialog = null
                        return
                    }

                    val callerName = snapshot.child("callerName").getValue(String::class.java) ?: "Someone"
                    val channelName = snapshot.child("channelName").getValue(String::class.java).orEmpty()
                    val callType = snapshot.child("type").getValue(String::class.java) ?: "audio"
                    val status = snapshot.child("status").getValue(String::class.java) ?: "ringing"
                    val callerId = snapshot.child("callerId").getValue(String::class.java).orEmpty()

                    if (status == "ringing" && incomingCallDialog == null) {
                        incomingCallDialog = AlertDialog.Builder(this@HomeFeedActivity)
                            .setTitle("Incoming ${callType.replaceFirstChar { it.uppercase() }} Call")
                            .setMessage("$callerName is calling you...")
                            .setPositiveButton("Accept") { _, _ ->
                                dbRef.child("calls").child(currentUserId).child("status").setValue("accepted")
                                incomingCallDialog = null

                                val intent = Intent(this@HomeFeedActivity, VoiceCallActivity::class.java)
                                intent.putExtra("channelName", channelName)
                                intent.putExtra("type", callType)
                                intent.putExtra("isCaller", false)
                                intent.putExtra("recipientId", callerId)
                                startActivity(intent)
                            }
                            .setNegativeButton("Decline") { _, _ ->
                                dbRef.child("calls").child(currentUserId).child("status").setValue("ended")
                                dbRef.child("calls").child(currentUserId).removeValue()
                                incomingCallDialog = null
                            }
                            .setCancelable(false)
                            .create()
                        incomingCallDialog?.show()
                    } else if (status == "ended") {
                        incomingCallDialog?.dismiss()
                        incomingCallDialog = null
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadHomeFeedPosts() {
        if (currentUserId.isEmpty()) return

        dbRef.child("friends").child(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(friendsSnap: DataSnapshot) {
                    val friendIds = friendsSnap.children.mapNotNull { it.key }.toSet()
                    val allowedAuthorIds = friendIds + currentUserId

                    dbRef.child("posts")
                        .addValueEventListener(object : ValueEventListener {
                            override fun onDataChange(postsSnap: DataSnapshot) {
                                feedPostList.clear()
                                for (child in postsSnap.children) {
                                    val post = child.getValue(Post::class.java)
                                    if (post != null && allowedAuthorIds.contains(post.authorId)) {
                                        feedPostList.add(post)
                                    }
                                }

                                feedPostList.sortByDescending { it.timestamp }
                                postAdapter.updateData(feedPostList)
                            }

                            override fun onCancelled(error: DatabaseError) {}
                        })
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadHomeFeedStories() {
        if (currentUserId.isEmpty()) return

        dbRef.child("friends").child(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(friendsSnap: DataSnapshot) {
                    val friendIds = friendsSnap.children.mapNotNull { it.key }.toSet()
                    val allowedUserIds = friendIds + currentUserId

                    dbRef.child("stories")
                        .addValueEventListener(object : ValueEventListener {
                            override fun onDataChange(storiesSnap: DataSnapshot) {
                                storyItemList.clear()

                                storyItemList.add(StoryModel(isCreateStory = true))

                                val currentTime = System.currentTimeMillis()
                                val activeStories = mutableListOf<Pair<Story, User?>>()

                                val targetUserIds = allowedUserIds.filter { storiesSnap.hasChild(it) }

                                if (targetUserIds.isEmpty()) {
                                    storyAdapter.updateData(storyItemList)
                                    return
                                }

                                var fetchedUserCount = 0
                                for (userId in targetUserIds) {
                                    val userStoriesSnap = storiesSnap.child(userId)

                                    dbRef.child("users").child(userId)
                                        .addListenerForSingleValueEvent(object : ValueEventListener {
                                            override fun onDataChange(userSnap: DataSnapshot) {
                                                val user = userSnap.getValue(User::class.java)

                                                for (sSnap in userStoriesSnap.children) {
                                                    val story = sSnap.getValue(Story::class.java)
                                                    if (story != null) {
                                                        val age = currentTime - story.timestamp
                                                        if (age in 0..86400000L) {
                                                            activeStories.add(Pair(story, user))
                                                        }
                                                    }
                                                }

                                                fetchedUserCount++
                                                if (fetchedUserCount == targetUserIds.size) {
                                                    activeStories.sortByDescending { it.first.timestamp }
                                                    for (pair in activeStories) {
                                                        storyItemList.add(
                                                            StoryModel(
                                                                story = pair.first,
                                                                author = pair.second,
                                                                isCreateStory = false
                                                            )
                                                        )
                                                    }
                                                    storyAdapter.updateData(storyItemList)
                                                }
                                            }

                                            override fun onCancelled(error: DatabaseError) {
                                                fetchedUserCount++
                                                if (fetchedUserCount == targetUserIds.size) {
                                                    storyAdapter.updateData(storyItemList)
                                                }
                                            }
                                        })
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {}
                        })
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
