package com.example.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager as AndroidMediaProjectionManager
import android.util.Log
import com.example.service.SigmaScreenCaptureService

class MediaProjectionManager(private val context: Context) {

    companion object {
        private const val TAG = "MediaProjectionMgr"
    }

    private val androidManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? AndroidMediaProjectionManager

    fun createScreenCaptureIntent(): Intent? {
        return try {
            androidManager?.createScreenCaptureIntent()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create screen capture intent: ${e.message}")
            null
        }
    }

    /**
     * Handles activity result from MediaProjection prompt.
     * Starts SigmaScreenCaptureService foreground service on OK, handles cancel without crashing.
     */
    fun handleActivityResult(resultCode: Int, data: Intent?): Boolean {
        if (resultCode == Activity.RESULT_OK && data != null) {
            Log.d(TAG, "MediaProjection permission granted. Starting SigmaScreenCaptureService...")
            SigmaScreenCaptureService.start(context, resultCode, data)
            return true
        } else {
            Log.d(TAG, "Screen sharing permission cancelled or unavailable (resultCode: $resultCode)")
            SigmaScreenCaptureService.stop(context)
            return false
        }
    }

    val isProjectionActive: Boolean
        get() = SigmaScreenCaptureService.isServiceRunning

    fun release() {
        try {
            SigmaScreenCaptureService.stop(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media projection: ${e.message}")
        }
    }
}
