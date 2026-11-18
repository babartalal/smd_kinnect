package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ahmadtalal.i210734_i210819.databinding.ItemFriendRequestBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FriendRequestAdapter(
    private val context: Context,
    private var requestList: List<User>,
    private val onRequestHandled: () -> Unit
) : RecyclerView.Adapter<FriendRequestAdapter.RequestViewHolder>() {

    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val dbRef = FirebaseDatabase.getInstance().reference

    fun updateData(newList: List<User>) {
        requestList = newList
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
            val displayName = sender.name.ifEmpty {
                "${sender.firstName} ${sender.lastName}".trim().ifEmpty { sender.username }
            }
            binding.requestNameTxt.text = displayName

            if (sender.profilePicBase64.isNotEmpty()) {
                try {
                    val imageBytes = Base64.decode(sender.profilePicBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    binding.requestAvatarImage.visibility = View.VISIBLE
                    binding.requestAvatarInitials.visibility = View.GONE
                    Glide.with(context).load(bitmap).into(binding.requestAvatarImage)
                } catch (e: Exception) {
                    showInitials(displayName)
                }
            } else {
                showInitials(displayName)
            }

            calculateMutualFriends(sender.userId)

            binding.confirmBtn.setOnClickListener {
                confirmFriendRequest(sender.userId)
            }

            binding.deleteBtn.setOnClickListener {
                deleteFriendRequest(sender.userId)
            }

            binding.root.setOnClickListener {
                val intent = Intent(context, OtherProfileActivity::class.java)
                intent.putExtra("userId", sender.userId)
                context.startActivity(intent)
            }
        }

        private fun showInitials(name: String) {
            binding.requestAvatarImage.visibility = View.GONE
            binding.requestAvatarInitials.visibility = View.VISIBLE
            val initials = name.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2)
                .joinToString("")
                .uppercase()
            binding.requestAvatarInitials.text = initials.ifEmpty { "U" }
        }

        private fun calculateMutualFriends(senderUserId: String) {
            if (currentUserId.isEmpty() || senderUserId == currentUserId) {
                binding.mutualFriendsTxt.text = "0 mutual friends"
                return
            }

            dbRef.child("friends").child(currentUserId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(currentSnapshot: DataSnapshot) {
                        val currentFriends = currentSnapshot.children.mapNotNull { it.key }.toSet()

                        dbRef.child("friends").child(senderUserId)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(targetSnapshot: DataSnapshot) {
                                    val targetFriends = targetSnapshot.children.mapNotNull { it.key }.toSet()
                                    val mutualCount = currentFriends.intersect(targetFriends).size
                                    binding.mutualFriendsTxt.text = "$mutualCount mutual friends"
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    binding.mutualFriendsTxt.text = "0 mutual friends"
                                }
                            })
                    }

                    override fun onCancelled(error: DatabaseError) {
                        binding.mutualFriendsTxt.text = "0 mutual friends"
                    }
                })
        }

        private fun confirmFriendRequest(senderUserId: String) {
            dbRef.child("friends").child(currentUserId).child(senderUserId).setValue(true)
            dbRef.child("friends").child(senderUserId).child(currentUserId).setValue(true)
            dbRef.child("friend_requests").child(currentUserId).child(senderUserId).removeValue()
                .addOnSuccessListener {
                    onRequestHandled()
                }
        }

        private fun deleteFriendRequest(senderUserId: String) {
            dbRef.child("friend_requests").child(currentUserId).child(senderUserId).removeValue()
                .addOnSuccessListener {
                    onRequestHandled()
                }
        }
    }
}
