package com.example.screen

import android.graphics.Bitmap
import android.util.DisplayMetrics
import android.util.Log
import com.example.service.SigmaScreenCaptureService

class ScreenCaptureManager(private val projectionManager: MediaProjectionManager) {

    companion object {
        private const val TAG = "ScreenCaptureManager"
    }

    fun captureScreen(
        metrics: DisplayMetrics,
        onBitmapCaptured: (Bitmap?) -> Unit
    ) {
        val service = SigmaScreenCaptureService.instance
        if (service == null || !SigmaScreenCaptureService.isServiceRunning) {
            Log.w(TAG, "Screen capture service not running, cannot capture real bitmap")
            onBitmapCaptured(null)
            return
        }

        try {
            service.captureFrame(metrics, onBitmapCaptured)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during screen capture: ${e.message}", e)
            onBitmapCaptured(null)
        }
    }
}
