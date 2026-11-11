package com.example.plant_app_hcc

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.plant_app_hcc.search.PlantCatalog
import org.hamcrest.Matchers.anything
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class GardenIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var saved: Map<String, *> = emptyMap<String, String>()

    @Before fun preserveGarden() {
        val prefs = context.getSharedPreferences(UserGarden.PREFERENCES, Context.MODE_PRIVATE)
        saved = prefs.all.toMap()
        prefs.edit().clear().commit()
    }

    @After fun restoreGarden() {
        val editor = context.getSharedPreferences(UserGarden.PREFERENCES, Context.MODE_PRIVATE).edit().clear()
        saved.forEach { (key, value) -> editor.putString(key, value as String) }
        editor.commit()
    }

    @Test fun separatePlantInstancesKeepCreditsOrderAndOwnTasks() {
        val catalog = PlantCatalog(context)
        try {
            val source = catalog.localSearch("aloe").first()
            val first = UserGarden.add(context, source, "Office aloe", "Desk")
            val second = UserGarden.add(context, source, "Bedroom aloe", "Window")
            assertNotEquals(first.id, second.id)
            assertEquals(source.id, first.speciesId)
            assertEquals(source.scientific, UserGarden.find(context, first.id)!!.scientific)
            assertEquals("Desk", UserGarden.find(context, first.id)!!.location)
            assertEquals(source.photoCredit, UserGarden.find(context, first.id)!!.photoCredit)
            assertEquals(source.photoSource, first.photoSource)
            assertEquals(source.dataCredits, first.dataCredits)
            val task = CareTaskManager.addTask(context, first.id, "Check office soil", System.currentTimeMillis())!!
            CareTaskManager.addTask(context, second.id, "Check bedroom leaves", System.currentTimeMillis())!!
            UserGarden.reorder(context, listOf(first.id, second.id))
            assertEquals(listOf(first.id, second.id), UserGarden.plants(context).map { it.id })
            assertEquals(listOf(task.id), CareTaskManager.getTasksForPlant(context, first.id).map { it.id })
            assertTrue(CareTaskManager.getTasksForPlant(context, first.id).first().subtitle.contains("Office aloe"))
            CareTaskManager.completeTask(context, task.id)
            assertTrue(CareTaskManager.getTasksForPlant(context, first.id).first().isCompleted)
            assertEquals(1, CareTaskManager.getUpcomingTasks(context).size)
            UserGarden.remove(context, first.id)
            assertEquals(listOf(second.id), UserGarden.plants(context).map { it.id })
            assertEquals(1, CareTaskManager.getTasksForPlant(context, second.id).size)
            assertTrue(CareTaskManager.getTasksForPlant(context, first.id).isEmpty())
            assertFalse(UserGarden.preferences(context).getString("tasks", "")!!.contains(task.id))
            assertNull(CareTaskManager.addTask(context, first.id, "Removed plant", System.currentTimeMillis()))
        } finally {
            catalog.close()
        }
    }

    @Test fun calendarAndTodoUseTheSameActualDueDatesAndCompletion() {
        val catalog = PlantCatalog(context)
        try {
            val plant = UserGarden.add(context, catalog.all.first())
            val today = Calendar.getInstance()
            val past = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -10) }
            val future = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 30) }
            CareTaskManager.addTask(context, plant.id, "Past check", past.timeInMillis)
            val now = CareTaskManager.addTask(context, plant.id, "Today check", today.timeInMillis)!!
            CareTaskManager.addTask(context, plant.id, "Future check", future.timeInMillis)
            assertEquals(listOf("Past check"), CareTaskManager.getOverdueTasks(context, today).map { it.title })
            assertEquals(listOf("Today check", "Future check"), CareTaskManager.getUpcomingTasks(context, today).map { it.title })
            assertEquals(listOf(now.id), CareTaskManager.getTasksForDate(context, today).map { it.id })
            assertEquals(listOf("Future check"), CareTaskManager.getTasksForDate(context, future).map { it.title })
            CareTaskManager.completeTask(context, now.id)
            assertTrue(CareTaskManager.getTasksForDate(context, today).isEmpty())
            assertEquals(listOf("Future check"), CareTaskManager.getUpcomingTasks(context, today).map { it.title })
        } finally {
            catalog.close()
        }
    }

    @Test fun searchAddTapTaskCompleteAndRemoveAcrossScreens() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            onView(withId(R.id.text_empty_garden)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_search)).perform(click())
            onView(withId(R.id.plant_search_input)).perform(replaceText("aloe"), closeSoftKeyboard())
            onData(anything()).inAdapterView(withId(R.id.plant_search_results)).atPosition(0).perform(click())
            onView(withId(R.id.garden_add)).perform(scrollTo(), click())
            scenario.recreate()
            onView(withId(R.id.garden_add)).check(doesNotExist())
            assertEquals(1, UserGarden.plants(context).size)
            onView(withId(R.id.garden_view)).perform(scrollTo(), click())
            // The inner card itself must open on a short tap, not just its outer holder.
            onView(withId(R.id.card_plant)).perform(click())
            onView(withId(R.id.plant_details_title)).check(matches(withText("Aloe vera")))
            onView(withId(R.id.garden_add_task)).perform(scrollTo(), click())
            onView(withId(R.id.garden_task_title)).perform(replaceText("Check office aloe soil"), closeSoftKeyboard())
            onView(withId(android.R.id.button1)).perform(click())
            onView(withText("Check office aloe soil")).check(matches(isDisplayed()))
            onView(withId(R.id.garden_view)).perform(scrollTo(), click())
            onView(withId(R.id.tab_to_do)).perform(click())
            onView(withText("Check office aloe soil")).check(matches(isDisplayed()))
            onView(withId(R.id.tab_calendar)).perform(click())
            onView(withText("Check office aloe soil")).check(matches(isDisplayed()))
            onView(withId(R.id.tab_my_garden)).perform(click())
            onView(withId(R.id.card_plant)).perform(click())
            onView(withText("Check office aloe soil")).perform(scrollTo(), click())
            scenario.recreate()
            onView(withText("Check office aloe soil")).check(matches(isChecked()))
            onView(withId(R.id.garden_view)).perform(scrollTo(), click())
            onView(withId(R.id.tab_to_do)).perform(click())
            onView(withText("Check office aloe soil")).check(doesNotExist())
            onView(withId(R.id.tab_my_garden)).perform(click())
            onView(withId(R.id.card_plant)).perform(click())
            onView(withId(R.id.garden_remove)).perform(scrollTo(), click())
            onView(withId(android.R.id.button2)).perform(click())
            assertEquals(1, UserGarden.plants(context).size)
            onView(withId(R.id.garden_remove)).perform(scrollTo(), click())
            onView(withId(android.R.id.button1)).perform(click())
            onView(withId(R.id.text_empty_garden)).check(matches(isDisplayed()))
            assertTrue(UserGarden.plants(context).isEmpty())
            scenario.recreate()
            onView(withId(R.id.text_empty_garden)).check(matches(isDisplayed()))
        }
    }

    @Test fun challengePointsBuyAReusablePotThatStaysOnTheChosenPlant() {
        val prefs = context.getSharedPreferences(ChallengeStore.PREFERENCES, Context.MODE_PRIVATE)
        val savedChallenges = prefs.all.mapValues { it.value as String }
        prefs.edit().clear().commit()
        try {
            val store = ChallengeStore(context)
            val catalog = PlantCatalog(context)
            val plant = try {
                UserGarden.add(context, catalog.all.first())
            } finally {
                catalog.close()
            }
            val watering = CareTaskManager.addTask(context, plant.id, "Water plant", System.currentTimeMillis())!!
            CareTaskManager.completeTask(context, watering.id)
            assertFalse(UserGarden.setPot(context, plant.id, PotStyle.TERRACOTTA))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withId(R.id.challenge_shop)).check(doesNotExist())
                onView(withId(R.id.tab_challenges)).perform(click())
                onView(withId(R.id.challenge_completed)).perform(click())
                onView(withId(R.id.challenge_shop)).perform(scrollTo(), click())
                onView(withId(R.id.bottom_bar_container)).check(matches(not(isDisplayed())))
                onView(withText("30 points to spend")).check(matches(isDisplayed()))
                onView(withText("Buy for 40 points")).check(matches(not(isEnabled())))
                onView(withText("Buy for 15 points")).perform(scrollTo(), click())
                onView(withText("15 points to spend")).check(matches(isDisplayed()))
                onView(withText(R.string.plant_shop_owned)).check(matches(not(isEnabled())))
                assertFalse(store.buyPot(PotStyle.TERRACOTTA))
                assertFalse(store.buyPot(PotStyle.STRIPED))
                assertEquals(30, store.points())
                assertEquals(15, ChallengeStore(context).availablePoints())

                scenario.recreate()
                onView(withText("15 points to spend")).check(matches(isDisplayed()))
                onView(withContentDescription("Navigate up")).perform(click())
                onView(withId(R.id.challenge_completed)).check(matches(isSelected()))
                onView(withId(R.id.challenge_shop)).perform(scrollTo(), click())
                pressBack()
                onView(withId(R.id.challenge_completed)).check(matches(isSelected()))
                onView(withId(R.id.tab_my_garden)).perform(click())
                onView(withId(R.id.card_plant)).perform(click())
                onView(withId(R.id.garden_change_pot)).perform(scrollTo(), click())
                onView(withText(R.string.pot_terracotta)).perform(click())
                scenario.recreate()
                assertEquals(PotStyle.TERRACOTTA.id, UserGarden.find(context, plant.id)?.potStyleId)
                onView(withId(R.id.garden_change_pot))
                    .check(matches(withText("Change pot · Terracotta pot")))
                pressBack()
                onView(withId(R.id.img_plant_pot)).check(matches(isDisplayed()))
            }
        } finally {
            val editor = prefs.edit().clear()
            savedChallenges.forEach { (key, value) -> editor.putString(key, value) }
            editor.commit()
        }
    }
}
