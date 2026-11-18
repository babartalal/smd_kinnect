package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmadtalal.i210734_i210819.databinding.ActivityFriendsBinding
import org.json.JSONArray

class FriendsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendsBinding
    private lateinit var requestAdapter: FriendRequestAdapter
    private lateinit var friendsAdapter: UserAdapter

    private val friendRequestsList = mutableListOf<User>()
    private val yourFriendsList = mutableListOf<User>()

    /** People who sent me a friend request. */
    private val requestsQuery = LiveQuery("friends/requests.php", intervalMs = 5000L) { result ->
        friendRequestsList.clear()
        friendRequestsList.addAll(parseUsers(result.arr()))
        binding.friendRequestsCountTxt.text = "${friendRequestsList.size}"
        requestAdapter.updateData(friendRequestsList)
    }

    /** My friends. */
    private val friendsQuery = LiveQuery("friends/list.php", intervalMs = 5000L) { result ->
        yourFriendsList.clear()
        yourFriendsList.addAll(parseUsers(result.arr()))
        friendsAdapter.updateData(yourFriendsList)
    }

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
    }

    override fun onStart() {
        super.onStart()
        if (!Session.isLoggedIn) return
        requestsQuery.start()
        friendsQuery.start()
    }

    override fun onStop() {
        super.onStop()
        requestsQuery.stop()
        friendsQuery.stop()
    }

    private fun parseUsers(array: JSONArray): List<User> =
        (0 until array.length()).map { User.fromJson(array.getJSONObject(it)) }

    private fun reloadLists() {
        requestsQuery.refresh()
        friendsQuery.refresh()
    }

    private fun setupRecyclerViews() {
        requestAdapter = FriendRequestAdapter(this, friendRequestsList) { reloadLists() }
        binding.friendRequestsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.friendRequestsRecyclerView.adapter = requestAdapter

        friendsAdapter = UserAdapter(this, yourFriendsList) { reloadLists() }
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
}
