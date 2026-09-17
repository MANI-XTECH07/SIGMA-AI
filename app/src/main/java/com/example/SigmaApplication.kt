package com.example

import android.app.Application
import com.example.background.SigmaNotification

class SigmaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SigmaNotification.createNotificationChannel(this)
    }
}
