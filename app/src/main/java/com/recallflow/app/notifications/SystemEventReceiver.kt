package com.recallflow.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.recallflow.app.RecallFlowApplication
import kotlinx.coroutines.launch

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as RecallFlowApplication
        app.applicationScope.launch {
            try {
                app.reminderScheduler.rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
