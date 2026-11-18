package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ahmadtalal.i210734_i210819.databinding.ItemCommentBinding

/**
 * Comments on a post. Each Comment arrives from comments/list.php with its
 * author and like information already attached.
 */
class CommentAdapter(
    private val context: Context,
    private var commentList: List<Comment>,
    /** Called after a like was saved, so the screen can reload the comments. */
    private val onLikeChanged: () -> Unit = {},
    private val onReplyClicked: (Comment, String) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

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
            val authorName = comment.author?.displayName().orEmpty().ifEmpty { "User" }
            binding.commentAuthorName.text = authorName
            bindAvatar(
                comment.author?.profilePic.orEmpty(), authorName,
                binding.commentAuthorAvatar, binding.commentAuthorInitials
            )

            val isReply = comment.replyToCommentId.isNotEmpty()
            if (isReply) {
                binding.replyingToTagTxt.visibility = View.VISIBLE
                binding.replyingToTagTxt.text = "@${comment.replyToAuthorName.ifEmpty { "User" }}"
            } else {
                binding.replyingToTagTxt.visibility = View.GONE
            }

            // Replies are indented under their parent and use a smaller avatar.
            val density = context.resources.displayMetrics.density
            binding.commentContainerLayout.setPadding(
                ((if (isReply) 56 else 16) * density).toInt(), (6 * density).toInt(),
                (16 * density).toInt(), (6 * density).toInt()
            )
            val avatarSize = ((if (isReply) 28 else 36) * density).toInt()
            for (avatarView in listOf(binding.commentAuthorAvatar, binding.commentAuthorInitials)) {
                avatarView.layoutParams = avatarView.layoutParams.apply {
                    width = avatarSize
                    height = avatarSize
                }
            }
            binding.commentAuthorAvatar.clipToOutline = true
            binding.commentImagePreview.clipToOutline = true

            binding.commentReplyBtn.setOnClickListener {
                onReplyClicked(comment, authorName)
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

            if (comment.image.isNotEmpty()) {
                binding.commentImagePreview.visibility = View.VISIBLE
                binding.commentImagePreview.loadRemote(comment.image)
            } else {
                binding.commentImagePreview.visibility = View.GONE
            }

            setupLikes(comment)
        }

        private fun setupLikes(comment: Comment) {
            binding.commentLikesCountTxt.text = "${comment.likeCount}"
            binding.commentLikePill.visibility = if (comment.likeCount > 0) View.VISIBLE else View.GONE

            val tealColor = ContextCompat.getColor(context, R.color.primary_teal)
            val grayColor = ContextCompat.getColor(context, R.color.text_gray)
            binding.commentLikeBtn.setTextColor(if (comment.likedByMe) tealColor else grayColor)

            binding.commentLikeBtn.setOnClickListener {
                // The server toggles the like and the reload shows the new count.
                Api.post("comments/like.php", mapOf("comment_id" to comment.commentId), queueIfOffline = true) { result ->
                    if (result.ok) onLikeChanged()
                }
            }
        }
    }
}
