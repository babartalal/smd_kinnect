package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ahmadtalal.i210734_i210819.databinding.ItemChatListBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ChatListAdapter(
    private val context: Context,
    private var conversationList: List<ChatConversation>
) : RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

    private val dbRef = FirebaseDatabase.getInstance().reference

    fun updateData(newList: List<ChatConversation>) {
        conversationList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatListBinding.inflate(LayoutInflater.from(context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = conversationList[position]
        holder.bind(chat)
    }

    override fun getItemCount(): Int = conversationList.size

    inner class ChatViewHolder(private val binding: ItemChatListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatConversation) {
            val recipientId = chat.recipientId
            if (recipientId.isEmpty()) return

            // Unread & Last Message
            val relativeTime = if (chat.timestamp > 0) {
                DateUtils.getRelativeTimeSpanString(
                    chat.timestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            } else ""

            val previewMsg = if (relativeTime.isNotEmpty()) "${chat.lastMessage} · $relativeTime" else chat.lastMessage
            binding.chatLastMessage.text = previewMsg

            if (chat.unread) {
                binding.chatUnreadBadge.visibility = View.VISIBLE
            } else {
                binding.chatUnreadBadge.visibility = View.GONE
            }

            // Fetch Recipient User Info & Online Status
            dbRef.child("users").child(recipientId)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) return

                        val user = snapshot.getValue(User::class.java) ?: return
                        val name = user.name.ifEmpty {
                            "${user.firstName} ${user.lastName}".trim().ifEmpty { user.username }
                        }

                        binding.chatRecipientName.text = name

                        // Presence (isOnline)
                        if (user.isOnline) {
                            binding.chatOnlineDot.visibility = View.VISIBLE
                        } else {
                            binding.chatOnlineDot.visibility = View.GONE
                        }

                        // Avatar
                        if (user.profilePicBase64.isNotEmpty()) {
                            try {
                                val imageBytes = Base64.decode(user.profilePicBase64, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                binding.chatAvatarImage.visibility = View.VISIBLE
                                binding.chatAvatarInitials.visibility = View.GONE
                                Glide.with(context).load(bitmap).into(binding.chatAvatarImage)
                            } catch (e: Exception) {
                                showInitials(name)
                            }
                        } else {
                            showInitials(name)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })

            binding.root.setOnClickListener {
                val intent = Intent(context, ChatActivity::class.java)
                intent.putExtra("recipientId", recipientId)
                context.startActivity(intent)
            }
        }

        private fun showInitials(name: String) {
            binding.chatAvatarImage.visibility = View.GONE
            binding.chatAvatarInitials.visibility = View.VISIBLE
            val initials = name.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2)
                .joinToString("")
                .uppercase()
            binding.chatAvatarInitials.text = initials.ifEmpty { "U" }
        }
    }
}
