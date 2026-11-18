package com.ahmadtalal.i210734_i210819

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.allOf
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Critical workflow 2: content. A signed-in user sees a post in the home
 * feed, opens its comments, writes a comment and sees it in the list.
 *
 * The account and its single post are created through the real web services
 * in setUp, then the app is signed in with Session.save, so the test does not
 * depend on any existing data.
 */
@RunWith(AndroidJUnit4::class)
class HomeCommentsTest {

    private lateinit var account: TestAccount
    private lateinit var postId: String
    private lateinit var postText: String
    private var scenario: ActivityScenario<HomeFeedActivity>? = null

    @Before
    fun setUp() {
        TestBackend.grantRuntimePermissions()
        Session.clear()

        account = TestBackend.createAccount()
        postText = "Espresso post ${System.currentTimeMillis()}"
        postId = TestBackend.createPost(account, postText)

        Session.save(account.userId, account.token)
    }

    @After
    fun tearDown() {
        scenario?.close()
        Session.clear()
    }

    /**
     * Proves the feed loads the user's post from posts/feed.php, the Comment
     * button opens that post's comments, and a comment sent from the app is
     * stored by comments/create.php and shown in the comments list.
     */
    @Test
    fun feedPost_openComments_sendComment_showsInList() {
        scenario = ActivityScenario.launch(HomeFeedActivity::class.java)

        // The feed is filled asynchronously; wait for our post's row to exist.
        // (It may be below the fold, so only its visibility is checked here.)
        val postRow = allOf(
            withParent(withId(R.id.postsRecyclerView)),
            hasDescendant(allOf(withId(R.id.postCaption), withText(postText)))
        )
        waitForView(postRow, withEffectiveVisibility(Visibility.VISIBLE))

        // Open the comments of exactly that post.
        onView(allOf(withId(R.id.commentBtn), isDescendantOfA(postRow)))
            .perform(scrollTo(), click())
        waitForView(withId(R.id.commentInputText))

        // Write and send a comment.
        val commentText = "Espresso comment ${System.currentTimeMillis()}"
        onView(withId(R.id.commentInputText)).perform(replaceText(commentText))
        closeSoftKeyboard()
        onView(withId(R.id.commentSendBtn)).perform(click())

        // The list is reloaded from the server after the write; wait for the new row.
        waitForView(
            allOf(
                withId(R.id.commentText),
                withText(commentText),
                isDescendantOfA(withId(R.id.commentsRecyclerView))
            )
        )

        // The screen reset itself for the next comment and counts the new one.
        onView(withId(R.id.commentInputText)).check(matches(withText("")))
        onView(withId(R.id.commentTotalCountTxt)).check(matches(withText("1 comments")))
        onView(withId(R.id.commentsRecyclerView)).check(matches(isDisplayed()))

        // And the comment really reached the server, not only the screen.
        assertTrue(
            "The comment should be stored on the server",
            TestBackend.commentTexts(account, postId).contains(commentText)
        )
    }
}
