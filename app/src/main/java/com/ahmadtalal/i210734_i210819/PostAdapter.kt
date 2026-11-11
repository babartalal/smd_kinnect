package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.text.format.DateUtils
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ahmadtalal.i210734_i210819.databinding.ItemPostBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class PostAdapter(
    private val context: Context,
    private var postList: List<Post>
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val dbRef = FirebaseDatabase.getInstance().reference

    fun updateData(newList: List<Post>) {
        postList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = postList[position]
        holder.bind(post)
    }

    override fun getItemCount(): Int = postList.size

    inner class PostViewHolder(private val binding: ItemPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(post: Post) {
            binding.reactionPickerLayout.visibility = View.GONE

            // Load Author Data
            loadAuthorData(post.authorId)

            // Timestamp
            if (post.timestamp > 0) {
                val relativeTime = DateUtils.getRelativeTimeSpanString(
                    post.timestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
                binding.postTimestamp.text = relativeTime
            } else {
                binding.postTimestamp.text = "Just now"
            }

            // Caption
            if (post.text.isNotEmpty()) {
                binding.postCaption.visibility = View.VISIBLE
                binding.postCaption.text = post.text
            } else {
                binding.postCaption.visibility = View.GONE
            }

            // Multi-Image Grid Logic
            setupImageGrid(post.images)

            // Setup Realtime Reactions & Picker
            setupReactions(post.postId)

            // Like button long press / click toggles reaction picker
            binding.likeBtn.setOnLongClickListener {
                toggleReactionPicker()
                true
            }

            binding.likeBtn.setOnClickListener {
                toggleReactionPicker()
            }

            // Comment button listener
            binding.commentBtn.setOnClickListener {
                val intent = Intent(context, CommentsActivity::class.java)
                intent.putExtra("postId", post.postId)
                context.startActivity(intent)
            }
        }

        private fun loadAuthorData(authorId: String) {
            if (authorId.isEmpty()) return

            dbRef.child("users").child(authorId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) return

                        val user = snapshot.getValue(User::class.java) ?: return
                        val name = user.name.ifEmpty {
                            "${user.firstName} ${user.lastName}".trim().ifEmpty { user.username }
                        }
                        binding.postAuthorName.text = name

                        if (user.profilePicBase64.isNotEmpty()) {
                            try {
                                val imageBytes = Base64.decode(user.profilePicBase64, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                binding.postAuthorAvatar.visibility = View.VISIBLE
                                binding.postAuthorInitials.visibility = View.GONE
                                Glide.with(context).load(bitmap).into(binding.postAuthorAvatar)
                            } catch (e: Exception) {
                                showInitials(name)
                            }
                        } else {
                            showInitials(name)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
        }

        private fun showInitials(name: String) {
            binding.postAuthorAvatar.visibility = View.GONE
            binding.postAuthorInitials.visibility = View.VISIBLE
            val initials = name.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2)
                .joinToString("")
                .uppercase()
            binding.postAuthorInitials.text = initials.ifEmpty { "U" }
        }

        private fun setupImageGrid(images: List<String>) {
            binding.singleImageView.visibility = View.GONE
            binding.twoImageGrid.visibility = View.GONE
            binding.threeImageGrid.visibility = View.GONE
            binding.fourPlusImageGrid.visibility = View.GONE
            binding.moreImagesOverlay.visibility = View.GONE

            if (images.isEmpty()) {
                binding.imageGridContainer.visibility = View.GONE
                return
            }

            binding.imageGridContainer.visibility = View.VISIBLE

            when (images.size) {
                1 -> {
                    binding.singleImageView.visibility = View.VISIBLE
                    loadImageBase64(images[0], binding.singleImageView)
                }
                2 -> {
                    binding.twoImageGrid.visibility = View.VISIBLE
                    loadImageBase64(images[0], binding.image21)
                    loadImageBase64(images[1], binding.image22)
                }
                3 -> {
                    binding.threeImageGrid.visibility = View.VISIBLE
                    loadImageBase64(images[0], binding.image31)
                    loadImageBase64(images[1], binding.image32)
                    loadImageBase64(images[2], binding.image33)
                }
                else -> { // 4 or more
                    binding.fourPlusImageGrid.visibility = View.VISIBLE
                    loadImageBase64(images[0], binding.image41)
                    loadImageBase64(images[1], binding.image42)
                    loadImageBase64(images[2], binding.image43)

                    if (images.size > 3) {
                        binding.moreImagesOverlay.visibility = View.VISIBLE
                        binding.moreImagesOverlay.text = "+${images.size - 3}"
                    }
                }
            }
        }

        private fun loadImageBase64(base64Str: String, targetView: ImageView) {
            try {
                val imageBytes = Base64.decode(base64Str, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                Glide.with(context).load(bitmap).into(targetView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun toggleReactionPicker() {
            if (binding.reactionPickerLayout.visibility == View.VISIBLE) {
                binding.reactionPickerLayout.visibility = View.GONE
            } else {
                binding.reactionPickerLayout.visibility = View.VISIBLE
            }
        }

        private fun setupReactions(postId: String) {
            val reactionsRef = dbRef.child("posts").child(postId).child("reactions")

            reactionsRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val totalReactions = snapshot.childrenCount
                    val userReaction = snapshot.child(currentUserId).getValue(String::class.java)

                    updateReactionButtonUI(userReaction)

                    if (totalReactions > 0) {
                        binding.reactionSummaryTxt.text = "$totalReactions reactions"
                    } else {
                        binding.reactionSummaryTxt.text = "0 reactions"
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })

            // Bind Reaction Selection Click Handlers
            binding.likeReaction.setOnClickListener { handleReactionSelected(postId, "like") }
            binding.loveReaction.setOnClickListener { handleReactionSelected(postId, "love") }
            binding.hahaReaction.setOnClickListener { handleReactionSelected(postId, "haha") }
            binding.wowReaction.setOnClickListener { handleReactionSelected(postId, "wow") }
            binding.sadReaction.setOnClickListener { handleReactionSelected(postId, "sad") }
            binding.angryReaction.setOnClickListener { handleReactionSelected(postId, "angry") }
        }

        private fun handleReactionSelected(postId: String, selectedReaction: String) {
            val userReactionRef = dbRef.child("posts").child(postId).child("reactions").child(currentUserId)

            userReactionRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val currentReaction = snapshot.getValue(String::class.java)
                    if (currentReaction == selectedReaction) {
                        // Same reaction selected again -> remove reaction (unlike)
                        userReactionRef.removeValue()
                    } else {
                        // Set or update reaction
                        userReactionRef.setValue(selectedReaction)
                    }
                    binding.reactionPickerLayout.visibility = View.GONE
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        }

        private fun updateReactionButtonUI(reactionType: String?) {
            val tealColor = ContextCompat.getColor(context, R.color.primary_teal)
            val grayColor = ContextCompat.getColor(context, R.color.text_gray)
            val redColor = Color.parseColor("#E53935")
            val orangeColor = Color.parseColor("#FB8C00")

            when (reactionType) {
                "like" -> {
                    binding.likeBtn.text = "Like"
                    binding.likeBtn.setTextColor(tealColor)
                }
                "love" -> {
                    binding.likeBtn.text = "Love"
                    binding.likeBtn.setTextColor(redColor)
                }
                "haha" -> {
                    binding.likeBtn.text = "Haha"
                    binding.likeBtn.setTextColor(orangeColor)
                }
                "wow" -> {
                    binding.likeBtn.text = "Wow"
                    binding.likeBtn.setTextColor(orangeColor)
                }
                "sad" -> {
                    binding.likeBtn.text = "Sad"
                    binding.likeBtn.setTextColor(orangeColor)
                }
                "angry" -> {
                    binding.likeBtn.text = "Angry"
                    binding.likeBtn.setTextColor(redColor)
                }
                else -> {
                    binding.likeBtn.text = "Like"
                    binding.likeBtn.setTextColor(grayColor)
                }
            }
        }
    }
}
