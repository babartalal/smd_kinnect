package com.ahmadtalal.i210734_i210819

import org.json.JSONObject

data class Post(
    val postId: String = "",
    val authorId: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    /** Server image paths; load them with ImageView.loadRemote. */
    val images: List<String> = emptyList(),
    val author: User? = null,
    val reactionCount: Int = 0,
    val myReaction: String = "",
    /** Written offline and still waiting in pending_actions. */
    val pending: Boolean = false
) {
    companion object {
        fun fromJson(json: JSONObject): Post {
            val imageArray = json.optJSONArray("images")
            val images = (0 until (imageArray?.length() ?: 0)).map { imageArray!!.optString(it) }
            return Post(
                postId = json.optString("postId"),
                authorId = json.optString("authorId"),
                text = json.optString("text"),
                timestamp = json.optLong("timestamp"),
                images = images,
                author = json.optJSONObject("author")?.let { User.fromJson(it) },
                reactionCount = json.optInt("reactionCount"),
                myReaction = json.optString("myReaction"),
                pending = json.optBoolean("pending")
            )
        }
    }
}
