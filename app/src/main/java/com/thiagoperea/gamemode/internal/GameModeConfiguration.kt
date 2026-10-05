package com.thiagoperea.gamemode.internal

data class GameModeConfiguration(
    val forceLandscape: Boolean = true,
    val configureBrightness: Boolean = true,
    val brightness: Int = DEFAULT_BRIGHTNESS,
    val configureMediaVolume: Boolean = true,
    val mediaVolumePercent: Int = DEFAULT_MEDIA_VOLUME_PERCENT,
    val enableDoNotDisturb: Boolean = true,
    val hideNotificationsInDnd: Boolean = true,
    val launchConfiguredApp: Boolean = true
) {
    companion object {
        const val DEFAULT_BRIGHTNESS = 179
        const val DEFAULT_MEDIA_VOLUME_PERCENT = 50
    }
}
