package com.example.plant_app_hcc

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Stable IDs preserve rewards earned with earlier versions of the app. */
enum class PlantChallenge(val id: String, val title: Int, val description: Int, val points: Int, val goal: Int) {
    WATER_PLANT("check_in", R.string.challenge_water_plant, R.string.challenge_water_description, 30, 1),
    KNOW_PLANT("know_plant", R.string.challenge_know_plant, R.string.challenge_know_plant_description, 20, 1),
    LIGHT_CHECK("light_check", R.string.challenge_light_check, R.string.challenge_light_description, 25, 1),
    HEALTHY_HABITS("healthy_habits", R.string.challenge_healthy_habits, R.string.challenge_habits_description, 40, 3),
    CARE_PLAN("care_plan", R.string.challenge_care_plan, R.string.challenge_plan_description, 25, 2),
    GROWING_COLLECTION("growing_collection", R.string.challenge_collection, R.string.challenge_collection_description, 25, 3),
    WATERING_ROUND("watering_round", R.string.challenge_watering_round, R.string.challenge_watering_round_description, 30, 2),
    GARDEN_SCHEDULE("garden_schedule", R.string.challenge_garden_schedule, R.string.challenge_garden_schedule_description, 25, 3),
    WELL_ROUNDED_CARE("well_rounded_care", R.string.challenge_care_variety, R.string.challenge_care_variety_description, 35, 3)
}

data class ChallengeProgress(val count: Int, val completed: Boolean)

/** Combines earned achievements with the existing points and pot purchase history. */
class ChallengeStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gardenPreferences = UserGarden.preferences(appContext)

    fun progress(): Map<PlantChallenge, ChallengeProgress> = synchronized(gardenPreferences) {
        val counts = currentCounts(appContext)
        val earned = earnedChallenges()
        PlantChallenge.entries.associateWith { challenge ->
            val completed = challenge in earned
            ChallengeProgress(if (completed) challenge.goal else counts.getOrDefault(challenge, 0), completed)
        }
    }

    fun points(): Int = earnedChallenges().sumOf { it.points }

    fun availablePoints(): Int = synchronized(preferences) { availablePoints(purchases()) }

    fun ownedPots(): List<PotStyle> = synchronized(preferences) {
        val purchases = purchases()
        PotStyle.entries.filter { it == PotStyle.STARTER || purchases.has(it.id) }
    }

    fun ownedBackgrounds(): List<GardenBackground> = synchronized(preferences) {
        val purchases = purchases()
        GardenBackground.entries.filter { it.price == 0 || purchases.has(it.id) }
    }

    fun buyPot(pot: PotStyle): Boolean = buy(pot.id, pot.price)

    fun buyBackground(background: GardenBackground): Boolean = buy(background.id, background.price)

    private fun buy(id: String, price: Int): Boolean = synchronized(preferences) {
        val purchases = purchases()
        if (price == 0 || purchases.has(id) || availablePoints(purchases) < price) return false
        // Save ownership and the price paid together, even if the catalog price changes later.
        purchases.put(id, price)
        preferences.edit().putString(POT_PURCHASES, purchases.toString()).apply()
        true
    }

    private fun earnedChallenges(): List<PlantChallenge> {
        val automatic = earnedIds(gardenPreferences)
        return PlantChallenge.entries.filter { it.id in automatic || previouslyCompleted(it) }
    }

    private fun previouslyCompleted(challenge: PlantChallenge): Boolean {
        val saved = preferences.getString(challenge.id, null) ?: return false
        return try {
            JSONObject(saved).optBoolean("completed")
        } catch (_: org.json.JSONException) {
            false
        }
    }

    private fun purchases() = JSONObject(preferences.getString(POT_PURCHASES, "{}") ?: "{}")

    private fun availablePoints(purchases: JSONObject): Int {
        val spent = purchases.keys().asSequence().sumOf { purchases.optInt(it) }
        return (points() - spent).coerceAtLeast(0)
    }

    companion object {
        const val PREFERENCES = "plant_challenges_v1"
        private const val POT_PURCHASES = "pot_purchases"
        private const val EARNED = "earned_challenges"

        @JvmStatic fun recordPlantViewed(context: Context, plantId: String) {
            val preferences = UserGarden.preferences(context)
            synchronized(preferences) {
                if (UserGarden.find(context, plantId) == null) return
                val old = earnedIds(preferences)
                val id = PlantChallenge.KNOW_PLANT.id
                if (id in old) return
                preferences.edit().putString(EARNED, JSONArray(old + id).toString()).apply()
            }
        }

        internal fun syncProgress(context: Context) {
            val preferences = UserGarden.preferences(context)
            synchronized(preferences) {
                val counts = currentCounts(context)
                val editor = preferences.edit()
                recordProgress(preferences, editor, counts)
                editor.apply()
            }
        }

        internal fun recordPlantCount(
            preferences: SharedPreferences,
            editor: SharedPreferences.Editor,
            plantCount: Int
        ) {
            if (plantCount >= PlantChallenge.GROWING_COLLECTION.goal) {
                recordProgress(preferences, editor, mapOf(PlantChallenge.GROWING_COLLECTION to plantCount))
            }
        }

        /** Called under the garden lock so the task change and any rewards are saved together. */
        internal fun recordTaskProgress(
            preferences: SharedPreferences,
            editor: SharedPreferences.Editor,
            tasks: List<CareEvent>,
            now: Long
        ) = recordProgress(preferences, editor, taskCounts(tasks, now))

        private fun recordProgress(
            preferences: SharedPreferences,
            editor: SharedPreferences.Editor,
            counts: Map<PlantChallenge, Int>
        ) {
            val old = earnedIds(preferences)
            val new = counts.filter { (challenge, count) -> count >= challenge.goal }
                .keys.map { it.id }
            val updated = old + new
            if (updated != old) editor.putString(EARNED, JSONArray(updated).toString())
        }

        private fun earnedIds(preferences: SharedPreferences): Set<String> {
            val rows = JSONArray(preferences.getString(EARNED, "[]") ?: "[]")
            return (0 until rows.length()).map { rows.getString(it) }.toSet()
        }

        private fun currentCounts(context: Context): Map<PlantChallenge, Int> {
            val plantCount = UserGarden.plants(context).size
            return taskCounts(CareTaskManager.tasksForChallenges(context), System.currentTimeMillis()) +
                (PlantChallenge.GROWING_COLLECTION to plantCount)
        }

        private fun taskCounts(tasks: List<CareEvent>, now: Long): Map<PlantChallenge, Int> {
            val completed = tasks.filter { it.isCompleted }
            val watering = tasks.filter { it.type == CareTaskType.WATERING }
            val today = CareTaskManager.startOfDay(now)
            val upcoming = tasks.filter { !it.isCompleted && it.dateInMillis >= today }
            val wateringDates = upcoming.asSequence()
                .filter { it.type == CareTaskType.WATERING }
                .groupBy({ it.plantId }, { it.dateInMillis })
            val completedTypes = completed.groupBy({ it.plantId }, { it.type })
            return mapOf(
                PlantChallenge.WATER_PLANT to if (watering.any { it.isCompleted }) 1 else 0,
                PlantChallenge.LIGHT_CHECK to if (completed.any { it.type == CareTaskType.LIGHT_CHECK }) 1 else 0,
                PlantChallenge.HEALTHY_HABITS to completed.map { it.completedDay }.filter { it > 0 }.distinct().size,
                PlantChallenge.CARE_PLAN to (wateringDates.values.maxOfOrNull { it.distinct().size } ?: 0),
                PlantChallenge.WATERING_ROUND to watering.filter { it.isCompleted }.distinctBy { it.plantId }.size,
                PlantChallenge.GARDEN_SCHEDULE to upcoming.distinctBy { it.plantId }.size,
                PlantChallenge.WELL_ROUNDED_CARE to (completedTypes.values.maxOfOrNull { it.distinct().size } ?: 0)
            )
        }
    }
}
