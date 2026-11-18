package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ahmadtalal.i210734_i210819.databinding.ItemFriendRequestBinding

/** Incoming friend requests; each sender arrives with their mutual-friend count. */
class FriendRequestAdapter(
    private val context: Context,
    private var requestList: List<User>,
    private val onRequestHandled: () -> Unit
) : RecyclerView.Adapter<FriendRequestAdapter.RequestViewHolder>() {

    fun updateData(newList: List<User>) {
        // Copy, because the caller reuses and clears its own list.
        requestList = newList.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val binding = ItemFriendRequestBinding.inflate(LayoutInflater.from(context), parent, false)
        return RequestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val sender = requestList[position]
        holder.bind(sender)
    }

    override fun getItemCount(): Int = requestList.size

    inner class RequestViewHolder(private val binding: ItemFriendRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(sender: User) {
            val displayName = sender.displayName()
            binding.requestNameTxt.text = displayName

            binding.requestAvatarImage.clipToOutline = true
            bindAvatar(sender.profilePic, displayName, binding.requestAvatarImage, binding.requestAvatarInitials)
            binding.mutualFriendsTxt.text = "${sender.mutualCount} mutual friends"

            binding.confirmBtn.setOnClickListener {
                Friendship.act(sender.userId, "accept", onRequestHandled)
            }

            binding.deleteBtn.setOnClickListener {
                Friendship.act(sender.userId, "decline", onRequestHandled)
            }

            binding.root.setOnClickListener {
                val intent = Intent(context, OtherProfileActivity::class.java)
                intent.putExtra("userId", sender.userId)
                context.startActivity(intent)
            }
        }
    }
}
