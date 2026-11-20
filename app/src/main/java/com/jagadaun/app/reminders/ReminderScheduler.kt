package com.jagadaun.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jagadaun.app.MainActivity
import com.jagadaun.app.data.local.JagaDaunDatabase
import com.jagadaun.app.domain.Reminder
import java.util.concurrent.TimeUnit

/** Room is authoritative; old jobs are cancelled and workers recheck the saved due time. */
class ReminderScheduler(context: Context) {
    private val context = context.applicationContext
    private val work = WorkManager.getInstance(this.context)
    private val notifications = NotificationManagerCompat.from(this.context)
    private val preferences = this.context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private var scheduled = emptyMap<String, Reminder>()

    init {
        this.context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Perawatan tanaman", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
    @Synchronized

    fun sync(reminders: List<Reminder>, force: Boolean = false) {
        val active = reminders.filter { it.active }.associateBy { it.id }
        val oldIds = preferences.getStringSet("scheduled", emptySet()).orEmpty().toSet()
        val edit = preferences.edit()
        for (id in oldIds - active.keys) {
            work.cancelUniqueWork(workName(id))
            notifications.cancel(id, 0)
            edit.remove("sent_$id")
        }
        for ((id, reminder) in active) {
            if (!force && scheduled[id] == reminder) continue
            if (scheduled[id]?.nextDue?.let { it != reminder.nextDue } == true) {
                notifications.cancel(id, 0)
            }
            val request = OneTimeWorkRequestBuilder<CareReminderWorker>()
                .setInputData(workDataOf("reminderId" to id, "due" to reminder.nextDue))
                .setInitialDelay((reminder.nextDue - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .build()
            work.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
        }
        edit.putStringSet("scheduled", active.keys).apply()
        scheduled = active
    }

    companion object {
        internal const val CHANNEL = "plant-care"
        internal const val PREFERENCES = "reminder-delivery"
        private fun workName(id: String) = "plant-care:$id"
    }
}

class CareReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("reminderId") ?: return Result.failure()
        val due = inputData.getLong("due", Long.MIN_VALUE)
        val database = JagaDaunDatabase.getInstance(applicationContext)
        val reminder = database.reminderDao().getById(id) ?: return Result.success()
        if (!reminder.active || reminder.nextDue != due) return Result.success()
        if (due > System.currentTimeMillis()) return Result.retry()
        val plant = database.plantDao().getById(reminder.plantId) ?: return Result.success()
        val notifications = NotificationManagerCompat.from(applicationContext)
        if (!notifications.areNotificationsEnabled()) return Result.success()
        val channel = notifications.getNotificationChannel(ReminderScheduler.CHANNEL)
        if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return Result.success()
        val preferences = applicationContext.getSharedPreferences(ReminderScheduler.PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getLong("sent_$id", Long.MIN_VALUE) == due) return Result.success()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("jagadaun://reminders")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(plant.displayName)
            .setContentText("${reminder.careType} • Jadwal ${reminder.preferredTime}. Periksa kondisi tanaman sebelum merawat.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "${reminder.careType} • Jadwal ${reminder.preferredTime}. Periksa kondisi tanaman sebelum merawat.",
            ))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        // Permission may be revoked between the enabled check and notify.
        try {
            notifications.notify(id, 0, notification)
        } catch (_: SecurityException) {
            return Result.success()
        }
        preferences.edit().putLong("sent_$id", due).commit()
        return Result.success()
    }
}
