package com.ahmadtalal.i210734_i210819

import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
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
                image = binding.messageImageView, videoTile = binding.messageVideoTile,
                fileRow = binding.messageFileRow, fileName = binding.messageFileNameTxt,
                heartBadge = binding.heartReactionBadge
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
                image = binding.messageImageView, videoTile = binding.messageVideoTile,
                fileRow = binding.messageFileRow, fileName = binding.messageFileNameTxt,
                heartBadge = binding.heartReactionBadge
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
        videoTile: View,
        fileRow: View,
        fileName: TextView,
        heartBadge: View
    ) {
        body.text = msg.text
        // Photo-only messages show just the rounded image tile, without an empty bubble.
        body.visibility = if (msg.text.isEmpty()) View.GONE else View.VISIBLE
        time.text = formatTime(msg.timestamp)
        // A message written offline shows "Sending..." until it has synced.
        editedLabel.visibility = if (msg.isEdited || msg.pending) View.VISIBLE else View.GONE
        (editedLabel as? TextView)?.text = if (msg.pending) "Sending..." else "Edited"

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

        // The attachment, if any: a photo tile, a video tile or a file row.
        val kind = msg.mediaKind
        image.visibility = if (kind == "image") View.VISIBLE else View.GONE
        videoTile.visibility = if (kind == "video") View.VISIBLE else View.GONE
        fileRow.visibility = if (kind == "file") View.VISIBLE else View.GONE
        if (kind == "image") {
            image.clipToOutline = true // round the corners to the tile background
            image.loadRemote(msg.image)
        }
        fileName.text = msg.fileName.ifEmpty { "File" }
        // A tap plays the video in the app, or opens the file from its server URL.
        videoTile.setOnClickListener { MediaOpener.playVideo(context, msg.image) }
        fileRow.setOnClickListener { MediaOpener.openFile(context, msg.image) }

        heartBadge.visibility = if (msg.heartReaction) View.VISIBLE else View.GONE

        // A message still waiting in the offline queue has no server id yet,
        // so it cannot be replied to, edited or reacted to.
        if (msg.pending || msg.messageId.isEmpty()) {
            root.setOnClickListener(null)
            root.setOnLongClickListener(null)
            videoTile.setOnLongClickListener(null)
            fileRow.setOnLongClickListener(null)
            return
        }

        root.setOnClickListener {
            onReplyRequested(msg)
        }
        val showOptions = View.OnLongClickListener {
            showOptionsDialog(msg, isOwnMessage)
            true
        }
        root.setOnLongClickListener(showOptions)
        // The tiles take their own taps, so they need the long-press menu too.
        videoTile.setOnLongClickListener(showOptions)
        fileRow.setOnLongClickListener(showOptions)
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
                    heartActionText -> change("messages/react.php", msg.messageId, mapOf("message_id" to msg.messageId)) {
                        it.put("heartReaction", !msg.heartReaction)
                    }
                    "Reply" -> onReplyRequested(msg)
                    "Edit Message" -> showEditDialog(msg)
                    "Delete Message" -> change("messages/delete.php", msg.messageId, mapOf("message_id" to msg.messageId)) {
                        it.put("text", "This message was deleted.").put("image", "").put("mediaType", "").put("fileName", "")
                    }
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
                    change("messages/edit.php", msg.messageId, mapOf("message_id" to msg.messageId, "text" to newText)) {
                        it.put("text", newText).put("isEdited", true)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Send a change to the server, then let the chat reload to show it.
     * Offline, the change is queued in pending_actions and [offlineChange]
     * applies it to the copy in SQLite so it shows straight away.
     */
    private fun change(
        endpoint: String,
        messageId: String,
        body: Map<String, Any?>,
        offlineChange: (org.json.JSONObject) -> Unit
    ) {
        Api.post(endpoint, body, queueIfOffline = true) { result ->
            when {
                result.ok -> onChanged()
                result.queued -> {
                    LocalDb.get().patchMessage(messageId, offlineChange)
                    onChanged()
                }
                else -> Toast.makeText(context, result.error, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** Opens chat attachments: videos in a full-screen player inside the app, files in the app that handles them. */
object MediaOpener {

    /**
     * Stream a video from the server ("uploads/abc.mp4") in a full-screen
     * dialog. Tap the video to pause or resume, press back to close. If the
     * built-in player cannot play it, it is handed to another app instead.
     */
    fun playVideo(context: Context, path: String) {
        if (path.isEmpty()) return
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
        }
        val status = TextView(context).apply {
            text = "Loading video..."
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val video = VideoView(context)
        container.addView(
            status,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        container.addView(
            video,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        dialog.setContentView(
            container,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        video.setOnPreparedListener {
            status.visibility = View.GONE
            video.start()
        }
        video.setOnErrorListener { _, _, _ ->
            dialog.dismiss()
            openFile(context, path)
            true
        }
        video.setOnClickListener {
            if (video.isPlaying) video.pause() else video.start()
        }
        dialog.setOnDismissListener { video.stopPlayback() }

        video.setVideoURI(Uri.parse(Api.imageUrl(path)))
        dialog.show()
    }

    /** Open (or download) a file from its server URL with whichever app the phone offers. */
    fun openFile(context: Context, path: String) {
        if (path.isEmpty()) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Api.imageUrl(path))))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this phone can open this file", Toast.LENGTH_SHORT).show()
        }
    }
}
