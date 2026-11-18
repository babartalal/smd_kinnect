package com.ahmadtalal.i210734_i210819

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ahmadtalal.i210734_i210819.databinding.ItemMessageIncomingBinding
import com.ahmadtalal.i210734_i210819.databinding.ItemMessageOutgoingBinding
import com.ahmadtalal.i210734_i210819.databinding.ItemMessageSystemBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.Date

class MessageAdapter(
    private val context: Context,
    private var messageList: List<Message>,
    private val onReplyRequested: (Message) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_OUTGOING = 1
        private const val TYPE_INCOMING = 2
        private const val TYPE_SYSTEM = 3
    }

    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val dbRef = FirebaseDatabase.getInstance().reference

    fun updateData(newList: List<Message>) {
        messageList = newList
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
            is IncomingViewHolder -> holder.bind(msg)
            is SystemViewHolder -> holder.bind(msg)
        }
    }

    override fun getItemCount(): Int = messageList.size

    inner class OutgoingViewHolder(private val binding: ItemMessageOutgoingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: Message) {
            binding.messageBodyTxt.text = msg.text
            binding.messageTimeTxt.text = formatTime(msg.timestamp)

            if (msg.isEdited) {
                binding.editedLabelTxt.visibility = View.VISIBLE
            } else {
                binding.editedLabelTxt.visibility = View.GONE
            }

            if (msg.replyToText.isNotEmpty()) {
                binding.replyBubbleTxt.visibility = View.VISIBLE
                binding.replyBubbleTxt.text = "Replying: ${msg.replyToText}"
            } else {
                binding.replyBubbleTxt.visibility = View.GONE
            }

            if (msg.imageBase64.isNotEmpty()) {
                try {
                    val imageBytes = Base64.decode(msg.imageBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    binding.messageImageView.visibility = View.VISIBLE
                    Glide.with(context).load(bitmap).into(binding.messageImageView)
                } catch (e: Exception) {
                    binding.messageImageView.visibility = View.GONE
                }
            } else {
                binding.messageImageView.visibility = View.GONE
            }

            if (msg.heartReaction) {
                binding.heartReactionBadge.visibility = View.VISIBLE
            } else {
                binding.heartReactionBadge.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onReplyRequested(msg)
            }

            binding.root.setOnLongClickListener {
                showOptionsDialog(msg, isOwnMessage = true)
                true
            }
        }
    }

    inner class IncomingViewHolder(private val binding: ItemMessageIncomingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: Message) {
            binding.messageBodyTxt.text = msg.text
            binding.messageTimeTxt.text = formatTime(msg.timestamp)

            if (msg.isEdited) {
                binding.editedLabelTxt.visibility = View.VISIBLE
            } else {
                binding.editedLabelTxt.visibility = View.GONE
            }

            if (msg.replyToText.isNotEmpty()) {
                binding.replyBubbleTxt.visibility = View.VISIBLE
                binding.replyBubbleTxt.text = "Replying: ${msg.replyToText}"
            } else {
                binding.replyBubbleTxt.visibility = View.GONE
            }

            if (msg.imageBase64.isNotEmpty()) {
                try {
                    val imageBytes = Base64.decode(msg.imageBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    binding.messageImageView.visibility = View.VISIBLE
                    Glide.with(context).load(bitmap).into(binding.messageImageView)
                } catch (e: Exception) {
                    binding.messageImageView.visibility = View.GONE
                }
            } else {
                binding.messageImageView.visibility = View.GONE
            }

            if (msg.heartReaction) {
                binding.heartReactionBadge.visibility = View.VISIBLE
            } else {
                binding.heartReactionBadge.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onReplyRequested(msg)
            }

            binding.root.setOnLongClickListener {
                showOptionsDialog(msg, isOwnMessage = false)
                true
            }
        }
    }

    inner class SystemViewHolder(private val binding: ItemMessageSystemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: Message) {
            binding.systemMessageTxt.text = msg.text
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

        // 5-MINUTE EDIT & DELETE RULE for OWN message
        val isWithin5Minutes = (System.currentTimeMillis() - msg.timestamp) <= 300000L
        if (isOwnMessage && isWithin5Minutes) {
            options.add("Edit Message")
            options.add("Delete Message")
        }

        AlertDialog.Builder(context)
            .setTitle("Message Options")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    heartActionText -> toggleHeartReaction(msg)
                    "Reply" -> onReplyRequested(msg)
                    "Edit Message" -> showEditDialog(msg)
                    "Delete Message" -> deleteMessage(msg)
                }
            }
            .show()
    }

    private fun toggleHeartReaction(msg: Message) {
        if (msg.chatId.isEmpty() || msg.messageId.isEmpty()) return
        val msgRef = dbRef.child("messages").child(msg.chatId).child(msg.messageId).child("heartReaction")
        msgRef.setValue(!msg.heartReaction)
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
                    val msgRef = dbRef.child("messages").child(msg.chatId).child(msg.messageId)
                    val updates = hashMapOf<String, Any>(
                        "text" to newText,
                        "isEdited" to true
                    )
                    msgRef.updateChildren(updates)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMessage(msg: Message) {
        if (msg.chatId.isEmpty() || msg.messageId.isEmpty()) return
        val msgRef = dbRef.child("messages").child(msg.chatId).child(msg.messageId)
        val updates = hashMapOf<String, Any>(
            "text" to "This message was deleted.",
            "imageBase64" to ""
        )
        msgRef.updateChildren(updates)
    }
}
