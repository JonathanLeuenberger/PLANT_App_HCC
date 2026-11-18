package com.example.plant_app_hcc

import android.app.DatePickerDialog
import android.content.Context
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.util.Calendar

object TaskDialog {

    fun show(
        context: Context,
        preselectedPlantId: String? = null,
        onTaskSaved: () -> Unit = {}
    ) {
        val plants = UserGarden.plants(context)
        if (plants.isEmpty()) {
            Toast.makeText(context, "Please add a plant to My Garden first!", Toast.LENGTH_LONG).show()
            return
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
        }

        // 1. Select Plant (Spinner)
        val textPlantLabel = TextView(context).apply {
            text = "Select Plant:"
            textSize = 14f
            setTextColor(0xFF1F2F27.toInt())
        }
        container.addView(textPlantLabel)

        val spinnerPlant = Spinner(context)
        val plantNames = plants.map { it.name }
        val plantAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, plantNames)
        spinnerPlant.adapter = plantAdapter

        val preselectedPos = if (preselectedPlantId != null) {
            plants.indexOfFirst { it.id == preselectedPlantId }.coerceAtLeast(0)
        } else 0
        spinnerPlant.setSelection(preselectedPos)
        container.addView(spinnerPlant)

        // 2. Task Title Input
        val textTitleLabel = TextView(context).apply {
            text = "Task Title (e.g. Water plant, Mist leaves):"
            textSize = 14f
            setTextColor(0xFF1F2F27.toInt())
            val topMargin = (12 * context.resources.displayMetrics.density).toInt()
            setPadding(0, topMargin, 0, 0)
        }
        container.addView(textTitleLabel)

        val inputTitle = EditText(context).apply {
            hint = "Water plant"
            setText("Water plant")
        }
        container.addView(inputTitle)

        // 3. Frequency Radio Group
        val textFreqLabel = TextView(context).apply {
            text = "Schedule Type:"
            textSize = 14f
            setTextColor(0xFF1F2F27.toInt())
            val topMargin = (12 * context.resources.displayMetrics.density).toInt()
            setPadding(0, topMargin, 0, 0)
        }
        container.addView(textFreqLabel)

        val radioGroupFreq = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
        }
        val radioOneTime = RadioButton(context).apply {
            text = "One-time task"
            id = View.generateViewId()
            isChecked = true
        }
        val radioWeekly = RadioButton(context).apply {
            text = "Weekly"
            id = View.generateViewId()
        }
        val radioBiweekly = RadioButton(context).apply {
            text = "Every-other-week"
            id = View.generateViewId()
        }
        val radioMonthly = RadioButton(context).apply {
            text = "Monthly"
            id = View.generateViewId()
        }
        radioGroupFreq.addView(radioOneTime)
        radioGroupFreq.addView(radioWeekly)
        radioGroupFreq.addView(radioBiweekly)
        radioGroupFreq.addView(radioMonthly)
        container.addView(radioGroupFreq)

        // 4. One-Time Task Date Selector Layout
        val oneTimeContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val textOneTimeLabel = TextView(context).apply {
            text = "Select Due Date:"
            textSize = 13f
            setTextColor(0xFF687469.toInt())
        }
        oneTimeContainer.addView(textOneTimeLabel)

        val dueCalendar = Calendar.getInstance()
        val dateButton = Button(context).apply {
            text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(dueCalendar.time)
            setOnClickListener {
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        dueCalendar.set(year, month, day)
                        text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(dueCalendar.time)
                    },
                    dueCalendar.get(Calendar.YEAR),
                    dueCalendar.get(Calendar.MONTH),
                    dueCalendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }
        oneTimeContainer.addView(dateButton)
        container.addView(oneTimeContainer)

        // 5. Days of Week Layout (for Weekly & Biweekly)
        val daysContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val textDaysLabel = TextView(context).apply {
            text = "Select Days of Week:"
            textSize = 13f
            setTextColor(0xFF687469.toInt())
        }
        daysContainer.addView(textDaysLabel)

        val daysRow1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val daysRow2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val checkSun = CheckBox(context).apply { text = "Sun"; tag = Calendar.SUNDAY }
        val checkMon = CheckBox(context).apply { text = "Mon"; tag = Calendar.MONDAY }
        val checkTue = CheckBox(context).apply { text = "Tue"; tag = Calendar.TUESDAY; isChecked = true }
        val checkWed = CheckBox(context).apply { text = "Wed"; tag = Calendar.WEDNESDAY }
        val checkThu = CheckBox(context).apply { text = "Thu"; tag = Calendar.THURSDAY; isChecked = true }
        val checkFri = CheckBox(context).apply { text = "Fri"; tag = Calendar.FRIDAY }
        val checkSat = CheckBox(context).apply { text = "Sat"; tag = Calendar.SATURDAY }

        val dayCheckBoxes = listOf(checkSun, checkMon, checkTue, checkWed, checkThu, checkFri, checkSat)

        daysRow1.addView(checkSun); daysRow1.addView(checkMon); daysRow1.addView(checkTue); daysRow1.addView(checkWed)
        daysRow2.addView(checkThu); daysRow2.addView(checkFri); daysRow2.addView(checkSat)

        daysContainer.addView(daysRow1)
        daysContainer.addView(daysRow2)
        container.addView(daysContainer)

        // 6. Day of Month Layout (for Monthly)
        val monthlyContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val textMonthLabel = TextView(context).apply {
            text = "Day of Month (1 - 31):"
            textSize = 13f
            setTextColor(0xFF687469.toInt())
        }
        monthlyContainer.addView(textMonthLabel)

        val inputDayOfMonth = EditText(context).apply {
            hint = "1"
            setText("1")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        monthlyContainer.addView(inputDayOfMonth)
        container.addView(monthlyContainer)

        // Radio group listener to toggle options visibility
        radioGroupFreq.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                radioOneTime.id -> {
                    oneTimeContainer.visibility = View.VISIBLE
                    daysContainer.visibility = View.GONE
                    monthlyContainer.visibility = View.GONE
                }
                radioMonthly.id -> {
                    oneTimeContainer.visibility = View.GONE
                    daysContainer.visibility = View.GONE
                    monthlyContainer.visibility = View.VISIBLE
                }
                else -> {
                    oneTimeContainer.visibility = View.GONE
                    daysContainer.visibility = View.VISIBLE
                    monthlyContainer.visibility = View.GONE
                }
            }
        }

        // Show Dialog
        var dialog: AlertDialog? = null
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Add new plant task")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save Task", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = inputTitle.text.toString().trim()
                if (title.isEmpty()) {
                    inputTitle.error = "Please enter a task title"
                    return@setOnClickListener
                }

                val selectedPlant = plants[spinnerPlant.selectedItemPosition]
                val selectedFreqId = radioGroupFreq.checkedRadioButtonId

                if (selectedFreqId == radioOneTime.id) {
                    CareTaskManager.addTask(
                        context = context,
                        plantId = selectedPlant.id,
                        title = title,
                        due = dueCalendar.timeInMillis
                    )
                    Toast.makeText(context, "Task saved for ${selectedPlant.name}!", Toast.LENGTH_SHORT).show()
                } else {
                    val frequency: RecurrenceFrequency
                    val daysOfWeekList = mutableListOf<Int>()
                    var dayOfMonthVal = 1

                    when (selectedFreqId) {
                        radioMonthly.id -> {
                            frequency = RecurrenceFrequency.MONTHLY
                            dayOfMonthVal = inputDayOfMonth.text.toString().toIntOrNull() ?: 1
                            dayOfMonthVal = dayOfMonthVal.coerceIn(1, 31)
                        }
                        radioBiweekly.id -> {
                            frequency = RecurrenceFrequency.BIWEEKLY
                            dayCheckBoxes.filter { it.isChecked }.forEach {
                                daysOfWeekList.add(it.tag as Int)
                            }
                        }
                        else -> {
                            frequency = RecurrenceFrequency.WEEKLY
                            dayCheckBoxes.filter { it.isChecked }.forEach {
                                daysOfWeekList.add(it.tag as Int)
                            }
                        }
                    }

                    if (frequency != RecurrenceFrequency.MONTHLY && daysOfWeekList.isEmpty()) {
                        Toast.makeText(context, "Please select at least one day of the week", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    CareTaskManager.addRecurringTask(
                        context = context,
                        plantId = selectedPlant.id,
                        title = title,
                        frequency = frequency,
                        daysOfWeek = daysOfWeekList,
                        dayOfMonth = dayOfMonthVal
                    )

                    Toast.makeText(context, "Recurring task saved for ${selectedPlant.name}!", Toast.LENGTH_SHORT).show()
                }

                dialog.dismiss()
                onTaskSaved()
            }
        }

        dialog.show()
    }
}
