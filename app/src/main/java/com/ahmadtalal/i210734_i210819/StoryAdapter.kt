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
import com.ahmadtalal.i210734_i210819.databinding.ItemStoryCardBinding
import com.google.firebase.auth.FirebaseAuth

data class StoryModel(
    val story: Story? = null,
    val author: User? = null,
    val isCreateStory: Boolean = false
)

class StoryAdapter(
    private val context: Context,
    private var storyList: List<StoryModel>
) : RecyclerView.Adapter<StoryAdapter.StoryViewHolder>() {

    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    fun updateData(newList: List<StoryModel>) {
        storyList = newList
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
                val author = item.author

                val displayName = author?.name?.ifEmpty {
                    "${author.firstName} ${author.lastName}".trim().ifEmpty { author.username }
                } ?: "User"

                if (story?.userId == currentUserId) {
                    binding.storyAuthorName.text = "Your Story"
                } else {
                    binding.storyAuthorName.text = displayName
                }

                if (!story?.imageBase64.isNullOrEmpty()) {
                    try {
                        val imageBytes = Base64.decode(story?.imageBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        binding.storyAuthorAvatar.visibility = View.VISIBLE
                        binding.storyAuthorInitials.visibility = View.GONE
                        Glide.with(context).load(bitmap).into(binding.storyAuthorAvatar)
                    } catch (e: Exception) {
                        showInitials(displayName)
                    }
                } else {
                    showInitials(displayName)
                }

                binding.root.setOnClickListener {
                    if (story?.userId == currentUserId) {
                        val intent = Intent(context, YourStoryActivity::class.java)
                        intent.putExtra("storyId", story?.storyId)
                        context.startActivity(intent)
                    } else {
                        val intent = Intent(context, StoryViewerActivity::class.java)
                        intent.putExtra("storyId", story?.storyId)
                        intent.putExtra("userId", story?.userId)
                        context.startActivity(intent)
                    }
                }
            }
        }

        private fun showInitials(name: String) {
            binding.storyAuthorAvatar.visibility = View.GONE
            binding.storyAuthorInitials.visibility = View.VISIBLE
            val initials = name.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2)
                .joinToString("")
                .uppercase()
            binding.storyAuthorInitials.text = initials.ifEmpty { "U" }
        }
    }
}
