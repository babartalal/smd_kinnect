package com.ahmadtalal.i210734_i210819

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

object NotificationUtil {

    private const val FCM_SERVER_KEY = "YOUR_FCM_SERVER_KEY_PLACEHOLDER"
    private const val FCM_URL = "https://fcm.googleapis.com/fcm/send"

    private val client = OkHttpClient()

    fun sendPushNotification(recipientToken: String, title: String, message: String) {
        if (recipientToken.isEmpty()) return

        try {
            val json = JSONObject()
            val notification = JSONObject()
            notification.put("title", title)
            notification.put("body", message)

            val data = JSONObject()
            data.put("title", title)
            data.put("message", message)

            json.put("to", recipientToken)
            json.put("notification", notification)
            json.put("data", data)

            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(FCM_URL)
                .post(requestBody)
                .addHeader("Authorization", "key=$FCM_SERVER_KEY")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("NotificationUtil", "FCM send failed: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.d("NotificationUtil", "FCM send response: ${response.code}")
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
