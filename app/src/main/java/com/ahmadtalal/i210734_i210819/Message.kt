package com.ahmadtalal.i210734_i210819

import org.json.JSONObject

data class Message(
    val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val text: String = "",
    /** Server image path; load it with ImageView.loadRemote. */
    val image: String = "",
    val timestamp: Long = 0L,
    val isEdited: Boolean = false,
    val replyToMsgId: String = "",
    val replyToText: String = "",
    val heartReaction: Boolean = false,
    val isSystemMessage: Boolean = false,
    /** Written offline and still waiting in pending_actions. */
    val pending: Boolean = false
) {
    companion object {
        fun fromJson(json: JSONObject): Message = Message(
            messageId = json.optString("messageId"),
            chatId = json.optString("chatId"),
            senderId = json.optString("senderId"),
            text = json.optString("text"),
            image = json.optString("image"),
            timestamp = json.optLong("timestamp"),
            isEdited = json.optBoolean("isEdited"),
            replyToMsgId = json.optString("replyToMsgId"),
            replyToText = json.optString("replyToText"),
            heartReaction = json.optBoolean("heartReaction"),
            isSystemMessage = json.optBoolean("isSystemMessage"),
            pending = json.optBoolean("pending")
        )
    }
}
