package com.ahmadtalal.i210734_i210819

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmadtalal.i210734_i210819.databinding.ActivityChatsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ChatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatsBinding
    private lateinit var chatListAdapter: ChatListAdapter
    private val conversationList = mutableListOf<ChatConversation>()

    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val dbRef = FirebaseDatabase.getInstance().reference

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
        loadActiveChats()
    }

    private fun setupRecyclerView() {
        chatListAdapter = ChatListAdapter(this, conversationList)
        binding.chatsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatsRecyclerView.adapter = chatListAdapter
    }

    private fun loadActiveChats() {
        if (currentUserId.isEmpty()) return

        dbRef.child("friends").child(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val friendIds = snapshot.children.mapNotNull { it.key }
                    if (friendIds.isEmpty()) {
                        conversationList.clear()
                        chatListAdapter.updateData(conversationList)
                        return
                    }

                    val tempMap = mutableMapOf<String, ChatConversation>()
                    var processedCount = 0

                    for (friendId in friendIds) {
                        val chatId = getChatId(currentUserId, friendId)
                        dbRef.child("messages").child(chatId)
                            .limitToLast(1)
                            .addValueEventListener(object : ValueEventListener {
                                override fun onDataChange(msgSnap: DataSnapshot) {
                                    var lastText = "Start a conversation"
                                    var lastTime = 0L

                                    for (child in msgSnap.children) {
                                        val msg = child.getValue(Message::class.java)
                                        if (msg != null) {
                                            lastText = if (msg.imageBase64.isNotEmpty()) "📷 Photo" else msg.text
                                            lastTime = msg.timestamp
                                        }
                                    }

                                    tempMap[friendId] = ChatConversation(
                                        recipientId = friendId,
                                        lastMessage = lastText,
                                        timestamp = lastTime,
                                        unread = false
                                    )

                                    processedCount++
                                    if (processedCount >= friendIds.size) {
                                        conversationList.clear()
                                        conversationList.addAll(tempMap.values.sortedByDescending { it.timestamp })
                                        chatListAdapter.updateData(conversationList)
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    processedCount++
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun getChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }
}
