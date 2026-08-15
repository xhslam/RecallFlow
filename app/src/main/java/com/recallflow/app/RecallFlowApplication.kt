package com.recallflow.app

import android.app.Application
import com.recallflow.app.data.RecallDatabase
import com.recallflow.app.data.RecallRepository
import com.recallflow.app.data.SettingsStore
import com.recallflow.app.notifications.ReminderScheduler
import com.recallflow.app.platform.AttachmentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RecallFlowApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var database: RecallDatabase
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var repository: RecallRepository
        private set
    lateinit var reminderScheduler: ReminderScheduler
        private set
    lateinit var attachmentStore: AttachmentStore
        private set

    override fun onCreate() {
        super.onCreate()
        database = RecallDatabase.get(this)
        settingsStore = SettingsStore(this)
        repository = RecallRepository(database)
        reminderScheduler = ReminderScheduler(this, database, settingsStore)
        attachmentStore = AttachmentStore(this)
        applicationScope.launch { reminderScheduler.rescheduleAll() }
    }
}
