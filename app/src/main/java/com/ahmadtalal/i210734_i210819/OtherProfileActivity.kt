package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmadtalal.i210734_i210819.databinding.ActivityOtherProfileBinding

class OtherProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtherProfileBinding

    private var targetUserId: String = ""
    private val currentUserId: String = Session.userId
    private var profileQuery: LiveQuery? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOtherProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        targetUserId = intent.getStringExtra("userId").orEmpty()
        binding.otherProfileBackBtn.setOnClickListener { finish() }

        if (targetUserId.isEmpty()) {
            Toast.makeText(this, "User not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // The teal button is a container (icon + label); the label is addFriendBtn.
        binding.addFriendContainer.visibility =
            if (currentUserId.isEmpty() || targetUserId == currentUserId) View.GONE else View.VISIBLE

        binding.messageBtn.setOnClickListener {
            val chatIntent = Intent(this, ChatActivity::class.java)
            chatIntent.putExtra("recipientId", targetUserId)
            startActivity(chatIntent)
        }
        binding.otherProfileSearchBtn.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        // Crop the profile picture to its round background.
        binding.otherProfileAvatarImage.clipToOutline = true
        binding.otherProfileMutualTxt.text = HtmlCompat.fromHtml(
            "Friends with <b>Aisha Khan</b>, <b>Lina Marsh</b> and <b>36 others</b>",
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        // One web service returns the profile, friend counts and friendship state.
        profileQuery = LiveQuery("users/get.php", mapOf("user_id" to targetUserId)) { result ->
            renderProfile(User.fromJson(result.obj()))
        }
    }

    override fun onStart() {
        super.onStart()
        profileQuery?.start()
    }

    override fun onStop() {
        super.onStop()
        profileQuery?.stop()
    }

    private fun renderProfile(user: User) {
        val displayName = user.displayName()

        binding.otherProfileTitleName.text = displayName
        binding.otherProfileNameTxt.text = displayName
        binding.otherProfileBioTxt.text = user.bio.ifEmpty { "No bio available." }
        bindAvatar(user.profilePic, displayName, binding.otherProfileAvatarImage, binding.otherProfileAvatar)
        if (user.coverPhoto.isNotEmpty()) binding.otherProfileCoverImage.loadRemote(user.coverPhoto)
        binding.otherProfileFriendsTxt.text = HtmlCompat.fromHtml(
            "<b>${user.friendCount}</b> friends · <b>${user.mutualCount}</b> mutual",
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        binding.addFriendBtn.text = Friendship.buttonLabel(user.friendState, addLabel = "Add friend")
        binding.addFriendContainer.setOnClickListener {
            Friendship.toggle(targetUserId, user.friendState) {
                profileQuery?.refresh()
            }
        }
    }
}
