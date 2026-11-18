package com.ahmadtalal.i210734_i210819

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthNavigationTest {

    @Test
    fun testAuthAndNavigationFlow() {
        ActivityScenario.launch(LoginActivity::class.java)

        onView(withId(R.id.loginEmailInput))
            .perform(typeText("test@example.com"), closeSoftKeyboard())
        onView(withId(R.id.loginPasswordInput))
            .perform(typeText("password123"), closeSoftKeyboard())
        onView(withId(R.id.loginBtn)).perform(click())

        ActivityScenario.launch(HomeFeedActivity::class.java)
        onView(withId(R.id.homeFeedMenuBtn)).perform(click())

        ActivityScenario.launch(MenuActivity::class.java)
        onView(withId(R.id.logoutBtn)).perform(click())

        ActivityScenario.launch(LoginActivity::class.java)
        onView(withId(R.id.loginBtn)).check(matches(isDisplayed()))
    }
}
