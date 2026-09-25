package com.thiagoperea.gamemode.internal

data class ConfigurationSnapshot(
    val brightnessMode: Int,
    val brightness: Int,

    val mediaVolume: Int,

    val autoRotation: Int,
    val currentRotation: Int,

    val dndInterruptionFilter: Int
)

data class RotationState(
    val autoRotation: Int,
    val currentRotation: Int,
)

data class BrightnessState(
    val mode: Int,
    val brightnessValue: Int
)
