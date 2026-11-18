package com.example.plant_app_hcc

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.plant_app_hcc.databinding.FragmentCalendarBinding
import com.example.plant_app_hcc.databinding.ItemCalendarDayBinding
import com.example.plant_app_hcc.databinding.ItemCalendarEventBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MonthDay(
    val dayNumber: Int, // 0 for empty padding cell
    val dateCalendar: Calendar?,
    val hasTasks: Boolean,
    val isSelected: Boolean
)

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val eventsList = mutableListOf<CareEvent>()
    private val monthDaysList = mutableListOf<MonthDay>()

    private lateinit var taskAdapter: EventAdapter
    private lateinit var monthAdapter: MonthDayAdapter

    private var displayCalendar: Calendar = Calendar.getInstance()
    private var selectedCalendar: Calendar = Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Task RecyclerView
        binding.dayView.layoutManager = LinearLayoutManager(requireContext())
        taskAdapter = EventAdapter(eventsList) { completedEvent ->
            CareTaskManager.completeTask(requireContext(), completedEvent.id)
            val index = eventsList.indexOf(completedEvent)
            if (index != -1) {
                eventsList.removeAt(index)
                taskAdapter.notifyItemRemoved(index)
                updateDailyCareHeader(selectedCalendar)
                refreshMonthGrid()
            }
        }
        binding.dayView.adapter = taskAdapter

        // Setup Month Grid (7 columns for Sun - Sat)
        binding.gridCalendarMonth.layoutManager = GridLayoutManager(requireContext(), 7)
        monthAdapter = MonthDayAdapter(monthDaysList) { selectedDay ->
            if (selectedDay.dateCalendar != null) {
                selectedCalendar = (selectedDay.dateCalendar.clone() as Calendar)
                refreshMonthGrid()
                loadEventsForDate(selectedCalendar)
            }
        }
        binding.gridCalendarMonth.adapter = monthAdapter

        // Month Navigation
        binding.btnPrevMonth.setOnClickListener {
            displayCalendar.add(Calendar.MONTH, -1)
            refreshMonthGrid()
        }

        binding.btnNextMonth.setOnClickListener {
            displayCalendar.add(Calendar.MONTH, 1)
            refreshMonthGrid()
        }

        refreshMonthGrid()
        loadEventsForDate(selectedCalendar)
    }

    override fun onResume() {
        super.onResume()
        refreshMonthGrid()
        loadEventsForDate(selectedCalendar)
    }

    private fun refreshMonthGrid() {
        monthDaysList.clear()

        // Set Month / Year title
        val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        binding.textMonthYear.text = monthYearFormat.format(displayCalendar.time)

        val cal = (displayCalendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val targetMonth = cal.get(Calendar.MONTH)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...

        // Add leading empty cells
        for (i in 1 until firstDayOfWeek) {
            monthDaysList.add(MonthDay(0, null, hasTasks = false, isSelected = false))
        }

        // Fill month days
        while (cal.get(Calendar.MONTH) == targetMonth) {
            val dayNumber = cal.get(Calendar.DAY_OF_MONTH)
            val dateCal = cal.clone() as Calendar

            val hasTasks = CareTaskManager.getTasksForDate(requireContext(), dateCal).isNotEmpty()
            val isSelected = isSameDay(dateCal, selectedCalendar)

            monthDaysList.add(MonthDay(dayNumber, dateCal, hasTasks, isSelected))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        monthAdapter.notifyDataSetChanged()
    }

    private fun loadEventsForDate(calendar: Calendar) {
        eventsList.clear()
        eventsList.addAll(CareTaskManager.getTasksForDate(requireContext(), calendar))
        taskAdapter.notifyDataSetChanged()
        updateDailyCareHeader(calendar)
    }

    private fun updateDailyCareHeader(calendar: Calendar) {
        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
        val dateStr = dateFormat.format(calendar.time)
        binding.textDailyCareTitle.text = "Daily Care Tasks - $dateStr"

        if (eventsList.isEmpty()) {
            binding.textEmptyDailyCare.visibility = View.VISIBLE
        } else {
            binding.textEmptyDailyCare.visibility = View.GONE
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Month Grid Adapter
    class MonthDayAdapter(
        private val days: List<MonthDay>,
        private val onDaySelected: (MonthDay) -> Unit
    ) : RecyclerView.Adapter<MonthDayAdapter.MonthDayViewHolder>() {

        class MonthDayViewHolder(val binding: ItemCalendarDayBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthDayViewHolder {
            val binding = ItemCalendarDayBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return MonthDayViewHolder(binding)
        }

        override fun onBindViewHolder(holder: MonthDayViewHolder, position: Int) {
            val day = days[position]

            if (day.dayNumber == 0) {
                holder.binding.textDayNumber.text = ""
                holder.binding.textDayNumber.background = null
                holder.binding.viewTaskUnderline.visibility = View.GONE
                holder.binding.root.setOnClickListener(null)
                return
            }

            holder.binding.textDayNumber.text = day.dayNumber.toString()

            // Green underline bar below date number if day has tasks
            if (day.hasTasks) {
                holder.binding.viewTaskUnderline.visibility = View.VISIBLE
            } else {
                holder.binding.viewTaskUnderline.visibility = View.GONE
            }

            // Highlight selected day
            if (day.isSelected) {
                holder.binding.textDayNumber.background = ContextCompat.getDrawable(
                    holder.itemView.context,
                    R.drawable.bg_calendar_selected_day
                )
                holder.binding.textDayNumber.setTextColor(Color.WHITE)
            } else {
                holder.binding.textDayNumber.background = null
                holder.binding.textDayNumber.setTextColor(0xFF1F2F27.toInt())
            }

            holder.binding.root.setOnClickListener {
                onDaySelected(day)
            }
        }

        override fun getItemCount(): Int = days.size
    }

    // Task List Adapter
    class EventAdapter(
        private val events: List<CareEvent>,
        private val onItemCompleted: (CareEvent) -> Unit
    ) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

        class EventViewHolder(val binding: ItemCalendarEventBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
            val binding = ItemCalendarEventBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return EventViewHolder(binding)
        }

        override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
            val event = events[position]
            holder.binding.textEventTitle.text = event.title
            holder.binding.textEventSubtitle.text = event.subtitle
            holder.binding.imgEventIcon.setImageResource(event.iconRes)

            holder.binding.checkEvent.isChecked = event.isCompleted
            holder.binding.checkEvent.contentDescription =
                holder.itemView.context.getString(R.string.care_task_complete, event.title)
            holder.binding.checkEvent.setOnClickListener {
                onItemCompleted(event)
            }

            holder.binding.root.setOnLongClickListener {
                onItemCompleted(event)
                true
            }
        }

        override fun getItemCount(): Int = events.size
    }
}
