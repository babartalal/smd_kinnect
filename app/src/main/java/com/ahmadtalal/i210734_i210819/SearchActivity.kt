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

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var userAdapter: UserAdapter
    private val searchResults = mutableListOf<User>()
    private var latestQuery = ""

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
                latestQuery = query
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
        Api.get("users/search.php", mapOf("q" to query)) { result ->
            // Ignore answers to text the user has already typed past.
            if (!result.ok || query != latestQuery) return@get

            val users = result.arr()
            searchResults.clear()
            for (i in 0 until users.length()) {
                searchResults.add(User.fromJson(users.getJSONObject(i)))
            }
            userAdapter.updateData(searchResults)
        }
    }
}
