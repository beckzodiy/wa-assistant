package com.beeone.waassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DRAFTS, "Reply drafts", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pop-ups with the customer's message and a suggested reply"
                enableVibration(true)
            }
        )
    }

    companion object {
        const val CHANNEL_DRAFTS = "drafts"
        lateinit var instance: App
            private set

        /** Background scope for network work (API calls). */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
