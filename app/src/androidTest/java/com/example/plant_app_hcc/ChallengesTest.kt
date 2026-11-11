package com.example.plant_app_hcc

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.allOf
import com.example.plant_app_hcc.search.Species
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class ChallengesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val preferenceNames = listOf(ChallengeStore.PREFERENCES, UserGarden.PREFERENCES)
    private lateinit var saved: Map<String, Map<String, String>>

    @Before fun preserveAppData() {
        saved = preferenceNames.associateWith { name ->
            val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val original = preferences.all.mapValues { it.value as String }
            preferences.edit().clear().commit()
            original
        }
    }

    @After fun restoreAppData() {
        saved.forEach { (name, original) ->
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            original.forEach { (key, value) -> editor.putString(key, value) }
            editor.commit()
        }
    }

    @Test fun existingRewardsCarryOverAndRepeatingATaskDoesNotPayAgain() {
        val preferences = context.getSharedPreferences(ChallengeStore.PREFERENCES, Context.MODE_PRIVATE)
        preferences.edit()
            .putString("check_in", """{"startedAt":1,"checked":[0,1,2,3],"completed":true}""")
            .putString("light_check", """{"startedAt":1,"checked":[0,1,2],"completed":false}""")
            .putString("pot_purchases", """{"terracotta":15}""")
            .commit()
        val store = ChallengeStore(context)
        assertEquals(30, store.points())
        assertEquals(15, store.availablePoints())
        assertTrue(PotStyle.TERRACOTTA in store.ownedPots())
        assertFalse(store.progress().getValue(PlantChallenge.LIGHT_CHECK).completed)

        val plant = addPlant("Aloe")
        val watering = CareTaskManager.addTask(context, plant.id, "Water Aloe", dueIn(0))!!
        CareTaskManager.completeTask(context, watering.id)
        CareTaskManager.setCompleted(context, watering.id, false)
        CareTaskManager.completeTask(context, watering.id)
        assertEquals(30, ChallengeStore(context).points())
        assertEquals(15, store.availablePoints())
    }

    @Test fun carePlanRequiresTwoDifferentUpcomingDatesForOnePlant() {
        val aloe = addPlant("Aloe")
        val fern = addPlant("Fern")
        val store = ChallengeStore(context)
        CareTaskManager.addTask(context, aloe.id, "Water Aloe", dueIn(-1))
        CareTaskManager.addTask(context, aloe.id, "Water Aloe", dueIn(1))
        CareTaskManager.addTask(context, aloe.id, "Water Aloe again", dueIn(1))
        CareTaskManager.addTask(context, fern.id, "Water Fern", dueIn(2))
        CareTaskManager.addTask(context, aloe.id, "Check soil before watering", dueIn(3))
        assertEquals(ChallengeProgress(1, false), store.progress().getValue(PlantChallenge.CARE_PLAN))
        assertEquals(0, store.points())

        CareTaskManager.addTask(context, aloe.id, "Water Aloe", dueIn(4))
        assertEquals(ChallengeProgress(2, true), store.progress().getValue(PlantChallenge.CARE_PLAN))
        assertEquals(25, store.points())
        assertFalse(store.progress().getValue(PlantChallenge.WATER_PLANT).completed)
    }

    @Test fun todoCalendarAndPlantProfilesAwardChallengesAutomatically() {
        val plant = addPlant("Aloe")
        val today = dueIn(0)
        val watering = CareTaskManager.addTask(context, plant.id, "Water Aloe", today)!!
        CareTaskManager.addTask(context, plant.id, "Inspect window", today, CareTaskType.LIGHT_CHECK)
        val store = ChallengeStore(context)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.tab_challenges)).perform(click())
            onView(withId(R.id.challenge_completed)).perform(click())
            onView(withText(R.string.challenge_empty_completed)).check(matches(isDisplayed()))
            onView(withId(R.id.challenge_available)).perform(click())
            onView(withTagValue(`is`("challenge:check_in" as Any))).perform(scrollTo(), click())
            onView(withId(R.id.challenge_progress_label)).check(matches(withText("0 of 1 complete")))
            onView(isAssignableFrom(android.widget.CheckBox::class.java)).check(doesNotExist())
            onView(withText("Finish challenge")).check(doesNotExist())

            onView(withId(R.id.tab_to_do)).perform(click())
            onView(withContentDescription("Complete Water Aloe")).perform(click())
            onView(withId(R.id.tab_challenges)).perform(click())
            onView(withId(R.id.challenge_points)).check(matches(withText("30 points earned")))
            onView(withId(R.id.challenge_completed)).perform(click())
            onView(withTagValue(`is`("challenge:check_in" as Any)))
                .check(matches(withText(R.string.challenge_view_completed)))
            onView(withId(R.id.challenge_rewards)).perform(scrollTo(), click())
            onView(withText(org.hamcrest.Matchers.containsString("Sprout — unlocked")))
                .check(matches(isDisplayed()))
            onView(withText(R.string.challenge_close)).perform(click())

            onView(withId(R.id.tab_calendar)).perform(click())
            onView(withText("Water Aloe")).check(doesNotExist())
            onView(withContentDescription("Complete Inspect window")).perform(click())
            assertEquals(55, store.points())
            assertEquals(ChallengeProgress(1, false), store.progress().getValue(PlantChallenge.HEALTHY_HABITS))

            onView(withId(R.id.tab_my_garden)).perform(click())
            onView(withId(R.id.card_plant)).perform(click())
            onView(withId(R.id.plant_details_title)).check(matches(withText("Aloe")))
            assertEquals(75, store.points())
            CareTaskManager.completeTask(context, watering.id)
            scenario.recreate()
            assertEquals(75, ChallengeStore(context).points())
        }
    }

    private fun addPlant(name: String) = UserGarden.add(context, Species().apply { this.name = name })

    private fun dueIn(days: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

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
