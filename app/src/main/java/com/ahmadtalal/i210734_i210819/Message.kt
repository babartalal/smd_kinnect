package com.ahmadtalal.i210734_i210819

import org.json.JSONObject

data class Message(
    val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val text: String = "",
    /**
     * Server path of the attached media (a photo, a video or a file; see
     * [mediaKind]). Photos load with ImageView.loadRemote, the rest open
     * from Api.imageUrl(image).
     */
    val image: String = "",
    /** "image", "video" or "file" as sent by the server; empty on older photo messages. */
    val mediaType: String = "",
    /** Name to show for an attached file ("Report.pdf"). */
    val fileName: String = "",
    val timestamp: Long = 0L,
    val isEdited: Boolean = false,
    val replyToMsgId: String = "",
    val replyToText: String = "",
    val heartReaction: Boolean = false,
    val isSystemMessage: Boolean = false,
    /** Written offline and still waiting in pending_actions. */
    val pending: Boolean = false
) {
    /** What is attached: "" (nothing), "image", "video" or "file". */
    val mediaKind: String
        get() = when {
            image.isEmpty() -> ""
            mediaType.isEmpty() -> "image" // sent before videos and files existed
            else -> mediaType
        }

    /** One line that stands for this message, e.g. in the "Replying" bar. */
    fun preview(): String = text.ifEmpty {
        when (mediaKind) {
            "image" -> "📷 Photo"
            "video" -> "🎥 Video"
            "file" -> "📎 " + fileName.ifEmpty { "File" }
            else -> ""
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Message = Message(
            messageId = json.optString("messageId"),
            chatId = json.optString("chatId"),
            senderId = json.optString("senderId"),
            text = json.optString("text"),
            image = json.optString("image"),
            mediaType = json.optString("mediaType"),
            fileName = json.optString("fileName"),
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
