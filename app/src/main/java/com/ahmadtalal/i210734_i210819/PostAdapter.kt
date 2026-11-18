package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ahmadtalal.i210734_i210819.databinding.ItemPostBinding

/**
 * Home feed posts. Each Post arrives from posts/feed.php with its author,
 * image paths, reaction count and my own reaction already attached.
 */
class PostAdapter(
    private val context: Context,
    postList: List<Post>
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private var postList: MutableList<Post> = postList.toMutableList()

    fun updateData(newList: List<Post>) {
        postList = newList.toMutableList()
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

            // Author
            val authorName = post.author?.displayName().orEmpty()
            binding.postAuthorName.text = authorName
            bindAvatar(post.author?.profilePic.orEmpty(), authorName, binding.postAuthorAvatar, binding.postAuthorInitials)

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

            // Reactions & Picker
            setupReactions(post)

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
                    binding.singleImageView.loadRemote(images[0])
                }
                2 -> {
                    binding.twoImageGrid.visibility = View.VISIBLE
                    binding.image21.loadRemote(images[0])
                    binding.image22.loadRemote(images[1])
                }
                3 -> {
                    binding.threeImageGrid.visibility = View.VISIBLE
                    binding.image31.loadRemote(images[0])
                    binding.image32.loadRemote(images[1])
                    binding.image33.loadRemote(images[2])
                }
                else -> { // 4 or more
                    binding.fourPlusImageGrid.visibility = View.VISIBLE
                    binding.image41.loadRemote(images[0])
                    binding.image42.loadRemote(images[1])
                    binding.image43.loadRemote(images[2])
                    if (images.size > 3) {
                        binding.moreImagesOverlay.visibility = View.VISIBLE
                        binding.moreImagesOverlay.text = "+${images.size - 3}"
                    }
                }
            }
        }

        private fun toggleReactionPicker() {
            if (binding.reactionPickerLayout.visibility == View.VISIBLE) {
                binding.reactionPickerLayout.visibility = View.GONE
            } else {
                binding.reactionPickerLayout.visibility = View.VISIBLE
            }
        }

        private fun setupReactions(post: Post) {
            updateReactionButtonUI(post.myReaction)
            binding.reactionSummaryTxt.text = "${post.reactionCount} reactions"

            // Bind Reaction Selection Click Handlers
            binding.likeReaction.setOnClickListener { handleReactionSelected(post.postId, "like") }
            binding.loveReaction.setOnClickListener { handleReactionSelected(post.postId, "love") }
            binding.hahaReaction.setOnClickListener { handleReactionSelected(post.postId, "haha") }
            binding.wowReaction.setOnClickListener { handleReactionSelected(post.postId, "wow") }
            binding.sadReaction.setOnClickListener { handleReactionSelected(post.postId, "sad") }
            binding.angryReaction.setOnClickListener { handleReactionSelected(post.postId, "angry") }
        }

        /**
         * The server toggles: picking the reaction I already have removes it,
         * any other one replaces it. It answers with the new count and state.
         */
        private fun handleReactionSelected(postId: String, selectedReaction: String) {
            binding.reactionPickerLayout.visibility = View.GONE

            val body = mapOf("post_id" to postId, "reaction" to selectedReaction)
            Api.post("posts/react.php", body, queueIfOffline = true) { result ->
                if (result.ok) {
                    val index = postList.indexOfFirst { it.postId == postId }
                    if (index != -1) {
                        postList[index] = postList[index].copy(
                            myReaction = result.obj().optString("myReaction"),
                            reactionCount = result.obj().optInt("reactionCount")
                        )
                        notifyItemChanged(index)
                    }
                } else {
                    Toast.makeText(context, result.error, Toast.LENGTH_SHORT).show()
                }
            }
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
