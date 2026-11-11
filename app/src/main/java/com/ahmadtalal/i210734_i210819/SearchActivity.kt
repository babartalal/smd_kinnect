package com.ahmadtalal.i210734_i210819

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmadtalal.i210734_i210819.databinding.ActivitySearchBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var userAdapter: UserAdapter
    private val searchResults = mutableListOf<User>()
    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.searchBackBtn.setOnClickListener { finish() }

        setupRecyclerView()
        setupSearchTextWatcher()
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter(this, searchResults)
        binding.searchRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.searchRecyclerView.adapter = userAdapter
    }

    private fun setupSearchTextWatcher() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                if (query.isNotEmpty()) {
                    performSearch(query)
                } else {
                    searchResults.clear()
                    userAdapter.updateData(searchResults)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun performSearch(query: String) {
        val usersRef = FirebaseDatabase.getInstance().getReference("users")

        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                searchResults.clear()
                val lowerQuery = query.lowercase()

                for (child in snapshot.children) {
                    val user = child.getValue(User::class.java)
                    if (user != null && user.userId.isNotEmpty() && user.userId != currentUserId) {
                        val usernameMatches = user.username.lowercase().contains(lowerQuery)
                        val firstNameMatches = user.firstName.lowercase().contains(lowerQuery)
                        val lastNameMatches = user.lastName.lowercase().contains(lowerQuery)
                        val fullNameMatches = user.name.lowercase().contains(lowerQuery)

                        if (usernameMatches || firstNameMatches || lastNameMatches || fullNameMatches) {
                            searchResults.add(user)
                        }
                    }
                }
                userAdapter.updateData(searchResults)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
