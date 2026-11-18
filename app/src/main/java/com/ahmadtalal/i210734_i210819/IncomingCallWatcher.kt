package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/**
 * Shows the "incoming call" dialog on a screen. It polls calls/incoming.php,
 * which replaces the listener the app had on Firebase's calls/{myUserId} node.
 *
 * Call [start] in onStart and [stop] in onStop.
 */
class IncomingCallWatcher(private val activity: AppCompatActivity) {

    private var dialog: AlertDialog? = null

    private val query = LiveQuery("calls/incoming.php", intervalMs = 3000L) { result ->
        // A call saved in the offline cache is long over; only trust live answers.
        if (!result.fromCache) {
            onIncomingCall(if (result.hasData) result.obj() else null)
        }
    }

    fun start() {
        if (Session.isLoggedIn) query.start()
    }

    fun stop() {
        query.stop()
        dismissDialog()
    }

    private fun dismissDialog() {
        dialog?.dismiss()
        dialog = null
    }

    private fun onIncomingCall(call: JSONObject?) {
        if (call == null) {
            // The caller hung up, or the call timed out.
            dismissDialog()
            return
        }
        if (dialog != null || activity.isFinishing || activity.isDestroyed) return

        val callerName = call.optString("callerName").ifEmpty { "Someone" }
        val channelName = call.optString("channelName")
        val callType = call.optString("type", "audio")
        val callerId = call.optString("callerId")

        dialog = AlertDialog.Builder(activity)
            .setTitle("Incoming ${callType.replaceFirstChar { it.uppercase() }} Call")
            .setMessage("$callerName is calling you...")
            .setPositiveButton("Accept") { _, _ ->
                dialog = null
                Api.post("calls/answer.php", mapOf("action" to "accept")) { result ->
                    if (result.ok) {
                        val intent = Intent(activity, VoiceCallActivity::class.java)
                        intent.putExtra("channelName", channelName)
                        intent.putExtra("type", callType)
                        intent.putExtra("isCaller", false)
                        intent.putExtra("recipientId", callerId)
                        activity.startActivity(intent)
                    } else {
                        Toast.makeText(activity, result.error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Decline") { _, _ ->
                dialog = null
                Api.post("calls/answer.php", mapOf("action" to "decline")) { query.refresh() }
            }
            .setCancelable(false)
            .create()
        dialog?.show()
    }
}
