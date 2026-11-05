package com.example.plant_app_hcc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar
import java.util.UUID

data class CareEvent(
    val id: String,
    val title: String,
    val subtitle: String,
    val dateInMillis: Long = 0L,
    val iconRes: Int = R.drawable.ic_todo,
    var isCompleted: Boolean = false,
    val plantId: String = ""
)

/** The calendar, to-do list and plant profiles all read the same saved tasks. */
object CareTaskManager {
    @JvmStatic fun addTask(context: Context, plantId: String, title: String, due: Long): CareEvent? =
        synchronized(UserGarden.preferences(context)) {
            if (title.isBlank() || title.trim().length > 120 || UserGarden.find(context, plantId) == null)
                return null
            val task = CareEvent("task_${UUID.randomUUID()}", title.trim(), "",
                startOfDay(due), plantId = plantId)
            save(context, read(context) + task)
            getTasksForPlant(context, plantId).find { it.id == task.id }
        }

    @JvmStatic fun completeTask(context: Context, taskId: String) {
        setCompleted(context, taskId, true)
    }

    @JvmStatic fun setCompleted(context: Context, taskId: String, completed: Boolean) =
        synchronized(UserGarden.preferences(context)) {
            save(context, read(context).map {
                if (it.id == taskId) it.copy(isCompleted = completed) else it
            })
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
            CareEvent(j.getString("id"), j.getString("title"), "", j.getLong("due"),
                isCompleted = j.optBoolean("completed"), plantId = j.getString("plantId"))
        }
    }

    private fun save(context: Context, tasks: List<CareEvent>) {
        val rows = JSONArray()
        tasks.forEach { task ->
            rows.put(JSONObject().put("id", task.id).put("plantId", task.plantId)
                .put("title", task.title).put("due", task.dateInMillis).put("completed", task.isCompleted))
        }
        UserGarden.preferences(context).edit().putString("tasks", rows.toString()).apply()
    }

    private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
