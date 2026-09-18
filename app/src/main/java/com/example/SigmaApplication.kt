package com.example

import android.app.Application
import com.example.background.SigmaNotification

class SigmaApplication : Application() {
    companion object {
        var instance: SigmaApplication? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        SigmaNotification.createNotificationChannel(this)
    }
}
