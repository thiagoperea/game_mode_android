package com.thiagoperea.gamemode

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.thiagoperea.gamemode.internal.setNotificationListHidden

class DndTileService : TileService() {

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (!notificationManager.isNotificationPolicyAccessGranted) {
            openPolicyAccessSettings()
            return
        }

        if (isDndActive()) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        } else {
            notificationManager.setNotificationListHidden(true)
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }
        updateTile()
    }

    private fun isDndActive(): Boolean =
        notificationManager.currentInterruptionFilter.let {
            it != NotificationManager.INTERRUPTION_FILTER_ALL &&
                it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = when {
            !notificationManager.isNotificationPolicyAccessGranted -> Tile.STATE_INACTIVE
            isDndActive() -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    private fun openPolicyAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
