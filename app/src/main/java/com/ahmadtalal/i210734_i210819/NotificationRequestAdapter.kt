package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.ahmadtalal.i210734_i210819.databinding.ItemNotificationRequestBinding
import org.json.JSONObject

/** One incoming friend request: who sent it and when (ms; 0 if unknown). */
data class FriendRequestNotice(val sender: User, val timestamp: Long) {
    companion object {
        /** friends/requests.php returns the sender's user JSON plus `timestamp`. */
        fun fromJson(json: JSONObject): FriendRequestNotice =
            FriendRequestNotice(User.fromJson(json), json.optLong("timestamp"))
    }
}

/**
 * Incoming friend requests shown as rows of the Notifications screen:
 * "<Name> sent you a friend request." with Confirm and Delete buttons.
 */
class NotificationRequestAdapter(
    private val context: Context,
    /** Called after a request was confirmed or deleted, so the screen can reload. */
    private val onRequestHandled: () -> Unit
) : RecyclerView.Adapter<NotificationRequestAdapter.RequestViewHolder>() {

    private var requests: List<FriendRequestNotice> = emptyList()

    fun updateData(newList: List<FriendRequestNotice>) {
        requests = newList.toList()
        notifyDataSetChanged()
    }

    /**
     * The request was answered (or, offline, queued to be answered): take its
     * row away at once, then let the screen reload the list from the server.
     */
    private fun requestHandled(senderId: String) {
        requests = requests.filter { it.sender.userId != senderId }
        notifyDataSetChanged()
        onRequestHandled()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val binding = ItemNotificationRequestBinding.inflate(LayoutInflater.from(context), parent, false)
        return RequestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        holder.bind(requests[position])
    }

    override fun getItemCount(): Int = requests.size

    inner class RequestViewHolder(private val binding: ItemNotificationRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(request: FriendRequestNotice) {
            val sender = request.sender
            val displayName = sender.displayName()

            // The name is bold, as in the other notification rows. It is
            // escaped first so a name can never be read as HTML.
            binding.notifRequestText.text = HtmlCompat.fromHtml(
                "<b>${TextUtils.htmlEncode(displayName)}</b> sent you a friend request.",
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            binding.notifRequestTime.text = shortAge(request.timestamp)

            binding.notifRequestAvatarImage.clipToOutline = true
            bindAvatar(
                sender.profilePic, displayName,
                binding.notifRequestAvatarImage, binding.notifRequestAvatarInitials
            )

            binding.notifRequestConfirmBtn.setOnClickListener {
                Friendship.act(sender.userId, "accept") { requestHandled(sender.userId) }
            }

            binding.notifRequestDeleteBtn.setOnClickListener {
                Friendship.act(sender.userId, "decline") { requestHandled(sender.userId) }
            }

            binding.root.setOnClickListener {
                val intent = Intent(context, OtherProfileActivity::class.java)
                intent.putExtra("userId", sender.userId)
                context.startActivity(intent)
            }
        }

        /** Age in the short form the design uses: "Just now", "5m", "2h", "3d", "4w". */
        private fun shortAge(timestamp: Long): String {
            if (timestamp <= 0) return ""
            val minutes = (System.currentTimeMillis() - timestamp).coerceAtLeast(0) / 60_000
            return when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "${minutes}m"
                minutes < 24 * 60 -> "${minutes / 60}h"
                minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)}d"
                else -> "${minutes / (7 * 24 * 60)}w"
            }
        }
    }
}
