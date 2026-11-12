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
class HomeCommentsTest {

    @Test
    fun testHomeFeedToCommentsFlow() {
        ActivityScenario.launch(HomeFeedActivity::class.java)
        onView(withId(R.id.postsRecyclerView)).check(matches(isDisplayed()))

        ActivityScenario.launch(CommentsActivity::class.java)

        onView(withId(R.id.commentInputText))
            .perform(typeText("Great post!"), closeSoftKeyboard())
        onView(withId(R.id.commentSendBtn)).perform(click())

        onView(withId(R.id.commentsRecyclerView)).check(matches(isDisplayed()))
    }
}
