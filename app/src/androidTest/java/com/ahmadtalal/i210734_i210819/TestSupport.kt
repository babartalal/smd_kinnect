package com.ahmadtalal.i210734_i210819

import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.FailureHandler
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shared plumbing for the Espresso tests: runtime permissions, test data
 * created through the real PHP web services, and a bounded wait helper.
 *
 * JUnit runs @Before/@Test/@After on the instrumentation thread (never the
 * main thread), so the blocking HTTP calls below are safe to make directly.
 */

/** A fresh account that exists on the server for the duration of one test. */
data class TestAccount(
    val userId: String,
    val token: String,
    val email: String,
    val password: String
)

/** Answer of one web service call made by a test. */
class BackendResponse(val code: Int, val json: JSONObject?) {
    val ok: Boolean get() = code in 200..299 && json?.optBoolean("ok") == true
    val error: String get() = json?.optString("error").orEmpty()
}

object TestBackend {

    const val PASSWORD = "Espresso#12345"

    /**
     * Grants the runtime permissions whose dialogs would otherwise cover the
     * screen (or block the network). Both only exist on newer Android
     * versions, so each grant is guarded by the API level.
     */
    fun grantRuntimePermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName

        // Android 13+: HomeFeedActivity asks for this as soon as it opens.
        if (Build.VERSION.SDK_INT >= 33) {
            grant(packageName, "android.permission.POST_NOTIFICATIONS")
        }
        // Android 17+: without it the app cannot reach XAMPP on the local network.
        if (Build.VERSION.SDK_INT >= 37) {
            grant(packageName, "android.permission.ACCESS_LOCAL_NETWORK")
        }
    }

    private fun grant(packageName: String, permission: String) {
        if (Build.VERSION.SDK_INT < 28) return
        try {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(packageName, permission)
        } catch (e: Throwable) {
            // Not a runtime permission on this system image: nothing to grant.
        }
    }

    /**
     * Signs up a brand-new account (unique email, so nothing depends on an
     * earlier run) and completes its profile, because the app sends accounts
     * without a username to EditProfileActivity instead of the home feed.
     */
    fun createAccount(): TestAccount {
        val unique = "${System.currentTimeMillis()}${(100..999).random()}"
        val email = "espresso_$unique@example.com"

        val signup = post(
            "auth/signup.php",
            JSONObject()
                .put("firstName", "Espresso")
                .put("lastName", "Tester")
                .put("email", email)
                .put("password", PASSWORD)
                .put("birthday", "1 January 2000")
                .put("gender", "Not Specified")
        )
        check(signup, "auth/signup.php")
        val data = signup.json!!.getJSONObject("data")
        val token = data.getString("token")
        val userId = data.getJSONObject("user").getString("userId")

        val update = post(
            "users/update.php",
            JSONObject()
                .put("name", "Espresso Tester")
                .put("username", "esp$unique")
                .put("bio", "Created by an Espresso test")
                .put("email", email),
            token
        )
        check(update, "users/update.php")

        return TestAccount(userId, token, email, PASSWORD)
    }

    /** Publishes a text-only post as [account] and returns its id. */
    fun createPost(account: TestAccount, text: String): String {
        val response = post(
            "posts/create.php",
            JSONObject().put("text", text).put("images", JSONArray()),
            account.token
        )
        check(response, "posts/create.php")
        return response.json!!.getJSONObject("data").getString("postId")
    }

    /** Texts of all comments the server holds for a post. */
    fun commentTexts(account: TestAccount, postId: String): List<String> {
        val response = request("GET", "comments/list.php?post_id=$postId", null, account.token)
        check(response, "comments/list.php")
        val comments = response.json!!.optJSONArray("data") ?: JSONArray()
        return (0 until comments.length()).map { comments.getJSONObject(it).optString("text") }
    }

    /** Raw login call, used to see how the server itself answers bad credentials. */
    fun login(email: String, password: String): BackendResponse =
        post("auth/login.php", JSONObject().put("email", email).put("password", password))

    fun post(endpoint: String, body: JSONObject, token: String = ""): BackendResponse =
        request("POST", endpoint, body, token)

    private fun check(response: BackendResponse, endpoint: String) {
        if (!response.ok) {
            throw AssertionError("Test setup: $endpoint failed (HTTP ${response.code}): ${response.error}")
        }
    }

    private fun request(method: String, endpoint: String, body: JSONObject?, token: String): BackendResponse {
        val url = Api.baseUrl + endpoint
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 8000
                connection.readTimeout = 20000
                connection.setRequestProperty("Accept", "application/json")
                if (token.isNotEmpty()) {
                    connection.setRequestProperty("X-Auth-Token", token)
                }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val json = try {
                    JSONObject(raw)
                } catch (e: Exception) {
                    null
                }
                return BackendResponse(code, json)
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            throw AssertionError("Cannot reach the Kinnect web services at $url. Is XAMPP running?", e)
        }
    }
}

/** Rethrows instead of Espresso's default handler, which dumps the view hierarchy on every miss. */
private val rethrow = FailureHandler { error, _ -> throw error }

/**
 * Wait strategy for the asynchronous app (OkHttp callbacks and polling lists):
 * re-checks every 250 ms until the view matched by [viewMatcher] satisfies
 * [state], and fails with the last Espresso error once [timeoutMs] is over.
 * It returns the moment the condition holds, so there are no fixed sleeps.
 */
fun waitForView(
    viewMatcher: Matcher<View>,
    state: Matcher<View> = isDisplayed(),
    timeoutMs: Long = 15000L
) {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    var lastError: Throwable? = null
    do {
        try {
            onView(viewMatcher).withFailureHandler(rethrow).check(matches(state))
            return
        } catch (e: Throwable) {
            // Not there yet (no match, wrong state, or no resumed activity during a transition).
            lastError = e
        }
        SystemClock.sleep(250)
    } while (SystemClock.elapsedRealtime() < deadline)

    throw AssertionError("Timed out after $timeoutMs ms waiting for $viewMatcher to be $state", lastError)
}
