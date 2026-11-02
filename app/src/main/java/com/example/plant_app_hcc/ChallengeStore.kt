package com.example.plant_app_hcc

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Stable IDs keep saved progress independent of display wording and list order. */
data class PlantChallenge(
    val id: String,
    val title: Int,
    val description: Int,
    val points: Int,
    val hours: Int,
    val steps: List<Int>
) {
    val durationMillis: Long get() = hours * 60L * 60L * 1000L
}

data class ChallengeProgress(
    val startedAt: Long = 0,
    val checked: Set<Int> = emptySet(),
    val completed: Boolean = false
) {
    fun expired(challenge: PlantChallenge, now: Long): Boolean =
        !completed && startedAt > 0 && now >= startedAt + challenge.durationMillis
}

/** Local challenge state only; garden, calendar and to-do data belong to their own screens. */
class ChallengeStore(
    private val preferences: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    )

    fun progress(challenge: PlantChallenge): ChallengeProgress = synchronized(preferences) {
        try {
            val json = JSONObject(preferences.getString(challenge.id, "{}") ?: "{}")
            val indices = json.optJSONArray("checked") ?: JSONArray()
            val checked = (0 until indices.length()).map { indices.optInt(it, -1) }
                .filter { it in challenge.steps.indices }.toSet()
            ChallengeProgress(json.optLong("startedAt"), checked, json.optBoolean("completed"))
        } catch (_: org.json.JSONException) {
            ChallengeProgress()
        }
    }

    fun start(challenge: PlantChallenge): Boolean = synchronized(preferences) {
        val old = progress(challenge)
        if (old.completed || (old.startedAt > 0 && !old.expired(challenge, now()))) return false
        // An expired attempt can restart, but its old checkmarks must not carry over.
        save(challenge, ChallengeProgress(startedAt = now().coerceAtLeast(1)))
        true
    }

    fun check(challenge: PlantChallenge, step: Int, checked: Boolean): Boolean =
        synchronized(preferences) {
            val old = progress(challenge)
            if (step !in challenge.steps.indices || old.startedAt == 0L || old.completed ||
                old.expired(challenge, now())) return false
            val updated = if (checked) old.checked + step else old.checked - step
            save(challenge, old.copy(checked = updated))
            true
        }

    fun finish(challenge: PlantChallenge): Boolean = synchronized(preferences) {
        val old = progress(challenge)
        if (old.startedAt == 0L || old.completed || old.expired(challenge, now()) ||
            old.checked.size != challenge.steps.size) return false
        save(challenge, old.copy(completed = true))
        true
    }

    // Points are derived from completed challenges, so Finish can never award twice.
    fun points(): Int = challenges.filter { progress(it).completed }.sumOf { it.points }

    private fun save(challenge: PlantChallenge, progress: ChallengeProgress) {
        val json = JSONObject().put("startedAt", progress.startedAt)
            .put("completed", progress.completed)
            .put("checked", JSONArray(progress.checked.sorted()))
        preferences.edit().putString(challenge.id, json.toString()).apply()
    }

    companion object {
        const val PREFERENCES = "plant_challenges_v1"
        val challenges = listOf(
            PlantChallenge("check_in", R.string.challenge_check_in, R.string.challenge_check_in_description,
                30, 24, listOf(R.string.challenge_step_soil, R.string.challenge_step_leaves,
                    R.string.challenge_step_location, R.string.challenge_step_sunlight)),
            PlantChallenge("know_plant", R.string.challenge_know_plant, R.string.challenge_know_plant_description,
                20, 72, listOf(R.string.challenge_step_choose, R.string.challenge_step_name,
                    R.string.challenge_step_guide)),
            PlantChallenge("light_check", R.string.challenge_light_check, R.string.challenge_light_description,
                25, 48, listOf(R.string.challenge_step_light_guide, R.string.challenge_step_observe_light,
                    R.string.challenge_step_compare_light)),
            PlantChallenge("healthy_habits", R.string.challenge_healthy_habits, R.string.challenge_habits_description,
                40, 168, listOf(R.string.challenge_step_drainage, R.string.challenge_step_pests,
                    R.string.challenge_step_growth, R.string.challenge_step_care_review)),
            PlantChallenge("care_plan", R.string.challenge_care_plan, R.string.challenge_plan_description,
                25, 72, listOf(R.string.challenge_step_priority, R.string.challenge_step_check_plan,
                    R.string.challenge_step_record_plan))
        )
    }
}
