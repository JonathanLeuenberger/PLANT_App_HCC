package com.example.plant_app_hcc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.plant_app_hcc.databinding.FragmentCalendarBinding
import com.example.plant_app_hcc.databinding.ItemCalendarEventBinding
import java.util.Calendar



class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private val eventsList = mutableListOf<CareEvent>()
    private lateinit var adapter: EventAdapter

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

        // Setup RecyclerView
        binding.dayView.layoutManager = LinearLayoutManager(requireContext())
        adapter = EventAdapter(eventsList) { completedEvent ->
            CareTaskManager.completeTask(requireContext(), completedEvent.id)
            val index = eventsList.indexOf(completedEvent)
            if (index != -1) {
                eventsList.removeAt(index)
                adapter.notifyItemRemoved(index)
            }
        }
        binding.dayView.adapter = adapter

        // Handle date selection in CalendarView
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance().apply {
                set(year, month, dayOfMonth)
            }
            loadEventsForDate(calendar)
        }

        // Load initial events for today
        loadEventsForDate(Calendar.getInstance())
    }

    private fun loadEventsForDate(calendar: Calendar) {
        eventsList.clear()
        eventsList.addAll(CareTaskManager.getTasksForDate(requireContext(), calendar))
        adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

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
            holder.binding.checkEvent.isClickable = false
            holder.binding.checkEvent.isFocusable = false

            // Single click hint
            holder.binding.root.setOnClickListener { view ->
                Toast.makeText(view.context, "Press and hold to complete task", Toast.LENGTH_SHORT).show()
            }

            // Press and hold (long-press) to complete
            holder.binding.root.setOnLongClickListener { view ->
                holder.binding.checkEvent.isChecked = true
                view.postDelayed({
                    onItemCompleted(event)
                }, 250)
                true
            }
        }

        override fun getItemCount(): Int = events.size
    }
}
