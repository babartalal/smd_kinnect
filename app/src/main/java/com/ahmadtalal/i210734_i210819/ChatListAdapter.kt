package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ahmadtalal.i210734_i210819.databinding.ItemChatListBinding

class ChatListAdapter(
    private val context: Context,
    private var conversationList: List<ChatConversation>
) : RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

    /** False while showing the offline copy, where "online" dots would be stale. */
    private var showPresence = true

    /** Avatar circle colours from the design kit; one is picked per name. */
    private val avatarBackgrounds = intArrayOf(
        R.drawable.k_bg_circle_maroon, R.drawable.k_bg_circle_purple, R.drawable.k_bg_circle_green,
        R.drawable.k_bg_circle_indigo, R.drawable.k_bg_circle_brown, R.drawable.k_bg_circle_slate
    )

    fun updateData(newList: List<ChatConversation>, showPresence: Boolean = true) {
        // Copy, because the caller reuses and clears its own list.
        conversationList = newList.toList()
        this.showPresence = showPresence
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
            binding.chatUnreadBadge.visibility = if (chat.unread) View.VISIBLE else View.GONE
            // Unread conversations show their preview in bold, dark text.
            binding.chatLastMessage.setTypeface(null, if (chat.unread) Typeface.BOLD else Typeface.NORMAL)
            binding.chatLastMessage.setTextColor(
                ContextCompat.getColor(context, if (chat.unread) R.color.k_text_primary else R.color.k_text_secondary)
            )

            // Recipient info & online status come with the conversation row.
            val user = chat.user
            val name = user?.displayName().orEmpty()
            binding.chatRecipientName.text = name
            binding.chatOnlineDot.visibility =
                if (showPresence && user?.isOnline == true) View.VISIBLE else View.GONE
            bindAvatar(user?.profilePic.orEmpty(), name, binding.chatAvatarImage, binding.chatAvatarInitials)
            // Round photo, and a per-person avatar colour like the design.
            binding.chatAvatarImage.clipToOutline = true
            binding.chatAvatarInitials.setBackgroundResource(
                avatarBackgrounds[(name.hashCode() and Int.MAX_VALUE) % avatarBackgrounds.size]
            )

            binding.root.setOnClickListener {
                val intent = Intent(context, ChatActivity::class.java)
                intent.putExtra("recipientId", recipientId)
                context.startActivity(intent)
            }
        }
    }
}
