package com.ahmadtalal.i210734_i210819

data class Comment(
    val commentId: String = "",
    val postId: String = "",
    val authorId: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val imageBase64: String = "",
    val replyToCommentId: String = "",
    val replyToAuthorName: String = ""
)
