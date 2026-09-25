package com.thiagoperea.gamemode.internal

import android.content.Context
import android.provider.Settings
import android.view.Surface

fun Context.getRotationState(): RotationState {
    val resolver = this.contentResolver
    val configAccel = Settings.System.ACCELEROMETER_ROTATION
    val configRotation = Settings.System.USER_ROTATION

    return RotationState(
        autoRotation = Settings.System.getInt(resolver, configAccel, 1),
        currentRotation = Settings.System.getInt(resolver, configRotation, Surface.ROTATION_0)
    )
}

fun Context.getBrightnessState(): BrightnessState {
    val resolver = this.contentResolver

    return BrightnessState(
        mode = Settings.System.getInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        ),
        brightnessValue = Settings.System.getInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS,
            128
        )
    )
}
