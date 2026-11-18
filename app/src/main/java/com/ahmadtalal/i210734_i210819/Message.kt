package com.ahmadtalal.i210734_i210819

data class Message(
    val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val text: String = "",
    val imageBase64: String = "",
    val timestamp: Long = 0L,
    val isEdited: Boolean = false,
    val replyToMsgId: String = "",
    val replyToText: String = "",
    val heartReaction: Boolean = false,
    val isSystemMessage: Boolean = false
)
