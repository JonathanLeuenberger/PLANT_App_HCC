package com.example.plant_app_hcc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar
import java.util.UUID

enum class CareTaskType(val id: String, val label: Int) {
    WATERING("watering", R.string.care_task_watering),
    LIGHT_CHECK("light_check", R.string.care_task_light_check),
    OTHER("other", R.string.care_task_other);

    companion object {
        private val watering = Regex("""^(water|watering|irrigate|irrigation)\b""", RegexOption.IGNORE_CASE)
        private val lightCheck = Regex(
            """^(light check|sunlight check|check (the )?(light|sunlight))\b""", RegexOption.IGNORE_CASE
        )

        @JvmStatic fun fromTitle(title: String): CareTaskType = when {
            watering.containsMatchIn(title.trim()) -> WATERING
            lightCheck.containsMatchIn(title.trim()) -> LIGHT_CHECK
            else -> OTHER
        }

        fun fromSaved(id: String, title: String): CareTaskType =
            entries.find { it.id == id } ?: fromTitle(title)
    }
}

data class CareEvent(
    val id: String,
    val title: String,
    val subtitle: String,
    val dateInMillis: Long = 0L,
    val iconRes: Int = R.drawable.ic_todo,
    var isCompleted: Boolean = false,
    val plantId: String = "",
    val type: CareTaskType = CareTaskType.OTHER,
    val completedDay: Int = 0
)

/** The calendar, to-do list and plant profiles all read the same saved tasks. */
object CareTaskManager {
    @JvmStatic @JvmOverloads
    fun addTask(
        context: Context,
        plantId: String,
        title: String,
        due: Long,
        type: CareTaskType = CareTaskType.fromTitle(title)
    ): CareEvent? =
        synchronized(UserGarden.preferences(context)) {
            if (title.isBlank() || title.trim().length > 120 || UserGarden.find(context, plantId) == null)
                return null
            val task = CareEvent("task_${UUID.randomUUID()}", title.trim(), "",
                startOfDay(due), plantId = plantId, type = type)
            save(context, read(context) + task)
            getTasksForPlant(context, plantId).find { it.id == task.id }
        }

    @JvmStatic fun completeTask(context: Context, taskId: String) {
        setCompleted(context, taskId, true)
    }

    @JvmStatic fun setCompleted(context: Context, taskId: String, completed: Boolean) =
        synchronized(UserGarden.preferences(context)) {
            val tasks = read(context)
            if (tasks.none { it.id == taskId && it.isCompleted != completed }) return
            val now = System.currentTimeMillis()
            val completedDay = if (completed) dayKey(now) else 0
            save(context, tasks.map {
                if (it.id == taskId) it.copy(isCompleted = completed, completedDay = completedDay) else it
            }, now)
        }

    internal fun tasksForChallenges(context: Context): List<CareEvent> =
        synchronized(UserGarden.preferences(context)) {
            val plantIds = UserGarden.plants(context).map { it.id }.toSet()
            read(context).filter { it.plantId in plantIds }
        }

    @JvmStatic fun getTasksForPlant(context: Context, plantId: String): List<CareEvent> =
        all(context).filter { it.plantId == plantId }
            .sortedWith(compareBy<CareEvent> { it.isCompleted }.thenBy { it.dateInMillis })

    fun getTasksForDate(context: Context, calendar: Calendar): List<CareEvent> {
        val start = startOfDay(calendar.timeInMillis)
        val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, 1) }
        return all(context).filter { !it.isCompleted && it.dateInMillis >= start && it.dateInMillis < end.timeInMillis }
    }

    fun getOverdueTasks(context: Context, today: Calendar = Calendar.getInstance()): List<CareEvent> =
        all(context).filter { !it.isCompleted && it.dateInMillis < startOfDay(today.timeInMillis) }

    fun getUpcomingTasks(context: Context, today: Calendar = Calendar.getInstance()): List<CareEvent> =
        all(context).filter { !it.isCompleted && it.dateInMillis >= startOfDay(today.timeInMillis) }

    internal fun removeForPlant(context: Context, plantId: String) {
        save(context, read(context).filterNot { it.plantId == plantId })
    }

    private fun all(context: Context): List<CareEvent> = synchronized(UserGarden.preferences(context)) {
        val plants = UserGarden.plants(context).associateBy { it.id }
        read(context).mapNotNull { task ->
            val plant = plants[task.plantId] ?: return@mapNotNull null
            task.copy(subtitle = plant.name + " • " + DateFormat.getDateInstance(DateFormat.MEDIUM)
                .format(java.util.Date(task.dateInMillis)))
        }.sortedBy { it.dateInMillis }
    }

    private fun read(context: Context): List<CareEvent> {
        val rows = JSONArray(UserGarden.preferences(context).getString("tasks", "[]") ?: "[]")
        return (0 until rows.length()).map { i ->
            val j = rows.getJSONObject(i)
            val title = j.getString("title")
            val type = CareTaskType.fromSaved(j.optString("type"), title)
            CareEvent(j.getString("id"), title, "", j.getLong("due"),
                iconRes = if (type == CareTaskType.WATERING) R.drawable.ic_watering_can else R.drawable.ic_todo,
                isCompleted = j.optBoolean("completed"), plantId = j.getString("plantId"),
                type = type, completedDay = j.optInt("completedDay"))
        }
    }

    private fun save(context: Context, tasks: List<CareEvent>, now: Long = System.currentTimeMillis()) {
        val rows = JSONArray()
        tasks.forEach { task ->
            rows.put(JSONObject().put("id", task.id).put("plantId", task.plantId)
                .put("title", task.title).put("due", task.dateInMillis).put("type", task.type.id)
                .put("completed", task.isCompleted).put("completedDay", task.completedDay))
        }
        val preferences = UserGarden.preferences(context)
        val editor = preferences.edit().putString("tasks", rows.toString())
        ChallengeStore.recordTaskProgress(preferences, editor, tasks, now)
        editor.apply()
    }

    internal fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun dayKey(time: Long): Int = Calendar.getInstance().apply { timeInMillis = time }.run {
        get(Calendar.YEAR) * 10_000 + (get(Calendar.MONTH) + 1) * 100 + get(Calendar.DAY_OF_MONTH)
    }
}
