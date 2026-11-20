package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.StatusSuccess
import com.example.ui.viewmodel.StudyToolsViewModel
import java.util.concurrent.TimeUnit

@Composable
fun PlannerScreen(viewModel: StudyToolsViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Tasks & Timetable | 1: Goals & Exams | 2: Real Reports
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showAddGoalDialog by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Planner") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Goals & Exams") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Reports") })
            }

            when (selectedTab) {
                0 -> {
                    val pending = tasks.count { !it.isCompleted }
                    val completed = tasks.count { it.isCompleted }
                    LazyColumn(
                        contentPadding = PaddingValues(14.dp, 12.dp, 14.dp, 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Pending: $pending • Completed: $completed  |  Reminders use WorkManager (subject to Android Doze/battery optimization).",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                        if (tasks.isEmpty()) {
                            item {
                                Text(
                                    "No study tasks yet. Tap + to create Daily tasks, Weekly timetable slots, or Revision reminders.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(tasks, key = { it.id }) { task ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { viewModel.toggleTaskCompletion(task) }) {
                                        Icon(
                                            imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                            contentDescription = "Toggle task done",
                                            tint = if (task.isCompleted) StatusSuccess else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(task.title, style = MaterialTheme.typography.titleSmall)
                                        val dayLabel = if (task.dayOfWeek in 1..7) {
                                            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[task.dayOfWeek - 1]
                                        } else "Daily"
                                        Text(
                                            "${task.taskType} • $dayLabel • ${task.subject} • ${task.durationMinutes}m" +
                                                if (task.scheduledTimeLabel.isNotBlank()) " @ ${task.scheduledTimeLabel}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (task.reminderEnabled && !task.isCompleted) {
                                        Icon(
                                            Icons.Filled.Alarm,
                                            contentDescription = "Reminder scheduled",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteTask(task) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete task")
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        contentPadding = PaddingValues(14.dp, 12.dp, 14.dp, 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (goals.isEmpty()) {
                            item {
                                Text(
                                    "No Monthly Goals or Exam Countdowns yet. Tap + to track your upcoming exams.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(goals, key = { it.id }) { goal ->
                            val daysLeft = TimeUnit.MILLISECONDS.toDays(
                                (goal.targetDateMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                            )
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (goal.goalType == "EXAM_COUNTDOWN") Icons.Filled.CalendarMonth else Icons.Filled.Flag,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(Modifier.size(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(goal.title, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${goal.subject} • $daysLeft days remaining • ${goal.progressPercent}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteGoal(goal.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete goal")
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = { goal.progressPercent / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Slider(
                                        value = goal.progressPercent.toFloat(),
                                        onValueChange = { viewModel.updateGoalProgress(goal, it.toInt()) },
                                        valueRange = 0f..100f
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        contentPadding = PaddingValues(14.dp, 12.dp, 14.dp, 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        "All Daily, Weekly & Monthly reports are computed strictly from Owner's real saved study activity.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        items(reports, key = { it.periodLabel }) { report ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(report.periodLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Total Study Time: ${report.totalMinutes} min • Tasks: ${report.completedTasks} • PYQs Solved: ${report.solvedPyqs} • Pages/Docs: ${report.docsRead} • Quizzes: ${report.quizzesDone}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (report.subjectBreakdown.isEmpty()) {
                                        Text("No logged activity in this period yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        val maxMins = report.subjectBreakdown.maxOf { it.second }.coerceAtLeast(1)
                                        report.subjectBreakdown.forEach { (subj, mins) ->
                                            Column {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(subj, style = MaterialTheme.typography.labelMedium)
                                                    Text("$mins min", style = MaterialTheme.typography.labelSmall)
                                                }
                                                LinearProgressIndicator(
                                                    progress = { mins.toFloat() / maxMins },
                                                    modifier = Modifier.fillMaxWidth().height(6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedTab != 2) {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) showAddTaskDialog = true else showAddGoalDialog = true
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("planner_add_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add study item")
            }
        }
    }

    if (showAddTaskDialog) {
        var title by remember { mutableStateOf("") }
        var subject by remember { mutableStateOf("Physics") }
        var taskType by remember { mutableStateOf("DAILY") }
        var dayOfWeek by remember { mutableIntStateOf(0) }
        var timeLabel by remember { mutableStateOf("07:00 PM") }
        var duration by remember { mutableStateOf("45") }
        var withReminder by remember { mutableStateOf(false) }
        var reminderMins by remember { mutableStateOf("60") }

        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            title = { Text("Add Study Task / Timetable") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("DAILY", "TIMETABLE", "REVISION").forEach { tp ->
                            FilterChip(selected = taskType == tp, onClick = { taskType = tp }, label = { Text(tp) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = timeLabel, onValueChange = { timeLabel = it }, label = { Text("Time Slot") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Minutes") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = withReminder,
                            onCheckedChange = { checked ->
                                withReminder = checked
                                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                        Text("Schedule notification reminder", style = MaterialTheme.typography.bodySmall)
                    }
                    if (withReminder) {
                        OutlinedTextField(
                            value = reminderMins,
                            onValueChange = { reminderMins = it },
                            label = { Text("Remind in (minutes)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addStudyTask(
                        title = title,
                        subject = subject,
                        taskType = taskType,
                        dayOfWeek = dayOfWeek,
                        timeLabel = timeLabel,
                        durationMinutes = duration.toIntOrNull() ?: 30,
                        reminderDelayMinutes = if (withReminder) (reminderMins.toLongOrNull() ?: 30L) else null
                    )
                    showAddTaskDialog = false
                }) { Text("Save Task") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddGoalDialog) {
        var title by remember { mutableStateOf("") }
        var goalType by remember { mutableStateOf("EXAM_COUNTDOWN") }
        var subject by remember { mutableStateOf("All Subjects") }
        var days by remember { mutableStateOf("30") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddGoalDialog = false },
            title = { Text("Add Monthly Goal / Exam Countdown") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = goalType == "EXAM_COUNTDOWN", onClick = { goalType = "EXAM_COUNTDOWN" }, label = { Text("Exam Countdown") })
                        FilterChip(selected = goalType == "MONTHLY_GOAL", onClick = { goalType = "MONTHLY_GOAL" }, label = { Text("Monthly Goal") })
                    }
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Goal / Exam Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text("Target Days From Today") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Target Notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addGoal(title, goalType, subject, days.toIntOrNull() ?: 30, notes)
                    showAddGoalDialog = false
                }) { Text("Save Goal") }
            },
            dismissButton = {
                TextButton(onClick = { showAddGoalDialog = false }) { Text("Cancel") }
            }
        )
    }
}
