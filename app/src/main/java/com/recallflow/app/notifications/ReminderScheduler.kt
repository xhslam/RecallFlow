package com.recallflow.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.getSystemService
import com.recallflow.app.data.AppSettings
import com.recallflow.app.data.NotificationBatchEntity
import com.recallflow.app.data.RecallDatabase
import com.recallflow.app.data.SettingsStore
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

object ReminderContract {
    const val ACTION_REVIEW_ALARM = "com.recallflow.app.REVIEW_ALARM"
    const val EXTRA_BATCH_ID = "batch_id"
    const val CHANNEL_ID = "review_due"
    const val CHANNEL_NAME = "复习到期提醒"
    const val MERGE_WINDOW_MINUTES = 15L
}

class ReminderScheduler(
    private val context: Context,
    private val database: RecallDatabase,
    private val settingsStore: SettingsStore
) {
    private val dao get() = database.recallDao()
    private val alarmManager: AlarmManager get() = requireNotNull(context.getSystemService())

    /**
     * 取消现有本地提醒并从数据库当前待办重建。该入口可在计划、设置、启动和时区变化后重复调用。
     */
    suspend fun rescheduleAll(now: Instant = Instant.now()) {
        cancelAll()
        val settings = settingsStore.settings.first()
        if (!settings.notificationsEnabled) return

        val candidates = dao.allScheduledActiveItems()
            .mapNotNull { item -> item.nextReviewAt?.let { item to it } }
            .filter { (_, dueAt) -> dueAt.isAfter(now) }
            .map { (item, dueAt) -> item.id to moveOutOfQuietHours(dueAt, settings) }
            .sortedBy { it.second }

        val batches = mutableListOf<MutableList<Pair<String, Instant>>>()
        candidates.forEach { candidate ->
            val last = batches.lastOrNull()
            if (last == null || Duration.between(last.first().second, candidate.second).toMinutes() > ReminderContract.MERGE_WINDOW_MINUTES) {
                batches += mutableListOf(candidate)
            } else {
                last += candidate
            }
        }

        batches.forEach { group ->
            val batch = NotificationBatchEntity(
                id = UUID.randomUUID().toString(),
                triggerAt = group.first().second,
                itemIdsCsv = group.joinToString(",") { it.first },
                requestCode = stableRequestCode(group.joinToString("|") { it.first } + group.first().second.toEpochMilli())
            )
            dao.upsertNotificationBatch(batch)
            schedule(batch)
            dao.upsertNotificationBatch(batch.copy(scheduled = true))
        }
    }

    suspend fun cancelAll() {
        dao.activeNotificationBatches().forEach { batch ->
            alarmManager.cancel(pendingIntent(batch))
        }
        dao.clearNotificationBatches()
    }

    private fun schedule(batch: NotificationBatchEntity) {
        val intent = pendingIntent(batch)
        val triggerAt = batch.triggerAt.toEpochMilli()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
    }

    private fun pendingIntent(batch: NotificationBatchEntity): PendingIntent {
        val intent = Intent(context, ReviewAlarmReceiver::class.java).apply {
            action = ReminderContract.ACTION_REVIEW_ALARM
            data = Uri.parse("recallflow://reminder/${batch.id}")
            putExtra(ReminderContract.EXTRA_BATCH_ID, batch.id)
        }
        return PendingIntent.getBroadcast(
            context,
            batch.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun moveOutOfQuietHours(instant: Instant, settings: AppSettings): Instant {
        val start = settings.quietStart?.let(::parseTime) ?: return instant
        val end = settings.quietEnd?.let(::parseTime) ?: return instant
        if (start == end) return instant
        val zone = ZoneId.systemDefault()
        val local = instant.atZone(zone)
        val time = local.toLocalTime()
        val crossesMidnight = start > end
        val isQuiet = if (crossesMidnight) time >= start || time < end else time >= start && time < end
        if (!isQuiet) return instant
        val endDate = when {
            !crossesMidnight -> local.toLocalDate()
            time >= start -> local.toLocalDate().plusDays(1)
            else -> local.toLocalDate()
        }
        return endDate.atTime(end).atZone(zone).toInstant()
    }

    private fun parseTime(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()
    private fun stableRequestCode(seed: String): Int = abs(seed.hashCode()).coerceAtLeast(1)
}
