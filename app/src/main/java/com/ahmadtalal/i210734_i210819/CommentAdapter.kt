package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ahmadtalal.i210734_i210819.databinding.ItemCommentBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class CommentAdapter(
    private val context: Context,
    private var commentList: List<Comment>,
    private val onReplyClicked: (Comment, String) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val dbRef = FirebaseDatabase.getInstance().reference

    fun updateData(newList: List<Comment>) {
        commentList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = commentList[position]
        holder.bind(comment)
    }

    override fun getItemCount(): Int = commentList.size

    inner class CommentViewHolder(private val binding: ItemCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment) {
            loadAuthorData(comment.authorId) { authorName ->
                if (comment.replyToCommentId.isNotEmpty()) {
                    binding.replyingToTagTxt.visibility = View.VISIBLE
                    binding.replyingToTagTxt.text = "Replying to ${comment.replyToAuthorName.ifEmpty { "User" }}"
                    binding.commentContainerLayout.setPadding(48, 12, 16, 12)
                } else {
                    binding.replyingToTagTxt.visibility = View.GONE
                    binding.commentContainerLayout.setPadding(16, 12, 16, 12)
                }

                binding.commentReplyBtn.setOnClickListener {
                    onReplyClicked(comment, authorName)
                }
            }

            binding.commentText.text = comment.text

            if (comment.timestamp > 0) {
                val relativeTime = DateUtils.getRelativeTimeSpanString(
                    comment.timestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
                binding.commentTimestamp.text = relativeTime
            } else {
                binding.commentTimestamp.text = "Just now"
            }

            if (comment.imageBase64.isNotEmpty()) {
                try {
                    val imageBytes = Base64.decode(comment.imageBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    binding.commentImagePreview.visibility = View.VISIBLE
                    Glide.with(context).load(bitmap).into(binding.commentImagePreview)
                } catch (e: Exception) {
                    binding.commentImagePreview.visibility = View.GONE
                }
            } else {
                binding.commentImagePreview.visibility = View.GONE
            }

            setupLikes(comment)
        }

        private fun loadAuthorData(authorId: String, onLoaded: (String) -> Unit) {
            if (authorId.isEmpty()) {
                onLoaded("User")
                return
            }

            dbRef.child("users").child(authorId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) {
                            onLoaded("User")
                            return
                        }

                        val user = snapshot.getValue(User::class.java)
                        val name = user?.name?.ifEmpty {
                            "${user.firstName} ${user.lastName}".trim().ifEmpty { user.username }
                        } ?: "User"

                        binding.commentAuthorName.text = name

                        if (user != null && user.profilePicBase64.isNotEmpty()) {
                            try {
                                val imageBytes = Base64.decode(user.profilePicBase64, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                binding.commentAuthorAvatar.visibility = View.VISIBLE
                                binding.commentAuthorInitials.visibility = View.GONE
                                Glide.with(context).load(bitmap).into(binding.commentAuthorAvatar)
                            } catch (e: Exception) {
                                showInitials(name)
                            }
                        } else {
                            showInitials(name)
                        }

                        onLoaded(name)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        onLoaded("User")
                    }
                })
        }

        private fun showInitials(name: String) {
            binding.commentAuthorAvatar.visibility = View.GONE
            binding.commentAuthorInitials.visibility = View.VISIBLE
            val initials = name.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2)
                .joinToString("")
                .uppercase()
            binding.commentAuthorInitials.text = initials.ifEmpty { "U" }
        }

        private fun setupLikes(comment: Comment) {
            val likesRef = dbRef.child("posts").child(comment.postId).child("comments").child(comment.commentId).child("likes")

            likesRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val count = snapshot.childrenCount
                    val isLikedByMe = snapshot.child(currentUserId).exists()

                    binding.commentLikesCountTxt.text = "$count"

                    val tealColor = ContextCompat.getColor(context, R.color.primary_teal)
                    val grayColor = ContextCompat.getColor(context, R.color.text_gray)

                    if (isLikedByMe) {
                        binding.commentLikeBtn.setTextColor(tealColor)
                    } else {
                        binding.commentLikeBtn.setTextColor(grayColor)
                    }

                    binding.commentLikeBtn.setOnClickListener {
                        if (isLikedByMe) {
                            likesRef.child(currentUserId).removeValue()
                        } else {
                            likesRef.child(currentUserId).setValue(true)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }
}
