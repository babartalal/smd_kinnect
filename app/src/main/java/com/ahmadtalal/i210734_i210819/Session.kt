package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The signed-in user on this device. Replaces FirebaseAuth.currentUser.
 *
 * The session token from the login/signup web service is stored encrypted:
 * it is sealed with an AES-256-GCM key that lives in the Android Keystore
 * (the key never leaves secure hardware and cannot be read by other apps or
 * from a backup), and only the ciphertext is written to SharedPreferences.
 */
object Session {

    private const val PREFS = "KinnectSession"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_TOKEN_ENCRYPTED = "token_encrypted"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEYSTORE_ALIAS = "kinnect_session_key"

    private lateinit var prefs: SharedPreferences

    /** Decrypted once per app start and kept in memory for request headers. */
    @Volatile
    private var cachedToken: String? = null

    /** True once this device's FCM token has been sent to the server for the current login. */
    @Volatile
    var fcmTokenSynced: Boolean = false

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove("token").apply() // drop the plain-text token older builds stored
    }

    val userId: String get() = prefs.getString(KEY_USER_ID, "").orEmpty()

    val token: String
        get() {
            cachedToken?.let { return it }
            val decrypted = decrypt(prefs.getString(KEY_TOKEN_ENCRYPTED, "").orEmpty())
            cachedToken = decrypted
            return decrypted
        }

    val isLoggedIn: Boolean get() = userId.isNotEmpty() && token.isNotEmpty()

    var fcmToken: String
        get() = prefs.getString(KEY_FCM_TOKEN, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_FCM_TOKEN, value).apply()
            fcmTokenSynced = false
        }

    fun save(userId: String, token: String) {
        prefs.edit().putString(KEY_USER_ID, userId).putString(KEY_TOKEN_ENCRYPTED, encrypt(token)).apply()
        cachedToken = token
        fcmTokenSynced = false
    }

    /** Signs out locally and wipes this user's offline data. */
    fun clear() {
        prefs.edit().remove(KEY_USER_ID).remove(KEY_TOKEN_ENCRYPTED).apply()
        cachedToken = ""
        fcmTokenSynced = false
        LocalDb.get().clearAll()
    }

    // ---------- Android Keystore encryption ----------

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** Returns "iv:ciphertext", both Base64. */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    /** Empty string when there is nothing stored or it cannot be opened (treated as signed out). */
    private fun decrypt(stored: String): String {
        val parts = stored.split(":")
        if (parts.size != 2) return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
