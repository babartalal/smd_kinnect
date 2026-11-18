package com.ahmadtalal.i210734_i210819

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmadtalal.i210734_i210819.databinding.ActivitySearchBinding

/**
 * Finds users by username or name as the user types (users/search.php).
 * The Everyone / Friends / Requests chips choose whose accounts are searched.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var userAdapter: UserAdapter
    private val searchResults = mutableListOf<User>()
    private var latestQuery = ""

    /** The `filter` value sent to users/search.php: everyone, friends or requests. */
    private var filter = FILTER_EVERYONE

    /** Counts searches so that a slow answer to an older one is ignored. */
    private var searchNumber = 0

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
        binding.searchClearBtn.setOnClickListener { binding.searchInput.text.clear() }

        setupRecyclerView()
        setupFilterChips()
        setupSearchTextWatcher()
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter(this, searchResults) {
            // A friend action can take a user out of the Friends or Requests
            // list (unfriend, confirm, cancel), so those lists are reloaded.
            if (filter != FILTER_EVERYONE) runSearch()
        }
        binding.searchRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.searchRecyclerView.adapter = userAdapter
    }

    private fun setupFilterChips() {
        binding.filterEveryoneChip.setOnClickListener { selectFilter(FILTER_EVERYONE) }
        binding.filterFriendsChip.setOnClickListener { selectFilter(FILTER_FRIENDS) }
        binding.filterRequestsChip.setOnClickListener { selectFilter(FILTER_REQUESTS) }
        showSelectedChip()
    }

    private fun selectFilter(newFilter: String) {
        if (filter == newFilter) return
        filter = newFilter
        showSelectedChip()
        runSearch()
    }

    /** Give the chosen chip the Kinnect.Chip.Selected look and the others Kinnect.Chip. */
    private fun showSelectedChip() {
        styleChip(binding.filterEveryoneChip, filter == FILTER_EVERYONE)
        styleChip(binding.filterFriendsChip, filter == FILTER_FRIENDS)
        styleChip(binding.filterRequestsChip, filter == FILTER_REQUESTS)
    }

    private fun styleChip(chip: TextView, selected: Boolean) {
        // Setting a background resets the padding, so it is put back afterwards.
        val left = chip.paddingLeft
        val top = chip.paddingTop
        val right = chip.paddingRight
        val bottom = chip.paddingBottom
        chip.setBackgroundResource(if (selected) R.drawable.k_bg_pill_teal_tint else R.drawable.k_bg_pill_gray)
        chip.setPadding(left, top, right, bottom)
        chip.setTextColor(
            ContextCompat.getColor(this, if (selected) R.color.primary_teal else R.color.k_text_primary)
        )
    }

    private fun setupSearchTextWatcher() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                latestQuery = s?.toString()?.trim().orEmpty()
                runSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /**
     * Search with the current text and filter. Everyone needs some text;
     * Friends and Requests list all of them while the field is empty.
     */
    private fun runSearch() {
        val number = ++searchNumber
        if (latestQuery.isEmpty() && filter == FILTER_EVERYONE) {
            searchResults.clear()
            userAdapter.updateData(searchResults)
            return
        }

        val params = mapOf("q" to latestQuery, "filter" to filter)
        Api.get("users/search.php", params) { result ->
            // Ignore answers to a text or filter the user has already moved past.
            if (!result.ok || number != searchNumber) return@get

            val users = result.arr()
            searchResults.clear()
            for (i in 0 until users.length()) {
                searchResults.add(User.fromJson(users.getJSONObject(i)))
            }
            userAdapter.updateData(searchResults)
        }
    }

    private companion object {
        const val FILTER_EVERYONE = "everyone"
        const val FILTER_FRIENDS = "friends"
        const val FILTER_REQUESTS = "requests"
    }
}
