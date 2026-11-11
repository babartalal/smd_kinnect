package com.ahmadtalal.i210734_i210819

data class Post(
    val postId: String = "",
    val authorId: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val images: List<String> = emptyList()
)
