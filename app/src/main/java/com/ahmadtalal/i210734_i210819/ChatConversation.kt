package com.ahmadtalal.i210734_i210819

import org.json.JSONObject

data class ChatConversation(
    val recipientId: String = "",
    val lastMessage: String = "",
    val timestamp: Long = 0L,
    val unread: Boolean = false,
    val user: User? = null
) {
    companion object {
        fun fromJson(json: JSONObject): ChatConversation = ChatConversation(
            recipientId = json.optString("recipientId"),
            lastMessage = json.optString("lastMessage"),
            timestamp = json.optLong("timestamp"),
            unread = json.optBoolean("unread"),
            user = json.optJSONObject("user")?.let { User.fromJson(it) }
        )
    }
}
