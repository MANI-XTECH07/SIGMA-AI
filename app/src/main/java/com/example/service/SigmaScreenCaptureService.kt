package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager as AndroidMediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import java.nio.ByteBuffer

class SigmaScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "sigma_screen_capture_channel"
        private const val NOTIFICATION_ID = 2002

        const val ACTION_START = "com.example.service.action.START_CAPTURE"
        const val ACTION_STOP = "com.example.service.action.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        var instance: SigmaScreenCaptureService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null && instance?.mediaProjection != null

        fun start(context: Context, resultCode: Int, data: Intent) {
            try {
                val intent = Intent(context, SigmaScreenCaptureService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_RESULT_CODE, resultCode)
                    putExtra(EXTRA_RESULT_DATA, data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SigmaScreenCaptureService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, SigmaScreenCaptureService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop SigmaScreenCaptureService: ${e.message}", e)
            }
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                cleanupAndStop()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                startForegroundWithNotification()

                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    initMediaProjection(resultCode, resultData)
                } else {
                    Log.w(TAG, "MediaProjection result invalid or cancelled")
                    cleanupAndStop()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "SigmaScreenCaptureService started in foreground")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaProjection foreground service: ${e.message}", e)
            cleanupAndStop()
        }
    }

    private fun initMediaProjection(resultCode: Int, data: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? AndroidMediaProjectionManager
            mediaProjection = projectionManager?.getMediaProjection(resultCode, data)

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection session stopped by system")
                    cleanupAndStop()
                }
            }, mainHandler)

            Log.d(TAG, "MediaProjection initialized successfully in foreground service")
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException creating MediaProjection: ${se.message}", se)
            cleanupAndStop()
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing MediaProjection: ${e.message}", e)
            cleanupAndStop()
        }
    }

    fun captureFrame(metrics: DisplayMetrics, onCaptured: (Bitmap?) -> Unit) {
        val projection = mediaProjection
        if (projection == null) {
            Log.w(TAG, "MediaProjection not active, cannot capture bitmap")
            onCaptured(null)
            return
        }

        try {
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader?.close()
            virtualDisplay?.release()

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = projection.createVirtualDisplay(
                "SigmaScreenCaptureDisplay",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                mainHandler
            )

            imageReader!!.setOnImageAvailableListener({ reader ->
                var image: Image? = null
                var bitmap: Bitmap? = null
                try {
                    image = reader.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer: ByteBuffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error acquiring frame from ImageReader: ${e.message}")
                } finally {
                    image?.close()
                    onCaptured(bitmap)
                }
            }, mainHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up capture frame: ${e.message}", e)
            onCaptured(null)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SIGMA Screen Vision Active")
            .setContentText("Analyzing screen content in real time")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SIGMA Screen Vision",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies when SIGMA Screen Vision is analyzing display"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun cleanupAndStop() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up screen capture: ${e.message}")
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            instance = null
            Log.d(TAG, "SigmaScreenCaptureService stopped safely")
        }
    }

    override fun onDestroy() {
        cleanupAndStop()
        super.onDestroy()
    }
}
