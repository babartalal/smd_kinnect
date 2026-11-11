package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmadtalal.i210734_i210819.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val dbRef = FirebaseDatabase.getInstance().reference

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

        loadUserProfileData()
        loadFriendCountStats()
    }

    private fun loadUserProfileData() {
        val currentUser = auth.currentUser ?: return
        val userId = currentUser.uid

        dbRef.child("users").child(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return

                    val user = snapshot.getValue(User::class.java) ?: return
                    val displayName = user.name.ifEmpty {
                        "${user.firstName} ${user.lastName}".trim().ifEmpty { user.username }
                    }

                    binding.profileTitleName.text = displayName
                    binding.profileNameTxt.text = displayName
                    binding.profileBioTxt.text = user.bio.ifEmpty { "No bio available." }

                    val initials = displayName.split(" ")
                        .mapNotNull { it.firstOrNull()?.toString() }
                        .take(2)
                        .joinToString("")
                        .uppercase()
                    binding.profileAvatar.text = initials.ifEmpty { "U" }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadFriendCountStats() {
        val currentUser = auth.currentUser ?: return
        val userId = currentUser.uid

        dbRef.child("friends").child(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val count = snapshot.childrenCount
                    binding.profileFriendsCountTxt.text = "$count friends"
                    binding.profileFriendsSectionCountTxt.text = "$count friends"
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
