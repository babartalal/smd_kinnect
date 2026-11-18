package com.ahmadtalal.i210734_i210819

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Critical workflow 1: authentication.
 *
 * Every test starts signed out with a brand-new account that was created
 * through the real web services (auth/signup.php + users/update.php), so no
 * test depends on data left behind by an earlier run. LoginActivity is
 * launched directly, which skips the 5 second splash screen.
 */
@RunWith(AndroidJUnit4::class)
class AuthNavigationTest {

    private lateinit var account: TestAccount
    private var scenario: ActivityScenario<LoginActivity>? = null

    @Before
    fun setUp() {
        TestBackend.grantRuntimePermissions()

        // Signed out, no cached data, and no "recent login" card from an earlier run.
        Session.clear()
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            .edit().clear().commit()

        account = TestBackend.createAccount()
    }

    @After
    fun tearDown() {
        scenario?.close()
        Session.clear()
    }

    /**
     * Proves the signed-out login screen shows its form, and that
     * Login -> Create account -> back returns to the login screen.
     */
    @Test
    fun loginScreen_opensCreateAccount_andComesBack() {
        scenario = ActivityScenario.launch(LoginActivity::class.java)

        onView(withId(R.id.loginEmailInput)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.loginPasswordInput)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.loginBtn)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.loginBtn)).check(matches(withText("Log in")))
        onView(withId(R.id.forgotPasswordLink)).perform(scrollTo()).check(matches(isDisplayed()))

        onView(withId(R.id.createNewAccBtn)).perform(scrollTo(), click())

        // The sign-up form is on screen.
        waitForView(withId(R.id.firstNameInput))
        onView(withId(R.id.lastNameInput)).check(matches(isDisplayed()))
        onView(withId(R.id.createAccBtn)).perform(scrollTo()).check(matches(isDisplayed()))

        // Its back arrow (in the fixed toolbar, so no scrolling) returns to the login screen.
        onView(withId(R.id.backToLoginBtn)).perform(click())
        waitForView(withId(R.id.loginBtn))
        onView(withId(R.id.createAccBtn)).check(doesNotExist())
    }

    /**
     * Proves a wrong password is rejected: the server answers 401, the app
     * stays on the login screen and no session is stored.
     */
    @Test
    fun login_withWrongPassword_staysOnLoginScreen() {
        val wrongPassword = "not-the-password"
        assertEquals(401, TestBackend.login(account.email, wrongPassword).code)

        scenario = ActivityScenario.launch(LoginActivity::class.java)
        submitLogin(account.email, wrongPassword)

        // The button is disabled while auth/login.php runs and enabled again once it answered.
        waitForView(withId(R.id.loginBtn), isEnabled())

        onView(withId(R.id.loginBtn)).check(matches(isDisplayed()))
        onView(withId(R.id.loginEmailInput)).check(matches(withText(account.email)))
        onView(withId(R.id.homeFeedMenuBtn)).check(doesNotExist())
        assertFalse("A failed login must not create a session", Session.isLoggedIn)
    }

    /**
     * Proves the main sign-in path: valid credentials are accepted by
     * auth/login.php, the session is saved and the home feed opens.
     */
    @Test
    fun login_withValidAccount_opensHomeFeed() {
        scenario = ActivityScenario.launch(LoginActivity::class.java)
        submitLogin(account.email, account.password)

        // LoginActivity replaces itself with HomeFeedActivity after the server answers.
        waitForView(withId(R.id.homeFeedMenuBtn))
        onView(withId(R.id.woym)).check(matches(isDisplayed()))
        onView(withId(R.id.loginBtn)).check(doesNotExist())

        assertTrue("A successful login must store the session", Session.isLoggedIn)
        assertEquals(account.userId, Session.userId)
    }

    private fun submitLogin(email: String, password: String) {
        waitForView(withId(R.id.loginBtn))
        // replaceText sets the text directly, so the keyboard's auto-correct cannot change it.
        onView(withId(R.id.loginEmailInput)).perform(scrollTo(), replaceText(email))
        onView(withId(R.id.loginPasswordInput)).perform(scrollTo(), replaceText(password))
        closeSoftKeyboard()
        onView(withId(R.id.loginBtn)).perform(scrollTo(), click())
    }
}
