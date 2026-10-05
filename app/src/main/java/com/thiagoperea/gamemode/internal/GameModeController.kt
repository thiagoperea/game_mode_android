package com.thiagoperea.gamemode.internal

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.view.Surface
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.gson.Gson

class GameModeController(
    val context: Activity
) {

    private val preferences: android.content.SharedPreferences
        get() = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)

    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    fun hasWriteSettingsPermission(): Boolean = Settings.System.canWrite(context)

    fun hasNotificationPolicyAccess(): Boolean =
        notificationManager.isNotificationPolicyAccessGranted

    fun requestWriteSettingsPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            "package:${context.packageName}".toUri()
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    fun requestNotificationPolicyAccess() {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    fun enableGameMode(): Boolean {
        val configuration = getConfiguration()
        if (configuration.requiresWriteSettingsPermission() && !hasWriteSettingsPermission()) {
            return false
        }
        if (configuration.enableDoNotDisturb && !hasNotificationPolicyAccess()) {
            return false
        }

        if (!isGameModeEnabled()) {
            saveConfigSnapshot()
            saveActiveConfiguration(configuration)
        }

        applyConfiguration(configuration)

        preferences.edit { putBoolean(PREF_GAME_MODE_ENABLED, true) }
        if (configuration.launchConfiguredApp) openConfiguredApp()
        return true
    }

    fun disableGameMode(): Boolean {
        val snapshot = loadConfigSnapshot()
        if (snapshot == null) {
            preferences.edit {
                remove(PREF_ACTIVE_CONFIG_JSON)
                putBoolean(PREF_GAME_MODE_ENABLED, false)
            }
            return true
        }
        val configuration = loadActiveConfiguration() ?: GameModeConfiguration()
        if (configuration.requiresWriteSettingsPermission() && !hasWriteSettingsPermission()) {
            return false
        }
        if (configuration.enableDoNotDisturb && !hasNotificationPolicyAccess()) {
            return false
        }

        restoreConfiguration(snapshot, configuration)
        preferences.edit {
            remove(PREF_CONFIG_JSON)
            remove(PREF_ACTIVE_CONFIG_JSON)
            putBoolean(PREF_GAME_MODE_ENABLED, false)
        }
        // O app do jogo continua aberto e mantém a tela em paisagem mesmo com a rotação restaurada.
        if (configuration.forceLandscape) goHome()
        return true
    }

    fun isGameModeEnabled(): Boolean =
        preferences.getBoolean(PREF_GAME_MODE_ENABLED, false)

    fun getConfiguredAppPackage(): String? =
        preferences.getString(PREF_TARGET_PACKAGE, null)

    fun setConfiguredAppPackage(packageName: String?) {
        preferences.edit {
            if (packageName == null) {
                remove(PREF_TARGET_PACKAGE)
            } else {
                putString(PREF_TARGET_PACKAGE, packageName)
            }
        }
    }

    fun getConfiguration(): GameModeConfiguration {
        val json = preferences.getString(PREF_GAME_MODE_CONFIG_JSON, null) ?: return GameModeConfiguration()
        return runCatching {
            Gson().fromJson(json, GameModeConfiguration::class.java)
        }.getOrNull() ?: GameModeConfiguration()
    }

    fun setConfiguration(configuration: GameModeConfiguration) {
        val normalized = configuration.copy(
            brightness = configuration.brightness.coerceIn(0, MAX_BRIGHTNESS),
            mediaVolumePercent = configuration.mediaVolumePercent.coerceIn(0, 100)
        )
        preferences.edit {
            putString(PREF_GAME_MODE_CONFIG_JSON, Gson().toJson(normalized))
        }
    }

    fun requiresWriteSettingsPermissionForEnable(): Boolean =
        getConfiguration().requiresWriteSettingsPermission()

    fun requiresNotificationPolicyAccessForEnable(): Boolean =
        getConfiguration().enableDoNotDisturb

    fun requiresWriteSettingsPermissionForDisable(): Boolean =
        loadConfigSnapshot()?.let {
            (loadActiveConfiguration() ?: GameModeConfiguration()).requiresWriteSettingsPermission()
        } ?: false

    fun requiresNotificationPolicyAccessForDisable(): Boolean =
        loadConfigSnapshot()?.let {
            (loadActiveConfiguration() ?: GameModeConfiguration()).enableDoNotDisturb
        } ?: false

    private fun openConfiguredApp(): Boolean {
        val packageName = getConfiguredAppPackage() ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false

        context.startActivity(intent)
        return true
    }

    private fun goHome() {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun saveConfigSnapshot() {
        val rotationState = context.getRotationState()
        val brightnessState = context.getBrightnessState()
        val audioManager = context.getSystemService(AudioManager::class.java)
        val currentDndFilter = notificationManager.currentInterruptionFilter
        val currentConfig = ConfigurationSnapshot(
            brightnessMode = brightnessState.mode,
            brightness = brightnessState.brightnessValue,
            mediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            autoRotation = rotationState.autoRotation,
            currentRotation = rotationState.currentRotation,
            dndInterruptionFilter = currentDndFilter.takeUnless {
                it == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            } ?: NotificationManager.INTERRUPTION_FILTER_ALL
        )

        preferences.edit {
            putString(PREF_CONFIG_JSON, Gson().toJson(currentConfig))
        }
    }

    private fun saveActiveConfiguration(configuration: GameModeConfiguration) {
        preferences.edit {
            putString(PREF_ACTIVE_CONFIG_JSON, Gson().toJson(configuration))
        }
    }

    private fun loadConfigSnapshot(): ConfigurationSnapshot? {
        val json = preferences.getString(PREF_CONFIG_JSON, null) ?: return null
        return runCatching {
            Gson().fromJson(json, ConfigurationSnapshot::class.java)
        }.getOrNull()
    }

    private fun applyConfiguration(configuration: GameModeConfiguration) {
        if (configuration.forceLandscape) forceLandscape()
        if (configuration.configureBrightness) setupBrightness(configuration.brightness)
        if (configuration.configureMediaVolume) setupMediaVolume(configuration.mediaVolumePercent)
        if (configuration.enableDoNotDisturb) setupDoNotDisturb(configuration.hideNotificationsInDnd)
    }

    private fun forceLandscape() {
        val resolver = context.contentResolver
        val configAccel = Settings.System.ACCELEROMETER_ROTATION
        val configRotation = Settings.System.USER_ROTATION

        // desliga a feature de rotação automatica
        Settings.System.putInt(resolver, configAccel, 0)

        // força a orientação horizontal (90 ou 270)
        Settings.System.putInt(resolver, configRotation, Surface.ROTATION_90)
    }

    private fun setupBrightness(brightness: Int) {
        val resolver = context.contentResolver
        val configBrightMode = Settings.System.SCREEN_BRIGHTNESS_MODE
        val configBright = Settings.System.SCREEN_BRIGHTNESS

        Settings.System.putInt(
            resolver,
            configBrightMode,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(resolver, configBright, brightness.coerceIn(0, MAX_BRIGHTNESS))
    }

    private fun setupMediaVolume(volumePercent: Int) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (maxVolume * volumePercent.coerceIn(0, 100) / 100f).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
    }

    private fun setupDoNotDisturb(hideNotifications: Boolean) {
        notificationManager.setNotificationListHidden(hideNotifications)
        notificationManager.setInterruptionFilter(
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        )
    }

    private fun restoreConfiguration(
        snapshot: ConfigurationSnapshot,
        configuration: GameModeConfiguration
    ) {
        val resolver = context.contentResolver
        if (configuration.forceLandscape) {
            Settings.System.putInt(
                resolver,
                Settings.System.ACCELEROMETER_ROTATION,
                snapshot.autoRotation
            )
            Settings.System.putInt(
                resolver,
                Settings.System.USER_ROTATION,
                snapshot.currentRotation
            )
        }
        if (configuration.configureBrightness) {
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                snapshot.brightnessMode
            )
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS,
                snapshot.brightness
            )
        }
        if (configuration.configureMediaVolume) {
            context.getSystemService(AudioManager::class.java).setStreamVolume(
                AudioManager.STREAM_MUSIC,
                snapshot.mediaVolume,
                0
            )
        }
        if (configuration.enableDoNotDisturb) {
            notificationManager.setInterruptionFilter(snapshot.dndInterruptionFilter)
        }
    }

    private fun loadActiveConfiguration(): GameModeConfiguration? {
        val json = preferences.getString(PREF_ACTIVE_CONFIG_JSON, null) ?: return null
        return runCatching {
            Gson().fromJson(json, GameModeConfiguration::class.java)
        }.getOrNull()
    }

    private fun GameModeConfiguration.requiresWriteSettingsPermission(): Boolean =
        forceLandscape || configureBrightness

    companion object {
        private const val SHARED_PREFS_NAME = "gamemode.db"
        private const val PREF_CONFIG_JSON = "pref.config"
        private const val PREF_ACTIVE_CONFIG_JSON = "pref.active_config"
        private const val PREF_GAME_MODE_CONFIG_JSON = "pref.game_mode_config"
        private const val PREF_GAME_MODE_ENABLED = "pref.game_mode_enabled"
        private const val PREF_TARGET_PACKAGE = "pref.target_package"
        private const val MAX_BRIGHTNESS = 255
    }
}
