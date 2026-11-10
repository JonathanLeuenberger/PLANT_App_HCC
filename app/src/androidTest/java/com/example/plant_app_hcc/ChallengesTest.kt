package com.example.plant_app_hcc

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChallengesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun progressPersistsAndPointsAreAwardedOnlyOnce() {
        val prefs = context.getSharedPreferences("challenge_store_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val challenge = ChallengeStore.challenges.first()
            val store = ChallengeStore(prefs) { 1_000L }
            assertFalse(store.check(challenge, 0, true))
            assertFalse(store.finish(challenge))
            assertTrue(store.start(challenge))
            assertTrue(store.check(challenge, 0, true))
            assertFalse(store.start(challenge)) // Continuing does not erase an attempt.
            assertFalse(store.check(challenge, 999, true))
            assertFalse(store.finish(challenge))
            val reopened = ChallengeStore(prefs) { 2_000L }
            assertEquals(setOf(0), reopened.progress(challenge).checked)
            challenge.steps.indices.forEach { reopened.check(challenge, it, true) }
            assertTrue(reopened.finish(challenge))
            assertFalse(store.finish(challenge))
            assertFalse(reopened.start(challenge))
            assertFalse(reopened.check(challenge, 0, false))
            assertEquals(challenge.points, ChallengeStore(prefs).points())
            assertEquals(challenge.steps.size, store.progress(challenge).checked.size)
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun expiredAttemptsCannotEarnPointsAndRetryResetsSteps() {
        val prefs = context.getSharedPreferences("challenge_expiry_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            var time = 10_000L
            val store = ChallengeStore(prefs) { time }
            val challenge = ChallengeStore.challenges.first()
            store.start(challenge)
            challenge.steps.indices.forEach { store.check(challenge, it, true) }
            time += challenge.durationMillis // The exact deadline is expired, not a free extra minute.
            assertTrue(store.progress(challenge).expired(challenge, time))
            assertFalse(store.finish(challenge))
            assertFalse(store.check(challenge, 0, false))
            assertEquals(0, store.points())
            assertTrue(store.start(challenge))
            assertTrue(store.progress(challenge).checked.isEmpty())
            assertEquals(time, store.progress(challenge).startedAt)
            assertFalse(store.finish(challenge))
            challenge.steps.indices.forEach { store.check(challenge, it, true) }
            assertTrue(store.finish(challenge))
            assertEquals(30, store.points())
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun homeChecklistBackRecreationCompletionAndRewards() {
        // Preserve the user's real challenge progress when this local UI test ends.
        val prefs = context.getSharedPreferences(ChallengeStore.PREFERENCES, Context.MODE_PRIVATE)
        val saved = prefs.all.mapValues { it.value as String }
        prefs.edit().clear().commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.window.addFlags(
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    )
                }
                onView(withId(R.id.tab_challenges)).perform(click())
                onView(withId(R.id.challenge_completed)).perform(click())
                onView(withText(R.string.challenge_empty_completed)).check(matches(isDisplayed()))
                onView(withId(R.id.challenge_available)).perform(click())
                onView(withTagValue(`is`("challenge:check_in" as Any))).perform(scrollTo(), click())
                onView(withId(R.id.challenge_finish)).check(matches(not(isEnabled())))
                onView(withText(R.string.challenge_step_soil)).perform(scrollTo(), click())
                pressBack()
                onView(withTagValue(`is`("challenge:check_in" as Any)))
                    .check(matches(withText(R.string.challenge_continue))).perform(scrollTo(), click())
                scenario.recreate()
                onView(withText(R.string.challenge_step_soil)).check(matches(isChecked()))
                listOf(R.string.challenge_step_leaves, R.string.challenge_step_location,
                    R.string.challenge_step_sunlight).forEach {
                    onView(withText(it)).perform(scrollTo(), click())
                }
                onView(withId(R.id.challenge_finish)).perform(scrollTo(), click())
                onView(withId(R.id.challenge_points)).check(matches(withText("30 points earned")))
                onView(withId(R.id.challenge_finish)).check(matches(not(isEnabled())))
                pressBack()
                onView(withId(R.id.challenge_completed)).perform(click())
                onView(withTagValue(`is`("challenge:check_in" as Any)))
                    .check(matches(withText(R.string.challenge_view_completed)))
                onView(withId(R.id.challenge_rewards)).perform(scrollTo(), click())
                onView(withText(org.hamcrest.Matchers.containsString("Sprout — unlocked")))
                    .check(matches(isDisplayed()))
                onView(withText(R.string.challenge_close)).perform(click())
                scenario.recreate()
                onView(withTagValue(`is`("challenge:check_in" as Any)))
                    .check(matches(withText(R.string.challenge_view_completed)))
                onView(withId(R.id.tab_my_garden)).perform(click())
                onView(withId(R.id.title_my_garden)).check(matches(isDisplayed()))
                onView(withId(R.id.tab_challenges)).perform(click())
                onView(withId(R.id.challenge_points)).check(matches(withText("30 points earned")))
            }
        } finally {
            val editor = prefs.edit().clear()
            saved.forEach { (key, value) -> editor.putString(key, value) }
            editor.commit()
        }
    }

    @Test fun bottomHubSwitchesSectionsWithoutDuplicateScreens() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.tab_my_garden)).check(matches(isSelected()))
            onView(withId(R.id.tab_challenges)).perform(click())
            onView(withId(R.id.tab_challenges)).check(matches(isSelected()))
            onView(withId(R.id.tab_calendar)).perform(click())
            onView(allOf(withId(R.id.calendarView), isAssignableFrom(android.widget.CalendarView::class.java)))
                .check(matches(isDisplayed()))
            onView(withId(R.id.tab_calendar)).check(matches(isSelected()))
            onView(withId(R.id.tab_to_do)).perform(click())
            onView(withId(R.id.text_upcoming_header)).check(matches(isDisplayed()))
            onView(withId(R.id.tab_to_do)).check(matches(isSelected()))
            onView(withId(R.id.tab_challenges)).perform(click())
            onView(withId(R.id.challenge_tabs)).check(matches(isDisplayed()))
            onView(withId(R.id.tab_challenges)).perform(click())
            pressBack()
            onView(withId(R.id.title_my_garden)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_search)).perform(click())
            onView(withId(R.id.bottom_bar_container)).check(matches(not(isDisplayed())))
            pressBack()
            onView(withId(R.id.tab_my_garden)).check(matches(isDisplayed()))
            onView(withId(R.id.tab_my_garden)).check(matches(isSelected()))
        }
    }
}
