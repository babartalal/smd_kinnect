package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmadtalal.i210734_i210819.databinding.ActivityFriendsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FriendsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendsBinding
    private lateinit var requestAdapter: FriendRequestAdapter
    private lateinit var friendsAdapter: UserAdapter

    private val friendRequestsList = mutableListOf<User>()
    private val yourFriendsList = mutableListOf<User>()

    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val dbRef = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFriendsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerViews()
        setupClickListeners()
        loadFriendRequests()
        loadYourFriends()
    }

    private fun setupRecyclerViews() {
        requestAdapter = FriendRequestAdapter(this, friendRequestsList) {
            loadFriendRequests()
            loadYourFriends()
        }
        binding.friendRequestsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.friendRequestsRecyclerView.adapter = requestAdapter

        friendsAdapter = UserAdapter(this, yourFriendsList)
        binding.yourFriendsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.yourFriendsRecyclerView.adapter = friendsAdapter
    }

    private fun setupClickListeners() {
        binding.friendsSearchBtn.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        binding.homeFeedBtn.setOnClickListener {
            finish()
        }

        binding.friendsMarketplaceBtn.setOnClickListener {
            startActivity(Intent(this, MarketplaceActivity::class.java))
            finish()
        }

        binding.friendsNotificationsBtn.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
            finish()
        }

        binding.friendsMenuBtn.setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
            finish()
        }
    }

    private fun loadFriendRequests() {
        if (currentUserId.isEmpty()) return

        dbRef.child("friend_requests").child(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    friendRequestsList.clear()
                    val senderIds = snapshot.children.mapNotNull { it.key }
                    binding.friendRequestsCountTxt.text = "${senderIds.size}"

                    if (senderIds.isEmpty()) {
                        requestAdapter.updateData(friendRequestsList)
                        return
                    }

                    var loadedCount = 0
                    for (senderId in senderIds) {
                        dbRef.child("users").child(senderId)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(userSnap: DataSnapshot) {
                                    val user = userSnap.getValue(User::class.java)
                                    if (user != null) {
                                        friendRequestsList.add(user)
                                    }
                                    loadedCount++
                                    if (loadedCount == senderIds.size) {
                                        requestAdapter.updateData(friendRequestsList)
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    loadedCount++
                                    if (loadedCount == senderIds.size) {
                                        requestAdapter.updateData(friendRequestsList)
                                    }
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadYourFriends() {
        if (currentUserId.isEmpty()) return

        dbRef.child("friends").child(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    yourFriendsList.clear()
                    val friendIds = snapshot.children.mapNotNull { it.key }

                    if (friendIds.isEmpty()) {
                        friendsAdapter.updateData(yourFriendsList)
                        return
                    }

                    var loadedCount = 0
                    for (friendId in friendIds) {
                        dbRef.child("users").child(friendId)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(userSnap: DataSnapshot) {
                                    val user = userSnap.getValue(User::class.java)
                                    if (user != null) {
                                        yourFriendsList.add(user)
                                    }
                                    loadedCount++
                                    if (loadedCount == friendIds.size) {
                                        friendsAdapter.updateData(yourFriendsList)
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    loadedCount++
                                    if (loadedCount == friendIds.size) {
                                        friendsAdapter.updateData(yourFriendsList)
                                    }
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
