package com.recallflow.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Database(
    entities = [
        StudyItemEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        StudyItemTagCrossRef::class,
        AttachmentEntity::class,
        ReviewScheduleEntity::class,
        ReviewEventEntity::class,
        NotificationBatchEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RecallDatabase : RoomDatabase() {
    abstract fun recallDao(): RecallDao

    companion object {
        @Volatile private var instance: RecallDatabase? = null
        fun get(context: Context): RecallDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RecallDatabase::class.java,
                "recallflow.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}

private val Context.dataStore by preferencesDataStore(name = "recallflow_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val QUIET_START = stringPreferencesKey("quiet_start")
        val QUIET_END = stringPreferencesKey("quiet_end")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            quietStart = prefs[Keys.QUIET_START],
            quietEnd = prefs[Keys.QUIET_END],
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false
        )
    }

    suspend fun updateNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun updateQuietHours(start: String?, end: String?) {
        context.dataStore.edit { prefs ->
            if (start == null) prefs.remove(Keys.QUIET_START) else prefs[Keys.QUIET_START] = start
            if (end == null) prefs.remove(Keys.QUIET_END) else prefs[Keys.QUIET_END] = end
        }
    }

    suspend fun markOnboardingDone() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }
}

data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val quietStart: String? = null,
    val quietEnd: String? = null,
    val onboardingDone: Boolean = false
)
