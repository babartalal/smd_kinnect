package com.ahmadtalal.i210734_i210819

import android.app.AlertDialog
import android.content.Context
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.ahmadtalal.i210734_i210819.databinding.ItemMessageIncomingBinding
import com.ahmadtalal.i210734_i210819.databinding.ItemMessageOutgoingBinding
import com.ahmadtalal.i210734_i210819.databinding.ItemMessageSystemBinding
import java.util.Date

class MessageAdapter(
    private val context: Context,
    private var messageList: List<Message>,
    /** Called after a message was edited, deleted or reacted to, so the chat can reload. */
    private val onChanged: () -> Unit = {},
    private val onReplyRequested: (Message) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_OUTGOING = 1
        private const val TYPE_INCOMING = 2
        private const val TYPE_SYSTEM = 3
    }

    private val currentUserId: String = Session.userId

    /** The other person's name, shown as avatar initials and in reply captions (set by the chat screen). */
    var peerName: String = ""

    fun updateData(newList: List<Message>) {
        // Copy, because the caller reuses and clears its own list.
        messageList = newList.toList()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val msg = messageList[position]
        return when {
            msg.isSystemMessage -> TYPE_SYSTEM
            msg.senderId == currentUserId -> TYPE_OUTGOING
            else -> TYPE_INCOMING
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        return when (viewType) {
            TYPE_OUTGOING -> OutgoingViewHolder(ItemMessageOutgoingBinding.inflate(inflater, parent, false))
            TYPE_INCOMING -> IncomingViewHolder(ItemMessageIncomingBinding.inflate(inflater, parent, false))
            else -> SystemViewHolder(ItemMessageSystemBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messageList[position]
        when (holder) {
            is OutgoingViewHolder -> holder.bind(msg)
            is IncomingViewHolder -> holder.bind(msg, position)
            is SystemViewHolder -> holder.bind(msg)
        }
    }

    override fun getItemCount(): Int = messageList.size

    inner class OutgoingViewHolder(private val binding: ItemMessageOutgoingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: Message) {
            bindBubble(
                msg, isOwnMessage = true, root = binding.root,
                body = binding.messageBodyTxt, time = binding.messageTimeTxt,
                editedLabel = binding.editedLabelTxt, replyBubble = binding.replyBubbleTxt,
                replyCaption = binding.replyCaptionTxt,
                image = binding.messageImageView, heartBadge = binding.heartReactionBadge
            )
        }
    }

    inner class IncomingViewHolder(private val binding: ItemMessageIncomingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: Message, position: Int) {
            // Small sender avatar, only beside the last bubble of a run of incoming messages.
            val next = messageList.getOrNull(position + 1)
            val lastOfGroup = next == null || next.isSystemMessage || next.senderId == currentUserId
            binding.incomingAvatarInitials.text = initialsOf(peerName)
            binding.incomingAvatarInitials.visibility = if (lastOfGroup) View.VISIBLE else View.INVISIBLE

            bindBubble(
                msg, isOwnMessage = false, root = binding.root,
                body = binding.messageBodyTxt, time = binding.messageTimeTxt,
                editedLabel = binding.editedLabelTxt, replyBubble = binding.replyBubbleTxt,
                replyCaption = binding.replyCaptionTxt,
                image = binding.messageImageView, heartBadge = binding.heartReactionBadge
            )
        }
    }

    inner class SystemViewHolder(private val binding: ItemMessageSystemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: Message) {
            binding.systemMessageTxt.text = msg.text
        }
    }

    /** Incoming and outgoing bubbles have the same parts, just different layouts. */
    private fun bindBubble(
        msg: Message,
        isOwnMessage: Boolean,
        root: View,
        body: TextView,
        time: TextView,
        editedLabel: View,
        replyBubble: TextView,
        replyCaption: TextView,
        image: ImageView,
        heartBadge: View
    ) {
        body.text = msg.text
        // Photo-only messages show just the rounded image tile, without an empty bubble.
        body.visibility = if (msg.text.isEmpty()) View.GONE else View.VISIBLE
        time.text = formatTime(msg.timestamp)
        editedLabel.visibility = if (msg.isEdited) View.VISIBLE else View.GONE

        if (msg.replyToText.isNotEmpty()) {
            val peerFirstName = peerName.substringBefore(" ")
            replyBubble.visibility = View.VISIBLE
            replyBubble.text = msg.replyToText
            replyCaption.visibility = View.VISIBLE
            replyCaption.text = when {
                peerFirstName.isEmpty() -> if (isOwnMessage) "You replied" else "Replied to you"
                isOwnMessage -> "You replied to $peerFirstName"
                else -> "$peerFirstName replied to you"
            }
        } else {
            replyBubble.visibility = View.GONE
            replyCaption.visibility = View.GONE
        }

        if (msg.image.isNotEmpty()) {
            image.visibility = View.VISIBLE
            image.clipToOutline = true // round the corners to the tile background
            image.loadRemote(msg.image)
        } else {
            image.visibility = View.GONE
        }

        heartBadge.visibility = if (msg.heartReaction) View.VISIBLE else View.GONE

        // A message still waiting in the offline queue has no server id yet,
        // so it cannot be replied to, edited or reacted to.
        if (msg.messageId.isEmpty()) {
            root.setOnClickListener(null)
            root.setOnLongClickListener(null)
            return
        }

        root.setOnClickListener {
            onReplyRequested(msg)
        }
        root.setOnLongClickListener {
            showOptionsDialog(msg, isOwnMessage)
            true
        }
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0) return "Just now"
        return DateFormat.format("h:mm a", Date(timestamp)).toString()
    }

    private fun showOptionsDialog(msg: Message, isOwnMessage: Boolean) {
        val options = mutableListOf<String>()

        val heartActionText = if (msg.heartReaction) "Remove ❤️ Reaction" else "Add ❤️ Reaction"
        options.add(heartActionText)
        options.add("Reply")

        // 5-MINUTE EDIT & DELETE RULE for OWN message (the server enforces it too)
        val isWithin5Minutes = (System.currentTimeMillis() - msg.timestamp) <= 300000L
        if (isOwnMessage && isWithin5Minutes) {
            options.add("Edit Message")
            options.add("Delete Message")
        }

        AlertDialog.Builder(context)
            .setTitle("Message Options")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    heartActionText -> change("messages/react.php", mapOf("message_id" to msg.messageId))
                    "Reply" -> onReplyRequested(msg)
                    "Edit Message" -> showEditDialog(msg)
                    "Delete Message" -> change("messages/delete.php", mapOf("message_id" to msg.messageId))
                }
            }
            .show()
    }

    private fun showEditDialog(msg: Message) {
        val editText = EditText(context)
        editText.setText(msg.text)
        editText.setSelection(editText.text.length)

        AlertDialog.Builder(context)
            .setTitle("Edit Message")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newText = editText.text.toString().trim()
                if (newText.isNotEmpty()) {
                    change("messages/edit.php", mapOf("message_id" to msg.messageId, "text" to newText))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Send a change to the server, then let the chat reload to show it. */
    private fun change(endpoint: String, body: Map<String, Any?>) {
        Api.post(endpoint, body) { result ->
            if (result.ok) {
                onChanged()
            } else {
                Toast.makeText(context, result.error, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
