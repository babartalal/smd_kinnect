package com.ahmadtalal.i210734_i210819

import org.json.JSONObject

data class Story(
    val storyId: String = "",
    val userId: String = "",
    val timestamp: Long = 0L,
    /**
     * Server path of the story's picture or video (see [mediaType]). Pictures
     * load with ImageView.loadRemote; videos play from Api.imageUrl(image).
     */
    val image: String = "",
    /** "image" or "video". */
    val mediaType: String = "image",
    val textLabel: String = "",
    val audience: String = "public"
) {
    val isVideo: Boolean get() = mediaType == "video"

    companion object {
        fun fromJson(json: JSONObject): Story = Story(
            storyId = json.optString("storyId"),
            userId = json.optString("userId"),
            timestamp = json.optLong("timestamp"),
            image = json.optString("image"),
            mediaType = json.optString("mediaType", "image").ifEmpty { "image" },
            textLabel = json.optString("textLabel"),
            audience = json.optString("audience", "public")
        )
    }
}
