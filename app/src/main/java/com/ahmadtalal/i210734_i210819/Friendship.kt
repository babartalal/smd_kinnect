package com.ahmadtalal.i210734_i210819

/**
 * Friend button logic shared by search results and profiles. The server
 * reports how a user relates to me as friendState; tapping the button sends
 * the matching action to friends/action.php.
 */
object Friendship {

    fun buttonLabel(friendState: String, addLabel: String): String = when (friendState) {
        "friends" -> "Friends"
        "requested" -> "Requested"
        "incoming" -> "Confirm"
        else -> addLabel
    }

    private fun actionFor(friendState: String): String = when (friendState) {
        "friends" -> "unfriend"
        "requested" -> "cancel"
        "incoming" -> "accept"
        else -> "send"
    }

    /** Perform the button's action; [onDone] receives the new friendState on success. */
    fun toggle(userId: String, friendState: String, onDone: (String) -> Unit) {
        val body = mapOf("user_id" to userId, "action" to actionFor(friendState))
        Api.post("friends/action.php", body) { result ->
            if (result.ok) onDone(result.obj().optString("friendState", "none"))
        }
    }

    fun act(userId: String, action: String, onDone: () -> Unit) {
        Api.post("friends/action.php", mapOf("user_id" to userId, "action" to action)) { result ->
            if (result.ok) onDone()
        }
    }
}
