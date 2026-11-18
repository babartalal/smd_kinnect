package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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

        fun bind(item: StoryModel) {
            if (item.isCreateStory) {
                binding.storyAuthorName.text = "Create Story"
                binding.createStoryIcon.visibility = View.VISIBLE
                binding.storyAuthorAvatar.visibility = View.GONE
                binding.storyAuthorInitials.visibility = View.GONE

                binding.root.setOnClickListener {
                    context.startActivity(Intent(context, StoryEditorActivity::class.java))
                }
            } else {
                binding.createStoryIcon.visibility = View.GONE
                val story = item.story
                val displayName = item.author?.displayName().orEmpty().ifEmpty { "User" }

                if (story != null && story.userId == currentUserId) {
                    binding.storyAuthorName.text = "Your Story"
                } else {
                    binding.storyAuthorName.text = displayName
                }

                // The card shows the story's own picture, or the author's initials.
                bindAvatar(story?.image.orEmpty(), displayName, binding.storyAuthorAvatar, binding.storyAuthorInitials)

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
