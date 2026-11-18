package com.ahmadtalal.i210734_i210819

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Sets up the session, local SQLite database and API client, and tells the
 * server the user is online while any screen of the app is visible. This
 * replaces the isOnline flag the app used to write to Firebase.
 */
class KinnectApp : Application() {

    private val handler = Handler(Looper.getMainLooper())
    private var startedActivities = 0

    private val heartbeat = object : Runnable {
        override fun run() {
            sendHeartbeat()
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        LocalDb.init(this)
        Session.init(this)
        Api.init(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities++ == 0) {
                    handler.post(heartbeat)
                }
            }

            override fun onActivityStopped(activity: Activity) {
                if (--startedActivities <= 0) {
                    startedActivities = 0
                    handler.removeCallbacks(heartbeat)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 20000L

        /**
         * Marks the user online, uploads this device's FCM token if the
         * server does not have it yet, and flushes writes queued offline.
         */
        fun sendHeartbeat() {
            if (!Session.isLoggedIn) return

            if (Session.fcmToken.isEmpty()) {
                try {
                    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                        if (!token.isNullOrEmpty()) Session.fcmToken = token
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val tokenToSend = if (Session.fcmTokenSynced) "" else Session.fcmToken
            Api.post("users/heartbeat.php", mapOf("fcmToken" to tokenToSend)) { result ->
                if (result.ok) {
                    if (tokenToSend.isNotEmpty()) Session.fcmTokenSynced = true
                    Api.syncPending()
                }
            }
        }
    }
}
