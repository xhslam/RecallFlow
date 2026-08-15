package com.recallflow.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.recallflow.app.MainActivity
import com.recallflow.app.RecallFlowApplication
import com.recallflow.app.data.ItemStatus
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.abs

class ReviewAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderContract.ACTION_REVIEW_ALARM) return
        val batchId = intent.getStringExtra(ReminderContract.EXTRA_BATCH_ID) ?: return
        val pendingResult = goAsync()
        val app = context.applicationContext as RecallFlowApplication
        app.applicationScope.launch {
            try {
                val dao = app.database.recallDao()
                val batch = dao.notificationBatchById(batchId) ?: return@launch
                val now = Instant.now()
                val currentItems = buildList {
                    batch.itemIdsCsv.split(',').filter { it.isNotBlank() }.forEach { itemId ->
                        val item = dao.itemById(itemId)
                        if (item != null && item.status == ItemStatus.ACTIVE && item.nextReviewAt != null && !item.nextReviewAt!!.isAfter(now)) {
                            add(item)
                        }
                    }
                }
                if (currentItems.isNotEmpty() && canPost(context)) {
                    ensureChannel(context)
                    val isSingle = currentItems.size == 1
                    val destination = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_DESTINATION, if (isSingle) MainActivity.DESTINATION_REVIEW else MainActivity.DESTINATION_TODAY)
                        if (isSingle) putExtra(MainActivity.EXTRA_ITEM_ID, currentItems.first().id)
                    }
                    val contentIntent = PendingIntent.getActivity(
                        context,
                        abs(batchId.hashCode()),
                        destination,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val title = if (isSingle) "该复习「${currentItems.first().title}」了" else "你有 ${currentItems.size} 项内容需要复习"
                    val text = if (isSingle) "打开 RecallFlow 完成第 ${currentItems.first().currentRound} 轮复习" else "打开 RecallFlow 查看今日复习列表"
                    val notification = NotificationCompat.Builder(context, ReminderContract.CHANNEL_ID)
                        .setSmallIcon(com.recallflow.app.R.drawable.ic_recallflow)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(contentIntent)
                        .build()
                    NotificationManagerCompat.from(context).notify(abs(batchId.hashCode()), notification)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ReminderContract.CHANNEL_ID,
            ReminderContract.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "在学习内容到期时提醒你复习"
        }
        manager.createNotificationChannel(channel)
    }
}
