package com.ahmadtalal.i210734_i210819

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmadtalal.i210734_i210819.databinding.ActivityChatsBinding

class ChatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatsBinding
    private lateinit var chatListAdapter: ChatListAdapter

    private val conversationList = mutableListOf<ChatConversation>()

    /**
     * One row per friend with their profile, last message and unread flag,
     * already sorted by the server (most recent conversation first).
     */
    private val chatsQuery = LiveQuery("chats/list.php", intervalMs = 4000L) { result ->
        val chats = result.arr()
        conversationList.clear()
        for (i in 0 until chats.length()) {
            conversationList.add(ChatConversation.fromJson(chats.getJSONObject(i)))
        }
        chatListAdapter.updateData(conversationList, showPresence = !result.fromCache)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.chatsBackBtn.setOnClickListener { finish() }

        setupRecyclerView()
    }

    override fun onStart() {
        super.onStart()
        if (Session.isLoggedIn) chatsQuery.start()
    }

    override fun onStop() {
        super.onStop()
        chatsQuery.stop()
    }

    private fun setupRecyclerView() {
        chatListAdapter = ChatListAdapter(this, conversationList)
        binding.chatsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatsRecyclerView.adapter = chatListAdapter
    }
}
