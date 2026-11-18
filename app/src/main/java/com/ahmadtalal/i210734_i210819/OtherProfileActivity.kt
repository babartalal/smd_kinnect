package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmadtalal.i210734_i210819.databinding.ActivityOtherProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class OtherProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtherProfileBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val dbRef = FirebaseDatabase.getInstance().reference

    private var targetUserId: String = ""
    private val currentUserId: String by lazy { auth.currentUser?.uid.orEmpty() }

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

        loadTargetUserProfile()
        loadMutualFriendsAndStats()
        setupFriendshipState()
    }

    private fun loadTargetUserProfile() {
        dbRef.child("users").child(targetUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return

                    val user = snapshot.getValue(User::class.java) ?: return
                    val displayName = user.name.ifEmpty {
                        "${user.firstName} ${user.lastName}".trim().ifEmpty { user.username }
                    }

                    binding.otherProfileTitleName.text = displayName
                    binding.otherProfileNameTxt.text = displayName
                    binding.otherProfileBioTxt.text = user.bio.ifEmpty { "No bio available." }

                    val initials = displayName.split(" ")
                        .mapNotNull { it.firstOrNull()?.toString() }
                        .take(2)
                        .joinToString("")
                        .uppercase()
                    binding.otherProfileAvatar.text = initials.ifEmpty { "U" }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadMutualFriendsAndStats() {
        if (currentUserId.isEmpty()) return

        dbRef.child("friends").child(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(currentSnapshot: DataSnapshot) {
                    val currentFriends = currentSnapshot.children.mapNotNull { it.key }.toSet()

                    dbRef.child("friends").child(targetUserId)
                        .addValueEventListener(object : ValueEventListener {
                            override fun onDataChange(targetSnapshot: DataSnapshot) {
                                val targetFriends = targetSnapshot.children.mapNotNull { it.key }.toSet()
                                val mutualCount = currentFriends.intersect(targetFriends).size
                                binding.otherProfileFriendsTxt.text = "${targetFriends.size} friends · $mutualCount mutual"
                            }

                            override fun onCancelled(error: DatabaseError) {}
                        })
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setupFriendshipState() {
        if (currentUserId.isEmpty() || targetUserId == currentUserId) {
            binding.addFriendBtn.visibility = View.GONE
            return
        } else {
            binding.addFriendBtn.visibility = View.VISIBLE
        }

        dbRef.child("friends").child(currentUserId).child(targetUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists() && snapshot.getValue(Boolean::class.java) == true) {
                        binding.addFriendBtn.text = "Friends"
                        binding.addFriendBtn.setOnClickListener { unfriend() }
                    } else {
                        dbRef.child("friend_requests").child(targetUserId).child(currentUserId)
                            .addValueEventListener(object : ValueEventListener {
                                override fun onDataChange(reqSentSnap: DataSnapshot) {
                                    if (reqSentSnap.exists() && reqSentSnap.getValue(Boolean::class.java) == true) {
                                        binding.addFriendBtn.text = "Requested"
                                        binding.addFriendBtn.setOnClickListener { cancelRequest() }
                                    } else {
                                        dbRef.child("friend_requests").child(currentUserId).child(targetUserId)
                                            .addValueEventListener(object : ValueEventListener {
                                                override fun onDataChange(reqRecSnap: DataSnapshot) {
                                                    if (reqRecSnap.exists() && reqRecSnap.getValue(Boolean::class.java) == true) {
                                                        binding.addFriendBtn.text = "Confirm"
                                                        binding.addFriendBtn.setOnClickListener { confirmRequest() }
                                                    } else {
                                                        binding.addFriendBtn.text = "+ Add friend"
                                                        binding.addFriendBtn.setOnClickListener { sendRequest() }
                                                    }
                                                }

                                                override fun onCancelled(error: DatabaseError) {}
                                            })
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {}
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun sendRequest() {
        dbRef.child("friend_requests").child(targetUserId).child(currentUserId).setValue(true)
    }

    private fun cancelRequest() {
        dbRef.child("friend_requests").child(targetUserId).child(currentUserId).removeValue()
    }

    private fun confirmRequest() {
        dbRef.child("friends").child(currentUserId).child(targetUserId).setValue(true)
        dbRef.child("friends").child(targetUserId).child(currentUserId).setValue(true)
        dbRef.child("friend_requests").child(currentUserId).child(targetUserId).removeValue()
    }

    private fun unfriend() {
        dbRef.child("friends").child(currentUserId).child(targetUserId).removeValue()
        dbRef.child("friends").child(targetUserId).child(currentUserId).removeValue()
    }
}
