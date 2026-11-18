package com.ahmadtalal.i210734_i210819

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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

        binding.addFriendBtn.visibility =
            if (currentUserId.isEmpty() || targetUserId == currentUserId) View.GONE else View.VISIBLE

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
        binding.otherProfileAvatar.text = initialsOf(displayName)
        binding.otherProfileFriendsTxt.text = "${user.friendCount} friends · ${user.mutualCount} mutual"

        binding.addFriendBtn.text = Friendship.buttonLabel(user.friendState, addLabel = "+ Add friend")
        binding.addFriendBtn.setOnClickListener {
            Friendship.toggle(targetUserId, user.friendState) {
                profileQuery?.refresh()
            }
        }
    }
}
