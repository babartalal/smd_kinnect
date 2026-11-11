package com.ahmadtalal.i210734_i210819

data class ChatConversation(
    val recipientId: String = "",
    val lastMessage: String = "",
    val timestamp: Long = 0L,
    val unread: Boolean = false
)
