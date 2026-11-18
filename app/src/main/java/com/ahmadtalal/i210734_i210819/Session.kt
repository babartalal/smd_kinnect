package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.content.SharedPreferences

/**
 * The signed-in user on this device. Replaces FirebaseAuth.currentUser:
 * the id and API token come from the login/signup web services and are kept
 * in private SharedPreferences so the user stays signed in between launches.
 */
object Session {

    private const val PREFS = "KinnectSession"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_TOKEN = "token"
    private const val KEY_FCM_TOKEN = "fcm_token"

    private lateinit var prefs: SharedPreferences

    /** True once this device's FCM token has been sent to the server for the current login. */
    @Volatile
    var fcmTokenSynced: Boolean = false

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    val userId: String get() = prefs.getString(KEY_USER_ID, "").orEmpty()
    val token: String get() = prefs.getString(KEY_TOKEN, "").orEmpty()
    val isLoggedIn: Boolean get() = userId.isNotEmpty() && token.isNotEmpty()

    var fcmToken: String
        get() = prefs.getString(KEY_FCM_TOKEN, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_FCM_TOKEN, value).apply()
            fcmTokenSynced = false
        }

    fun save(userId: String, token: String) {
        prefs.edit().putString(KEY_USER_ID, userId).putString(KEY_TOKEN, token).apply()
        fcmTokenSynced = false
    }

    /** Signs out locally and wipes this user's offline data. */
    fun clear() {
        prefs.edit().remove(KEY_USER_ID).remove(KEY_TOKEN).apply()
        fcmTokenSynced = false
        LocalDb.get().clearAll()
    }
}
