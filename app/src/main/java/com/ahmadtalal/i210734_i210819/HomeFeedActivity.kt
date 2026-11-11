package com.ahmadtalal.i210734_i210819

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
    private val feedPostList = mutableListOf<Post>()

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

        setupRecyclerView()
        setupClickListeners()
        loadHomeFeedPosts()
    }

    private fun setupRecyclerView() {
        postAdapter = PostAdapter(this, feedPostList)
        binding.postsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.postsRecyclerView.adapter = postAdapter
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

        binding.storyCard2.setOnClickListener {
            startActivity(Intent(this, StoryViewerActivity::class.java))
        }

        binding.viewMyStoryCard.setOnClickListener {
            startActivity(Intent(this, YourStoryActivity::class.java))
        }

        binding.createStoryCard.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
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
}
