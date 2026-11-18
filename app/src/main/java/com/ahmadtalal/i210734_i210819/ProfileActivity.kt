package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmadtalal.i210734_i210819.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    /** My profile and friend count, kept fresh while the screen is visible. */
    private val profileQuery = LiveQuery("users/get.php", intervalMs = 6000L) { result ->
        renderProfile(User.fromJson(result.obj()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.profileBackBtn.setOnClickListener { finish() }
        binding.editProfileBtn.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        if (Session.isLoggedIn) profileQuery.start()
    }

    override fun onStop() {
        super.onStop()
        profileQuery.stop()
    }

    private fun renderProfile(user: User) {
        val displayName = user.displayName()

        binding.profileTitleName.text = displayName
        binding.profileNameTxt.text = displayName
        binding.profileBioTxt.text = user.bio.ifEmpty { "No bio available." }
        binding.profileAvatar.text = initialsOf(displayName)

        binding.profileFriendsCountTxt.text = "${user.friendCount} friends"
        binding.profileFriendsSectionCountTxt.text = "${user.friendCount} friends"
    }
}
