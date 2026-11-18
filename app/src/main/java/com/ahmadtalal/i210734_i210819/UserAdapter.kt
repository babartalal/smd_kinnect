package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ahmadtalal.i210734_i210819.databinding.ItemUserSearchBinding

/**
 * A list of users with a friend button. Each User already carries its
 * mutual-friend count and friendship state from the server, so rows need
 * no further requests.
 */
class UserAdapter(
    private val context: Context,
    userList: List<User>,
    /** Called after a friend action succeeded, so the screen can reload. */
    private val onFriendshipChanged: () -> Unit = {}
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private val currentUserId: String = Session.userId
    private var userList: MutableList<User> = userList.toMutableList()

    fun updateData(newList: List<User>) {
        userList = newList.toMutableList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserSearchBinding.inflate(LayoutInflater.from(context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]
        holder.bind(user)
    }

    override fun getItemCount(): Int = userList.size

    inner class UserViewHolder(private val binding: ItemUserSearchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            val displayName = user.displayName()
            binding.userNameTxt.text = displayName

            binding.userAvatarImage.clipToOutline = true
            bindAvatar(user.profilePic, displayName, binding.userAvatarImage, binding.userAvatarInitials)

            val isMe = currentUserId.isEmpty() || user.userId == currentUserId
            binding.userInfoTxt.text = if (isMe) "You" else "${user.mutualCount} mutual friends"

            if (isMe) {
                binding.actionBtn.visibility = View.GONE
            } else {
                binding.actionBtn.visibility = View.VISIBLE
                // "Add" is the round icon button from the design; other states show a text pill.
                val label = Friendship.buttonLabel(user.friendState, addLabel = "")
                binding.actionBtn.text = label
                binding.actionBtn.setBackgroundResource(
                    if (label.isEmpty()) R.drawable.people_bg_add_friend_round
                    else R.drawable.k_bg_pill_teal_tint
                )
                binding.actionBtn.setOnClickListener {
                    Friendship.toggle(user.userId, user.friendState) { newState ->
                        val index = userList.indexOfFirst { it.userId == user.userId }
                        if (index != -1) {
                            userList[index] = userList[index].copy(friendState = newState)
                            notifyItemChanged(index)
                        }
                        onFriendshipChanged()
                    }
                }
            }

            binding.root.setOnClickListener {
                val intent = Intent(context, OtherProfileActivity::class.java)
                intent.putExtra("userId", user.userId)
                context.startActivity(intent)
            }
        }
    }
}
