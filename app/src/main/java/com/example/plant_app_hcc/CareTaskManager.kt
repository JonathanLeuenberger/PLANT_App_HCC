package com.example.plant_app_hcc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

enum class RecurrenceFrequency {
    WEEKLY,      // Repeats every week on selected days
    BIWEEKLY,    // Repeats every other week on selected days
    MONTHLY      // Repeats monthly on selected day of month
}

data class RecurringTask(
    val id: String,
    val plantId: String,
    val title: String,
    val frequency: RecurrenceFrequency,
    val daysOfWeek: List<Int> = emptyList(),
    val dayOfMonth: Int = 1,
    val startDateMillis: Long = System.currentTimeMillis()
)

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
    ): CareEvent? = synchronized(UserGarden.preferences(context)) {
        if (title.isBlank() || title.trim().length > 120 || UserGarden.find(context, plantId) == null)
            return null
        val task = CareEvent(
            id = "task_${UUID.randomUUID()}",
            title = title.trim(),
            subtitle = "",
            dateInMillis = startOfDay(due),
            iconRes = if (type == CareTaskType.WATERING) R.drawable.ic_watering_can else R.drawable.ic_todo,
            plantId = plantId,
            type = type
        )
        saveSingleTasks(context, readSingleTasks(context) + task)
        getTasksForPlant(context, plantId).find { it.id == task.id }
    }

    @JvmStatic fun addRecurringTask(
        context: Context,
        plantId: String,
        title: String,
        frequency: RecurrenceFrequency,
        daysOfWeek: List<Int>,
        dayOfMonth: Int,
        startDate: Long = System.currentTimeMillis()
    ): RecurringTask? = synchronized(UserGarden.preferences(context)) {
        if (title.isBlank() || title.trim().length > 120 || UserGarden.find(context, plantId) == null)
            return null
        val rule = RecurringTask(
            id = "rec_rule_${UUID.randomUUID()}",
            plantId = plantId,
            title = title.trim(),
            frequency = frequency,
            daysOfWeek = daysOfWeek,
            dayOfMonth = dayOfMonth.coerceIn(1, 31),
            startDateMillis = startOfDay(startDate)
        )
        saveRecurringRules(context, readRecurringRules(context) + rule)
        rule
    }

    @JvmStatic fun completeTask(context: Context, taskId: String) {
        setCompleted(context, taskId, true)
    }

    @JvmStatic fun setCompleted(context: Context, taskId: String, completed: Boolean) =
        synchronized(UserGarden.preferences(context)) {
            val tasks = readSingleTasks(context)
            val existing = tasks.find { it.id == taskId }
            val now = System.currentTimeMillis()
            val completedDay = if (completed) dayKey(now) else 0

            if (existing != null) {
                saveSingleTasks(context, tasks.map {
                    if (it.id == taskId) it.copy(isCompleted = completed, completedDay = completedDay) else it
                }, now)
            } else if (taskId.startsWith("rec_")) {
                val rules = readRecurringRules(context)
                val matchingRule = rules.find { taskId.startsWith("rec_${it.id}_") }
                val plantId = matchingRule?.plantId ?: ""
                val title = matchingRule?.title ?: ""
                val type = CareTaskType.fromTitle(title)

                val updated = tasks.filterNot { it.id == taskId } + CareEvent(
                    id = taskId,
                    title = title,
                    subtitle = "",
                    dateInMillis = parseDateFromRecId(taskId),
                    iconRes = if (type == CareTaskType.WATERING) R.drawable.ic_watering_can else R.drawable.ic_todo,
                    isCompleted = completed,
                    plantId = plantId,
                    type = type,
                    completedDay = completedDay
                )
                saveSingleTasks(context, updated, now)
            }
        }

    internal fun tasksForChallenges(context: Context): List<CareEvent> =
        synchronized(UserGarden.preferences(context)) {
            val plantIds = UserGarden.plants(context).map { it.id }.toSet()
            all(context, System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L, System.currentTimeMillis() + 60 * 24 * 60 * 60 * 1000L)
                .filter { it.plantId in plantIds }
        }

    @JvmStatic fun getTasksForPlant(context: Context, plantId: String): List<CareEvent> =
        all(context).filter { it.plantId == plantId }
            .sortedWith(compareBy<CareEvent> { it.isCompleted }.thenBy { it.dateInMillis })

    @JvmStatic fun getRecurringTasksForPlant(context: Context, plantId: String): List<RecurringTask> =
        readRecurringRules(context).filter { it.plantId == plantId }

    @JvmStatic fun removeRecurringTask(context: Context, ruleId: String) =
        synchronized(UserGarden.preferences(context)) {
            saveRecurringRules(context, readRecurringRules(context).filterNot { it.id == ruleId })
        }

    fun getTasksForDate(context: Context, calendar: Calendar): List<CareEvent> {
        val start = startOfDay(calendar.timeInMillis)
        val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, 1) }
        return all(context, start - 7 * 24 * 60 * 60 * 1000L, start + 30 * 24 * 60 * 60 * 1000L)
            .filter { !it.isCompleted && it.dateInMillis >= start && it.dateInMillis < end.timeInMillis }
    }

    fun getOverdueTasks(context: Context, today: Calendar = Calendar.getInstance()): List<CareEvent> {
        val todayStart = startOfDay(today.timeInMillis)
        val past7DaysStart = todayStart - 7 * 24 * 60 * 60 * 1000L
        return all(context, past7DaysStart, todayStart + 7 * 24 * 60 * 60 * 1000L)
            .filter { !it.isCompleted && it.dateInMillis >= past7DaysStart && it.dateInMillis < todayStart }
    }

    fun getUpcomingTasks(context: Context, today: Calendar = Calendar.getInstance()): List<CareEvent> {
        val todayStart = startOfDay(today.timeInMillis)
        val future7DaysEnd = todayStart + 8 * 24 * 60 * 60 * 1000L
        return all(context, todayStart - 1 * 24 * 60 * 60 * 1000L, future7DaysEnd)
            .filter { !it.isCompleted && it.dateInMillis >= todayStart && it.dateInMillis < future7DaysEnd }
    }

    internal fun removeForPlant(context: Context, plantId: String) {
        saveSingleTasks(context, readSingleTasks(context).filterNot { it.plantId == plantId })
        saveRecurringRules(context, readRecurringRules(context).filterNot { it.plantId == plantId })
    }

    private fun all(
        context: Context,
        rangeStart: Long = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L,
        rangeEnd: Long = System.currentTimeMillis() + 14 * 24 * 60 * 60 * 1000L
    ): List<CareEvent> = synchronized(UserGarden.preferences(context)) {
        val plants = UserGarden.plants(context).associateBy { it.id }
        val singleTasks = readSingleTasks(context)
        val singleTaskMap = singleTasks.associateBy { it.id }

        // Generate projected recurring instances
        val rules = readRecurringRules(context)
        val projectedEvents = mutableListOf<CareEvent>()

        val cal = Calendar.getInstance().apply { timeInMillis = rangeStart }
        val endCal = Calendar.getInstance().apply { timeInMillis = rangeEnd }

        while (!cal.after(endCal)) {
            val dayStart = startOfDay(cal.timeInMillis)
            rules.forEach { rule ->
                if (matchesRule(rule, cal)) {
                    val instanceId = "rec_${rule.id}_${dayStart}"
                    val override = singleTaskMap[instanceId]
                    val isCompleted = override?.isCompleted ?: false
                    val completedDay = override?.completedDay ?: 0
                    val type = CareTaskType.fromTitle(rule.title)
                    projectedEvents.add(
                        CareEvent(
                            id = instanceId,
                            title = rule.title,
                            subtitle = "",
                            dateInMillis = dayStart,
                            iconRes = if (type == CareTaskType.WATERING) R.drawable.ic_watering_can else R.drawable.ic_todo,
                            isCompleted = isCompleted,
                            plantId = rule.plantId,
                            type = type,
                            completedDay = completedDay
                        )
                    )
                }
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val concreteSingleTasks = singleTasks.filterNot { it.id.startsWith("rec_") }
        val combined = (concreteSingleTasks + projectedEvents).distinctBy { it.id }

        combined.mapNotNull { task ->
            val plant = plants[task.plantId] ?: return@mapNotNull null
            val dateLabel = formatDateLabel(task.dateInMillis)
            task.copy(subtitle = plant.name + " • " + dateLabel)
        }.sortedBy { it.dateInMillis }
    }

    private fun matchesRule(rule: RecurringTask, date: Calendar): Boolean {
        val targetStart = startOfDay(date.timeInMillis)
        val ruleStart = startOfDay(rule.startDateMillis)
        if (targetStart < ruleStart) return false

        return when (rule.frequency) {
            RecurrenceFrequency.WEEKLY -> {
                val dayOfWeek = date.get(Calendar.DAY_OF_WEEK)
                rule.daysOfWeek.contains(dayOfWeek)
            }
            RecurrenceFrequency.BIWEEKLY -> {
                val dayOfWeek = date.get(Calendar.DAY_OF_WEEK)
                if (!rule.daysOfWeek.contains(dayOfWeek)) return false

                val startWeek = Calendar.getInstance().apply {
                    timeInMillis = ruleStart
                    set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val targetWeek = (date.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val diffWeeks = ((targetWeek.timeInMillis - startWeek.timeInMillis) / (7 * 24 * 60 * 60 * 1000L)).toInt()
                diffWeeks >= 0 && diffWeeks % 2 == 0
            }
            RecurrenceFrequency.MONTHLY -> {
                val targetDayOfMonth = date.get(Calendar.DAY_OF_MONTH)
                val maxDay = date.getActualMaximum(Calendar.DAY_OF_MONTH)
                val expectedDay = rule.dayOfMonth.coerceAtMost(maxDay)
                targetDayOfMonth == expectedDay
            }
        }
    }

    private fun formatDateLabel(time: Long): String {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val target = Calendar.getInstance().apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val diffDays = ((target.timeInMillis - today.timeInMillis) / (24 * 60 * 60 * 1000L)).toInt()
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val dateFormatted = dateFormat.format(java.util.Date(time))

        return when (diffDays) {
            0 -> "Today ($dateFormatted)"
            -1 -> "Yesterday ($dateFormatted)"
            1 -> "Tomorrow ($dateFormatted)"
            else -> dateFormatted
        }
    }

    private fun parseDateFromRecId(taskId: String): Long {
        return try {
            taskId.substringAfterLast("_").toLong()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun readSingleTasks(context: Context): List<CareEvent> {
        val rows = JSONArray(UserGarden.preferences(context).getString("tasks", "[]") ?: "[]")
        return (0 until rows.length()).map { i ->
            val j = rows.getJSONObject(i)
            val title = j.getString("title")
            val type = CareTaskType.fromSaved(j.optString("type"), title)
            CareEvent(
                id = j.getString("id"),
                title = title,
                subtitle = "",
                dateInMillis = j.getLong("due"),
                iconRes = if (type == CareTaskType.WATERING) R.drawable.ic_watering_can else R.drawable.ic_todo,
                isCompleted = j.optBoolean("completed"),
                plantId = j.getString("plantId"),
                type = type,
                completedDay = j.optInt("completedDay")
            )
        }
    }

    private fun saveSingleTasks(context: Context, tasks: List<CareEvent>, now: Long = System.currentTimeMillis()) {
        val rows = JSONArray()
        tasks.forEach { task ->
            rows.put(JSONObject().put("id", task.id).put("plantId", task.plantId)
                .put("title", task.title).put("due", task.dateInMillis).put("type", task.type.id)
                .put("completed", task.isCompleted).put("completedDay", task.completedDay))
        }
        val preferences = UserGarden.preferences(context)
        val editor = preferences.edit().putString("tasks", rows.toString())
        val allTasks = tasksForChallenges(context)
        ChallengeStore.recordTaskProgress(preferences, editor, allTasks, now)
        editor.apply()
    }

    private fun readRecurringRules(context: Context): List<RecurringTask> {
        val rows = JSONArray(UserGarden.preferences(context).getString("recurring_rules", "[]") ?: "[]")
        return (0 until rows.length()).mapNotNull { i ->
            try {
                val j = rows.getJSONObject(i)
                val daysArr = j.optJSONArray("daysOfWeek") ?: JSONArray()
                val daysList = (0 until daysArr.length()).map { daysArr.getInt(it) }
                RecurringTask(
                    id = j.getString("id"),
                    plantId = j.getString("plantId"),
                    title = j.getString("title"),
                    frequency = RecurrenceFrequency.valueOf(j.getString("frequency")),
                    daysOfWeek = daysList,
                    dayOfMonth = j.optInt("dayOfMonth", 1),
                    startDateMillis = j.optLong("startDate", System.currentTimeMillis())
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun saveRecurringRules(context: Context, rules: List<RecurringTask>) {
        val rows = JSONArray()
        rules.forEach { rule ->
            val daysArr = JSONArray()
            rule.daysOfWeek.forEach { daysArr.put(it) }
            rows.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("plantId", rule.plantId)
                    .put("title", rule.title)
                    .put("frequency", rule.frequency.name)
                    .put("daysOfWeek", daysArr)
                    .put("dayOfMonth", rule.dayOfMonth)
                    .put("startDate", rule.startDateMillis)
            )
        }
        val preferences = UserGarden.preferences(context)
        val editor = preferences.edit().putString("recurring_rules", rows.toString())
        val allTasks = tasksForChallenges(context)
        ChallengeStore.recordTaskProgress(preferences, editor, allTasks, System.currentTimeMillis())
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
