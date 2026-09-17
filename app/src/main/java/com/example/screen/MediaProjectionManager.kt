package com.example.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager as AndroidMediaProjectionManager
import android.util.Log

class MediaProjectionManager(private val context: Context) {

    companion object {
        private const val TAG = "MediaProjectionMgr"
        var currentProjection: MediaProjection? = null
            private set
    }

    private val androidManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? AndroidMediaProjectionManager

    fun createScreenCaptureIntent(): Intent? {
        return androidManager?.createScreenCaptureIntent()
    }

    fun handleActivityResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            currentProjection = androidManager?.getMediaProjection(resultCode, data)
            Log.d(TAG, "MediaProjection initialized successfully")
        } else {
            Log.w(TAG, "MediaProjection permission denied or cancelled: $resultCode")
            currentProjection = null
        }
    }

    val isProjectionActive: Boolean
        get() = currentProjection != null

    fun release() {
        try {
            currentProjection?.stop()
            currentProjection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media projection: ${e.message}")
        }
    }
}
