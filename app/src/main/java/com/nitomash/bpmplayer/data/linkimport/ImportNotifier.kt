package com.nitomash.bpmplayer.data.linkimport

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.nitomash.bpmplayer.MainActivity
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.domain.linkimport.ImportJobId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun foregroundInfo(
        jobId: ImportJobId,
        title: String,
        text: String,
        progress: Int,
        max: Int,
        indeterminate: Boolean,
    ): ForegroundInfo {
        createChannel()
        val requestCode = jobId.value.hashCode()
        val cancelIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ImportActionReceiver::class.java).apply {
                action = ImportActions.CANCEL
                putExtra(ImportActions.EXTRA_JOB_ID, jobId.value)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            context,
            requestCode xor OPEN_REQUEST_CODE_SALT,
            Intent(context, MainActivity::class.java).apply {
                action = ImportActions.OPEN_IMPORT
                putExtra(ImportActions.EXTRA_JOB_ID, jobId.value)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(max, progress, indeterminate)
            .addAction(
                android.R.drawable.ic_delete,
                context.getString(R.string.import_cancel),
                cancelIntent,
            )
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.import_notification_title),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "bpm_player_import"
        const val NOTIFICATION_ID = 4201
        const val OPEN_REQUEST_CODE_SALT = 0x5A5A5A5A
    }
}
