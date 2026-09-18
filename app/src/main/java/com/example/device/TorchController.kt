package com.example.device

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log

class TorchController(private val context: Context) {

    companion object {
        private const val TAG = "TorchController"
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var isTorchOn: Boolean = false

    fun turnOnTorch(): Boolean {
        if (cameraManager == null) return false
        return try {
            val cameraId = getTorchCameraId() ?: return false
            cameraManager.setTorchMode(cameraId, true)
            isTorchOn = true
            Log.d(TAG, "Flashlight turned ON")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to turn ON torch: ${e.message}")
            false
        }
    }

    fun turnOffTorch(): Boolean {
        if (cameraManager == null) return false
        return try {
            val cameraId = getTorchCameraId() ?: return false
            cameraManager.setTorchMode(cameraId, false)
            isTorchOn = false
            Log.d(TAG, "Flashlight turned OFF")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to turn OFF torch: ${e.message}")
            false
        }
    }

    fun toggleTorch(): Boolean {
        return if (isTorchOn) turnOffTorch() else turnOnTorch()
    }

    private fun getTorchCameraId(): String? {
        try {
            for (id in cameraManager?.cameraIdList ?: emptyArray()) {
                val characteristics = cameraManager?.getCameraCharacteristics(id)
                val hasFlash = characteristics?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics?.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering torch camera ID: ${e.message}")
        }
        return cameraManager?.cameraIdList?.firstOrNull()
    }
}
