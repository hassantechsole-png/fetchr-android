package com.fetchr.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.fetchr.app.server.LocalServer
import kotlinx.coroutines.*

class FetchrApplication : Application() {

    companion object {
        const val CHANNEL_ID = "fetchr_downloads"
        lateinit var instance: FetchrApplication
            private set
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var localServer: LocalServer

    override fun onCreate() {
        super.onCreate()
        instance = this

        NewPipe.init(NewPipeDownloader.getInstance())

        createNotificationChannel()

        localServer = LocalServer(this)
        appScope.launch {
            localServer.start()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fetchr Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Video and audio download progress"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        localServer.stop()
    }
}
