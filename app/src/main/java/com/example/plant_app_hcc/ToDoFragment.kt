package com.example.plant_app_hcc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.plant_app_hcc.databinding.FragmentTodoBinding

class ToDoFragment : Fragment() {

    private var _binding: FragmentTodoBinding? = null
    private val binding get() = _binding!!

    private val overdueList = mutableListOf<CareEvent>()
    private val upcomingList = mutableListOf<CareEvent>()

    private lateinit var overdueAdapter: CalendarFragment.EventAdapter
    private lateinit var upcomingAdapter: CalendarFragment.EventAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTodoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Overdue Tasks RecyclerView
        binding.recyclerOverdueTasks.layoutManager = LinearLayoutManager(requireContext())
        overdueAdapter = CalendarFragment.EventAdapter(overdueList) { completedTask ->
            CareTaskManager.completeTask(completedTask.id)
            val index = overdueList.indexOf(completedTask)
            if (index != -1) {
                overdueList.removeAt(index)
                overdueAdapter.notifyItemRemoved(index)
                updateEmptyStates()
            }
        }
        binding.recyclerOverdueTasks.adapter = overdueAdapter

        // Setup Upcoming Tasks RecyclerView
        binding.recyclerUpcomingTasks.layoutManager = LinearLayoutManager(requireContext())
        upcomingAdapter = CalendarFragment.EventAdapter(upcomingList) { completedTask ->
            CareTaskManager.completeTask(completedTask.id)
            val index = upcomingList.indexOf(completedTask)
            if (index != -1) {
                upcomingList.removeAt(index)
                upcomingAdapter.notifyItemRemoved(index)
                updateEmptyStates()
            }
        }
        binding.recyclerUpcomingTasks.adapter = upcomingAdapter

        refreshTasks()
    }

    override fun onResume() {
        super.onResume()
        refreshTasks()
    }

    private fun refreshTasks() {
        overdueList.clear()
        overdueList.addAll(CareTaskManager.getOverdueTasks())
        overdueAdapter.notifyDataSetChanged()

        upcomingList.clear()
        upcomingList.addAll(CareTaskManager.getUpcomingTasks())
        upcomingAdapter.notifyDataSetChanged()

        updateEmptyStates()
    }

    private fun updateEmptyStates() {
        binding.textEmptyOverdue.visibility = if (overdueList.isEmpty()) View.VISIBLE else View.GONE
        binding.textEmptyUpcoming.visibility = if (upcomingList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
