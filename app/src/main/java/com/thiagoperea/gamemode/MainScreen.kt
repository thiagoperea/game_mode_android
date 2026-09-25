package com.thiagoperea.gamemode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thiagoperea.gamemode.internal.GameModeController
import com.thiagoperea.gamemode.internal.GameModeConfiguration
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    controller: GameModeController,
    modifier: Modifier
) {
    val context = LocalContext.current
    val configuredPackage = remember { mutableStateOf(controller.getConfiguredAppPackage()) }
    val configuration = remember { mutableStateOf(controller.getConfiguration()) }
    val launchableApps = remember { loadLaunchableApps(context) }
    val configuredApp = launchableApps.firstOrNull { it.packageName == configuredPackage.value }
    val menuExpanded = remember { mutableStateOf(false) }

    fun updateConfiguration(update: (GameModeConfiguration) -> GameModeConfiguration) {
        val updated = update(configuration.value)
        controller.setConfiguration(updated)
        configuration.value = controller.getConfiguration()
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = if (controller.isGameModeEnabled()) {
                "GameMode ligado"
            } else {
                "GameMode desligado"
            }
        )

        Text(
            text = "Ações ao ligar",
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )

        Text(
            text = "As alterações ficam salvas e serão aplicadas na próxima ativação.",
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ConfigurationSwitch(
            label = "Forçar orientação paisagem",
            checked = configuration.value.forceLandscape,
            onCheckedChange = { enabled ->
                updateConfiguration { it.copy(forceLandscape = enabled) }
            }
        )

        ConfigurationSwitch(
            label = "Ajustar brilho",
            checked = configuration.value.configureBrightness,
            onCheckedChange = { enabled ->
                updateConfiguration { it.copy(configureBrightness = enabled) }
            }
        )
        if (configuration.value.configureBrightness) {
            Text("Brilho: ${brightnessPercent(configuration.value.brightness)}%")
            Slider(
                value = brightnessPercent(configuration.value.brightness).toFloat(),
                onValueChange = { value ->
                    updateConfiguration {
                        it.copy(brightness = brightnessFromPercent(value.toInt()))
                    }
                },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        ConfigurationSwitch(
            label = "Ajustar volume de mídia",
            checked = configuration.value.configureMediaVolume,
            onCheckedChange = { enabled ->
                updateConfiguration { it.copy(configureMediaVolume = enabled) }
            }
        )
        if (configuration.value.configureMediaVolume) {
            Text("Volume de mídia: ${configuration.value.mediaVolumePercent}%")
            Slider(
                value = configuration.value.mediaVolumePercent.toFloat(),
                onValueChange = { value ->
                    updateConfiguration { it.copy(mediaVolumePercent = value.toInt()) }
                },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        ConfigurationSwitch(
            label = "Ativar Não Perturbe",
            checked = configuration.value.enableDoNotDisturb,
            onCheckedChange = { enabled ->
                updateConfiguration { it.copy(enableDoNotDisturb = enabled) }
            }
        )

        ConfigurationSwitch(
            label = "Abrir aplicativo ao ligar",
            checked = configuration.value.launchConfiguredApp,
            onCheckedChange = { enabled ->
                updateConfiguration { it.copy(launchConfiguredApp = enabled) }
            }
        )

        Text(
            text = "Aplicativo para abrir:",
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )

        Text(
            text = "Escolha o aplicativo que será aberto ao ligar o GameMode.",
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Button(
            onClick = { menuExpanded.value = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(configuredApp?.label ?: "Selecionar aplicativo")
        }

        DropdownMenu(
            expanded = menuExpanded.value,
            onDismissRequest = { menuExpanded.value = false }
        ) {
            launchableApps.forEach { app ->
                DropdownMenuItem(
                    text = { Text(app.label) },
                    onClick = {
                        controller.setConfiguredAppPackage(app.packageName)
                        configuredPackage.value = app.packageName
                        menuExpanded.value = false
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ConfigurationSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun brightnessPercent(brightness: Int): Int =
    (brightness.coerceIn(0, 255) * 100 / 255)

private fun brightnessFromPercent(percent: Int): Int =
    (percent.coerceIn(0, 100) * 255 / 100f).roundToInt()

private data class LaunchableApp(
    val packageName: String,
    val label: String
)

private fun loadLaunchableApps(context: android.content.Context): List<LaunchableApp> {
    val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    }

    return context.packageManager
        .queryIntentActivities(launcherIntent, 0)
        .asSequence()
        .map { resolveInfo ->
            LaunchableApp(
                packageName = resolveInfo.activityInfo.packageName,
                label = resolveInfo.loadLabel(context.packageManager).toString()
            )
        }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
        .toList()
}
