package com.ahmadtalal.i210734_i210819

import org.json.JSONObject

data class Story(
    val storyId: String = "",
    val userId: String = "",
    val timestamp: Long = 0L,
    /** Server image path; load it with ImageView.loadRemote. */
    val image: String = "",
    val textLabel: String = "",
    val audience: String = "public"
) {
    companion object {
        fun fromJson(json: JSONObject): Story = Story(
            storyId = json.optString("storyId"),
            userId = json.optString("userId"),
            timestamp = json.optLong("timestamp"),
            image = json.optString("image"),
            textLabel = json.optString("textLabel"),
            audience = json.optString("audience", "public")
        )
    }
}
