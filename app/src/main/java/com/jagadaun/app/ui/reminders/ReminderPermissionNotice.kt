package com.jagadaun.app.ui.reminders

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.jagadaun.app.AppContainer
import com.jagadaun.app.reminders.ReminderScheduler
import com.jagadaun.app.ui.components.SecondaryButton

@Composable
internal fun ReminderPermissionNotice(container: AppContainer) {
    val context = LocalContext.current
    val manager = remember(context) { NotificationManagerCompat.from(context) }
    fun enabled() = manager.areNotificationsEnabled() &&
        manager.getNotificationChannel(ReminderScheduler.CHANNEL)?.importance != android.app.NotificationManager.IMPORTANCE_NONE
    var notificationsEnabled by remember { mutableStateOf(enabled()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = enabled()
        if (notificationsEnabled) container.refreshReminderNotifications()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled = enabled()
        if (notificationsEnabled) container.refreshReminderNotifications()
    }
    Column {
        if (!notificationsEnabled) {
            Text("Aktifkan notifikasi untuk menerima pengingat. Jadwal tetap terlihat meskipun izin ditolak.")
            SecondaryButton("Aktifkan notifikasi", onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !requested && !manager.areNotificationsEnabled()) {
                    requested = true
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    })
                }
            })
        } else {
            Text("Notifikasi aktif. Waktu dapat bergeser karena penghemat baterai Android; periksa kondisi tanaman sebelum mengikuti jadwal.")
        }
    }
}
