package com.ahmadtalal.i210734_i210819

import org.json.JSONObject

data class User(
    val userId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val name: String = "",
    val username: String = "",
    val bio: String = "",
    val website: String = "",
    val birthday: String = "",
    val gender: String = "",
    val email: String = "",
    val phone: String = "",
    /** Server paths such as "uploads/abc.jpg"; load them with ImageView.loadRemote. */
    val profilePic: String = "",
    val coverPhoto: String = "",
    val isOnline: Boolean = false,
    /** How this user relates to me: self, friends, requested, incoming or none. */
    val friendState: String = "none",
    val friendCount: Int = 0,
    val mutualCount: Int = 0
) {
    companion object {
        fun fromJson(json: JSONObject): User = User(
            userId = json.optString("userId"),
            firstName = json.optString("firstName"),
            lastName = json.optString("lastName"),
            name = json.optString("name"),
            username = json.optString("username"),
            bio = json.optString("bio"),
            website = json.optString("website"),
            birthday = json.optString("birthday"),
            gender = json.optString("gender"),
            email = json.optString("email"),
            phone = json.optString("phone"),
            profilePic = json.optString("profilePic"),
            coverPhoto = json.optString("coverPhoto"),
            isOnline = json.optBoolean("isOnline"),
            friendState = json.optString("friendState", "none"),
            friendCount = json.optInt("friendCount"),
            mutualCount = json.optInt("mutualCount")
        )
    }
}
