package com.example.screen

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.util.DisplayMetrics
import android.util.Log
import java.nio.ByteBuffer

class ScreenCaptureManager(private val projectionManager: MediaProjectionManager) {

    companion object {
        private const val TAG = "ScreenCaptureManager"
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    fun captureScreen(
        metrics: DisplayMetrics,
        onBitmapCaptured: (Bitmap?) -> Unit
    ) {
        val projection = MediaProjectionManager.currentProjection
        if (projection == null) {
            Log.w(TAG, "MediaProjection not active, cannot capture real bitmap")
            onBitmapCaptured(null)
            return
        }

        try {
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = projection.createVirtualDisplay(
                "SigmaScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                null
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
                    Log.e(TAG, "Error acquiring screenshot: ${e.message}")
                } finally {
                    image?.close()
                    cleanup()
                    onBitmapCaptured(bitmap)
                }
            }, null)

        } catch (e: Exception) {
            Log.e(TAG, "Exception setting up screen capture: ${e.message}")
            cleanup()
            onBitmapCaptured(null)
        }
    }

    private fun cleanup() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
    }
}
