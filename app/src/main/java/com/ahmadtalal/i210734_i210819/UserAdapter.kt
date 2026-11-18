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
import com.ahmadtalal.i210734_i210819.databinding.ItemUserSearchBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class UserAdapter(
    private val context: Context,
    private var userList: List<User>
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val dbRef = FirebaseDatabase.getInstance().reference

    fun updateData(newList: List<User>) {
        userList = newList
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
            val displayName = user.name.ifEmpty {
                "${user.firstName} ${user.lastName}".trim().ifEmpty { user.username }
            }
            binding.userNameTxt.text = displayName

            if (user.profilePicBase64.isNotEmpty()) {
                try {
                    val imageBytes = Base64.decode(user.profilePicBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    binding.userAvatarImage.visibility = View.VISIBLE
                    binding.userAvatarInitials.visibility = View.GONE
                    Glide.with(context).load(bitmap).into(binding.userAvatarImage)
                } catch (e: Exception) {
                    showInitials(displayName)
                }
            } else {
                showInitials(displayName)
            }

            calculateMutualFriends(user.userId)
            updateFriendshipState(user.userId)

            binding.root.setOnClickListener {
                val intent = Intent(context, OtherProfileActivity::class.java)
                intent.putExtra("userId", user.userId)
                context.startActivity(intent)
            }
        }

        private fun showInitials(name: String) {
            binding.userAvatarImage.visibility = View.GONE
            binding.userAvatarInitials.visibility = View.VISIBLE
            val initials = name.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2)
                .joinToString("")
                .uppercase()
            binding.userAvatarInitials.text = initials.ifEmpty { "U" }
        }

        private fun calculateMutualFriends(targetUserId: String) {
            if (currentUserId.isEmpty() || targetUserId == currentUserId) {
                binding.userInfoTxt.text = "You"
                return
            }

            dbRef.child("friends").child(currentUserId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(currentSnapshot: DataSnapshot) {
                        val currentFriends = currentSnapshot.children.mapNotNull { it.key }.toSet()

                        dbRef.child("friends").child(targetUserId)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(targetSnapshot: DataSnapshot) {
                                    val targetFriends = targetSnapshot.children.mapNotNull { it.key }.toSet()
                                    val mutualCount = currentFriends.intersect(targetFriends).size
                                    binding.userInfoTxt.text = "$mutualCount mutual friends"
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    binding.userInfoTxt.text = "0 mutual friends"
                                }
                            })
                    }

                    override fun onCancelled(error: DatabaseError) {
                        binding.userInfoTxt.text = "0 mutual friends"
                    }
                })
        }

        private fun updateFriendshipState(targetUserId: String) {
            if (currentUserId.isEmpty() || targetUserId == currentUserId) {
                binding.actionBtn.visibility = View.GONE
                return
            } else {
                binding.actionBtn.visibility = View.VISIBLE
            }

            dbRef.child("friends").child(currentUserId).child(targetUserId)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists() && snapshot.getValue(Boolean::class.java) == true) {
                            binding.actionBtn.text = "Friends"
                            binding.actionBtn.setOnClickListener { unfriend(targetUserId) }
                        } else {
                            dbRef.child("friend_requests").child(targetUserId).child(currentUserId)
                                .addValueEventListener(object : ValueEventListener {
                                    override fun onDataChange(reqSentSnap: DataSnapshot) {
                                        if (reqSentSnap.exists() && reqSentSnap.getValue(Boolean::class.java) == true) {
                                            binding.actionBtn.text = "Requested"
                                            binding.actionBtn.setOnClickListener { cancelRequest(targetUserId) }
                                        } else {
                                            dbRef.child("friend_requests").child(currentUserId).child(targetUserId)
                                                .addValueEventListener(object : ValueEventListener {
                                                    override fun onDataChange(reqRecSnap: DataSnapshot) {
                                                        if (reqRecSnap.exists() && reqRecSnap.getValue(Boolean::class.java) == true) {
                                                            binding.actionBtn.text = "Confirm"
                                                            binding.actionBtn.setOnClickListener { confirmRequest(targetUserId) }
                                                        } else {
                                                            binding.actionBtn.text = "+ Add"
                                                            binding.actionBtn.setOnClickListener { sendRequest(targetUserId) }
                                                        }
                                                    }
                                                    override fun onCancelled(error: DatabaseError) {}
                                                })
                                        }
                                    }
                                    override fun onCancelled(error: DatabaseError) {}
                                })
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }

        private fun sendRequest(targetUserId: String) {
            dbRef.child("friend_requests").child(targetUserId).child(currentUserId).setValue(true)
        }

        private fun cancelRequest(targetUserId: String) {
            dbRef.child("friend_requests").child(targetUserId).child(currentUserId).removeValue()
        }

        private fun confirmRequest(targetUserId: String) {
            dbRef.child("friends").child(currentUserId).child(targetUserId).setValue(true)
            dbRef.child("friends").child(targetUserId).child(currentUserId).setValue(true)
            dbRef.child("friend_requests").child(currentUserId).child(targetUserId).removeValue()
        }

        private fun unfriend(targetUserId: String) {
            dbRef.child("friends").child(currentUserId).child(targetUserId).removeValue()
            dbRef.child("friends").child(targetUserId).child(currentUserId).removeValue()
        }
    }
}
