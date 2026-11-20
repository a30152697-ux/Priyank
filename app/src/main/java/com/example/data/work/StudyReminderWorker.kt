package com.example.data.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.MainActivity
import com.example.R
import java.util.concurrent.TimeUnit

class StudyReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Study Reminder"
        val subject = inputData.getString(KEY_SUBJECT) ?: "General"
        val taskId = inputData.getLong(KEY_TASK_ID, 0L)

        ensureNotificationChannel(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            taskId.toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("SUKOON: Reminder for Owner")
            .setContentText("[$subject] $title")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Owner, it's time for your scheduled study session ($subject): $title")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(
            (taskId.toInt() and 0x7FFFFFFF).coerceAtLeast(1001),
            notification
        )
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "sukoon_study_reminders"
        const val KEY_TITLE = "task_title"
        const val KEY_SUBJECT = "task_subject"
        const val KEY_TASK_ID = "task_id"

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notification_channel_study_name),
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = context.getString(R.string.notification_channel_study_desc)
                    }
                    nm.createNotificationChannel(channel)
                }
            }
        }
    }
}

class StudyReminderScheduler(private val context: Context) {
    fun scheduleReminder(taskId: Long, title: String, subject: String, delayMinutes: Long) {
        val work = OneTimeWorkRequestBuilder<StudyReminderWorker>()
            .setInitialDelay(delayMinutes.coerceAtLeast(1L), TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    StudyReminderWorker.KEY_TASK_ID to taskId,
                    StudyReminderWorker.KEY_TITLE to title,
                    StudyReminderWorker.KEY_SUBJECT to subject
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "sukoon_task_reminder_$taskId",
            ExistingWorkPolicy.REPLACE,
            work
        )
    }

    fun cancelReminder(taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("sukoon_task_reminder_$taskId")
    }
}
