package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ahmadtalal.i210734_i210819.databinding.ItemStoryCardBinding

data class StoryModel(
    val story: Story? = null,
    val author: User? = null,
    val isCreateStory: Boolean = false
)

class StoryAdapter(
    private val context: Context,
    private var storyList: List<StoryModel>
) : RecyclerView.Adapter<StoryAdapter.StoryViewHolder>() {

    private val currentUserId: String = Session.userId

    /** Name of the signed-in user, for the initials on the "Create story" card. */
    private var currentUserName: String = ""

    fun setCurrentUserName(name: String) {
        currentUserName = name
        notifyDataSetChanged()
    }

    fun updateData(newList: List<StoryModel>) {
        // Copy, because the caller reuses and clears its own list.
        storyList = newList.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryViewHolder {
        val binding = ItemStoryCardBinding.inflate(LayoutInflater.from(context), parent, false)
        return StoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StoryViewHolder, position: Int) {
        val item = storyList[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = storyList.size

    inner class StoryViewHolder(private val binding: ItemStoryCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // Round the card's corners and the avatar picture.
            binding.storyCardContainer.clipToOutline = true
            binding.storyAuthorAvatar.clipToOutline = true
        }

        fun bind(item: StoryModel) {
            if (item.isCreateStory) {
                binding.createStoryLayout.visibility = View.VISIBLE
                binding.storyContentLayout.visibility = View.GONE
                binding.createStoryIcon.visibility = View.VISIBLE
                binding.createStoryInitials.text = initialsOf(currentUserName)

                binding.root.setOnClickListener {
                    context.startActivity(Intent(context, StoryEditorActivity::class.java))
                }
            } else {
                binding.createStoryLayout.visibility = View.GONE
                binding.storyContentLayout.visibility = View.VISIBLE
                val story = item.story
                val displayName = item.author?.displayName().orEmpty().ifEmpty { "User" }

                if (story != null && story.userId == currentUserId) {
                    binding.storyAuthorName.text = "Your story"
                } else {
                    binding.storyAuthorName.text = displayName
                }

                // The story's own picture fills the card. A video story has no
                // picture to show here, so its card keeps the placeholder.
                val storyImage = story?.image.orEmpty()
                if (storyImage.isNotEmpty() && story?.isVideo != true) {
                    binding.storyImage.loadRemote(storyImage)
                } else {
                    Glide.with(context.applicationContext).clear(binding.storyImage)
                    binding.storyImage.setImageResource(R.drawable.feed_landscape_night)
                }

                // The author's picture or initials sit in the ring on top of it.
                bindAvatar(
                    item.author?.profilePic.orEmpty(), displayName,
                    binding.storyAuthorAvatar, binding.storyAuthorInitials
                )

                binding.root.setOnClickListener {
                    if (story != null && story.userId == currentUserId) {
                        val intent = Intent(context, YourStoryActivity::class.java)
                        intent.putExtra("storyId", story.storyId)
                        context.startActivity(intent)
                    } else if (story != null) {
                        val intent = Intent(context, StoryViewerActivity::class.java)
                        intent.putExtra("storyId", story.storyId)
                        intent.putExtra("userId", story.userId)
                        context.startActivity(intent)
                    }
                }
            }
        }
    }
}
