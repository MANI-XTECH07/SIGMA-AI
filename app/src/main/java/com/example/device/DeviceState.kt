package com.example.device

enum class DeviceStatus {
    DEVICE_LOCKED,
    DEVICE_AWAKE,
    AUTHENTICATION_REQUIRED,
    DEVICE_UNLOCKED
}

data class DeviceState(
    val status: DeviceStatus = DeviceStatus.DEVICE_UNLOCKED,
    val isScreenOn: Boolean = true,
    val isKeyguardLocked: Boolean = false,
    val isDeviceSecure: Boolean = false
)
