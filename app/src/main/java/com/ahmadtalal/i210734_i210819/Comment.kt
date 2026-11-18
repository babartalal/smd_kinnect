package com.ahmadtalal.i210734_i210819

import org.json.JSONObject

data class Comment(
    val commentId: String = "",
    val postId: String = "",
    val authorId: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    /** Server image path; load it with ImageView.loadRemote. */
    val image: String = "",
    val replyToCommentId: String = "",
    val replyToAuthorName: String = "",
    val author: User? = null,
    val likeCount: Int = 0,
    val likedByMe: Boolean = false
) {
    companion object {
        fun fromJson(json: JSONObject): Comment = Comment(
            commentId = json.optString("commentId"),
            postId = json.optString("postId"),
            authorId = json.optString("authorId"),
            text = json.optString("text"),
            timestamp = json.optLong("timestamp"),
            image = json.optString("image"),
            replyToCommentId = json.optString("replyToCommentId"),
            replyToAuthorName = json.optString("replyToAuthorName"),
            author = json.optJSONObject("author")?.let { User.fromJson(it) },
            likeCount = json.optInt("likeCount"),
            likedByMe = json.optBoolean("likedByMe")
        )
    }
}
