package com.thiagoperea.gamemode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.thiagoperea.gamemode.internal.GameModeController
import com.thiagoperea.gamemode.ui.theme.GameModeTheme

class MainActivity : ComponentActivity() {

    val controller = GameModeController(this)
    private var pendingAction: String? = null
    private var permissionRequestInFlight: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAction = actionFromIntent(intent)
        enableEdgeToEdge()
        setContent {
            GameModeTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainScreen(
                        controller = controller,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAction = actionFromIntent(intent)
        permissionRequestInFlight = null
    }

    override fun onPostResume() {
        super.onPostResume()

        val action = pendingAction ?: return
        val permissionRequest = permissionRequestInFlight
        if (permissionRequest != null) {
            permissionRequestInFlight = null
            if (!hasPermission(permissionRequest)) {
                pendingAction = null
                return
            }
        }

        when (action) {
            ACTION_ENABLE -> {
                if (controller.getConfiguration().launchConfiguredApp &&
                    controller.getConfiguredAppPackage() == null
                ) {
                    pendingAction = null
                    return
                }

                when {
                    controller.requiresWriteSettingsPermissionForEnable() &&
                        !controller.hasWriteSettingsPermission() -> {
                        permissionRequestInFlight = PERMISSION_WRITE_SETTINGS
                        controller.requestWriteSettingsPermission()
                        return
                    }
                    controller.requiresNotificationPolicyAccessForEnable() &&
                        !controller.hasNotificationPolicyAccess() -> {
                        permissionRequestInFlight = PERMISSION_NOTIFICATION_POLICY
                        controller.requestNotificationPolicyAccess()
                        return
                    }
                    else -> {
                        val enabled = controller.enableGameMode()
                        pendingAction = null
                        if (enabled) finish()
                    }
                }
            }
            ACTION_DISABLE -> {
                when {
                    controller.requiresWriteSettingsPermissionForDisable() &&
                        !controller.hasWriteSettingsPermission() -> {
                        permissionRequestInFlight = PERMISSION_WRITE_SETTINGS
                        controller.requestWriteSettingsPermission()
                        return
                    }
                    controller.requiresNotificationPolicyAccessForDisable() &&
                        !controller.hasNotificationPolicyAccess() -> {
                        permissionRequestInFlight = PERMISSION_NOTIFICATION_POLICY
                        controller.requestNotificationPolicyAccess()
                        return
                    }
                    else -> {
                        controller.disableGameMode()
                        pendingAction = null
                        finish()
                    }
                }
            }
        }
    }

    private fun hasPermission(permission: String): Boolean = when (permission) {
        PERMISSION_WRITE_SETTINGS -> controller.hasWriteSettingsPermission()
        PERMISSION_NOTIFICATION_POLICY -> controller.hasNotificationPolicyAccess()
        else -> true
    }

    private fun actionFromIntent(intent: android.content.Intent): String? {
        return intent.getStringExtra(EXTRA_ACTION) ?: when (intent.component?.className) {
            "$packageName.EnableGameModeActivity" -> ACTION_ENABLE
            "$packageName.DisableGameModeActivity" -> ACTION_DISABLE
            else -> null
        }
    }

    companion object {
        const val EXTRA_ACTION = "extra.action"
        const val ACTION_ENABLE = "enable"
        const val ACTION_DISABLE = "disable"

        private const val PERMISSION_WRITE_SETTINGS = "write_settings"
        private const val PERMISSION_NOTIFICATION_POLICY = "notification_policy"
    }
}
