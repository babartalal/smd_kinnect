package com.ahmadtalal.i210734_i210819

data class Story(
    val storyId: String = "",
    val userId: String = "",
    val timestamp: Long = 0L,
    val imageBase64: String = "",
    val textLabel: String = "",
    val audience: String = "public"
)
