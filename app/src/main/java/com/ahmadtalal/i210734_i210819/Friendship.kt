package com.ahmadtalal.i210734_i210819

/**
 * Friend button logic shared by search results, profiles and requests. The
 * server reports how a user relates to me as friendState; tapping the button
 * sends the matching action to friends/action.php.
 *
 * Offline, the action is queued in pending_actions and the expected new
 * state is shown (and saved in SQLite) straight away.
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

    /** The state an action leads to, used while the real answer is still queued. */
    private fun expectedState(action: String): String = when (action) {
        "send" -> "requested"
        "accept" -> "friends"
        else -> "none"
    }

    /** Perform the button's action; [onDone] receives the new friendState. */
    fun toggle(userId: String, friendState: String, onDone: (String) -> Unit) {
        send(userId, actionFor(friendState), onDone)
    }

    fun act(userId: String, action: String, onDone: () -> Unit) {
        send(userId, action) { onDone() }
    }

    private fun send(userId: String, action: String, onDone: (String) -> Unit) {
        val body = mapOf("user_id" to userId, "action" to action)
        Api.post("friends/action.php", body, queueIfOffline = true) { result ->
            when {
                result.ok -> onDone(result.obj().optString("friendState", "none"))
                result.queued -> {
                    val expected = expectedState(action)
                    LocalDb.get().patchUser(userId) { it.put("friendState", expected) }
                    onDone(expected)
                }
            }
        }
    }
}
