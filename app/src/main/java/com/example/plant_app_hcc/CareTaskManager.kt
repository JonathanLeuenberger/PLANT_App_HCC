package com.example.plant_app_hcc

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class CareEvent(
    val id: String,
    val title: String,
    val subtitle: String,
    val dateInMillis: Long = 0L,
    val iconRes: Int = R.drawable.ic_watering_can,
    var isCompleted: Boolean = false
)

object CareTaskManager {
    private val completedEventIds = mutableSetOf<String>()

    fun completeTask(taskId: String) {
        completedEventIds.add(taskId)
    }

    fun isTaskCompleted(taskId: String): Boolean {
        return completedEventIds.contains(taskId)
    }

    fun getTasksForDate(calendar: Calendar): List<CareEvent> {
        val dateCopy = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayOfMonth = dateCopy.get(Calendar.DAY_OF_MONTH)
        val rawList = generateTasksForDate(dateCopy, dayOfMonth)
        return rawList.filter { !isTaskCompleted(it.id) }
    }

    /** Overdue tasks scheduled within the past 7 days (today - 7 to today - 1). */
    fun getOverdueTasks(today: Calendar = Calendar.getInstance()): List<CareEvent> {
        val overdue = mutableListOf<CareEvent>()
        for (daysAgo in 1..7) {
            val pastDate = (today.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayOfMonth = pastDate.get(Calendar.DAY_OF_MONTH)
            val dayTasks = generateTasksForDate(pastDate, dayOfMonth)
            overdue.addAll(dayTasks.filter { !isTaskCompleted(it.id) })
        }
        return overdue
    }

    /** Upcoming tasks scheduled within the future 7 days (today to today + 7). */
    fun getUpcomingTasks(today: Calendar = Calendar.getInstance()): List<CareEvent> {
        val upcoming = mutableListOf<CareEvent>()
        for (daysAhead in 0..7) {
            val futureDate = (today.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, daysAhead)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayOfMonth = futureDate.get(Calendar.DAY_OF_MONTH)
            val dayTasks = generateTasksForDate(futureDate, dayOfMonth)
            upcoming.addAll(dayTasks.filter { !isTaskCompleted(it.id) })
        }
        return upcoming
    }

    private fun formatDateLabel(date: Calendar): String {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val taskDate = (date.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffDays = ((taskDate.timeInMillis - today.timeInMillis) / (24 * 60 * 60 * 1000L)).toInt()
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val dateFormatted = dateFormat.format(date.time)

        return when (diffDays) {
            0 -> "Today ($dateFormatted)"
            -1 -> "Yesterday ($dateFormatted)"
            1 -> "Tomorrow ($dateFormatted)"
            else -> dateFormatted
        }
    }

    private fun generateTasksForDate(date: Calendar, dayOfMonth: Int): List<CareEvent> { // Placeholder for generating dummy tasks
        val userPlants = UserGarden.addedPlants
        val plantName = if (userPlants.isNotEmpty()) userPlants.first().name else "Aloe Vera"
        val year = date.get(Calendar.YEAR)
        val month = date.get(Calendar.MONTH) + 1
        val dateLabel = formatDateLabel(date)

        val list = mutableListOf<CareEvent>()
        if (dayOfMonth % 2 == 0) {
            list.add(
                CareEvent(
                    id = "event_${year}_${month}_${dayOfMonth}_water",
                    title = "Water $plantName",
                    subtitle = "$plantName • $dateLabel • 9:00 AM",
                    dateInMillis = date.timeInMillis,
                    iconRes = R.drawable.ic_watering_can
                )
            )
        } else {
            list.add(
                CareEvent(
                    id = "event_${year}_${month}_${dayOfMonth}_soil",
                    title = "Check Soil Moisture",
                    subtitle = "$plantName • $dateLabel • 10:00 AM",
                    dateInMillis = date.timeInMillis,
                    iconRes = R.drawable.ic_potted_plant
                )
            )
            list.add(
                CareEvent(
                    id = "event_${year}_${month}_${dayOfMonth}_pot",
                    title = "Change Pot",
                    subtitle = "$plantName • $dateLabel • 6:00 PM",
                    dateInMillis = date.timeInMillis,
                    iconRes = R.drawable.ic_potted_plant
                )
            )
        }
        return list
    }
}
