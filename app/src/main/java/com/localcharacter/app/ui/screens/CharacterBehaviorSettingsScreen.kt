package com.localcharacter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcharacter.app.domain.model.CharacterContentOverride
import com.localcharacter.app.domain.model.CharacterPreferences
import com.localcharacter.app.domain.model.ContentMode
import com.localcharacter.app.domain.model.TtsReadMode
import com.localcharacter.app.domain.model.VoiceAutoplayOverride
import com.localcharacter.app.tts.TtsPlaybackState
import com.localcharacter.app.ui.AppViewModel
import com.localcharacter.app.ui.components.DetailTopBar
import com.localcharacter.app.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterBehaviorSettingsScreen(characterId: String, viewModel: AppViewModel, onBack: () -> Unit) {
    val characters by viewModel.characters.collectAsState()
    val globalMode by viewModel.contentMode.collectAsState()
    val adultConfirmed by viewModel.adultContentConfirmed.collectAsState()
    val preferencesFlow = remember(characterId) { viewModel.characterPreferences(characterId) }
    val preferences by preferencesFlow.collectAsState(initial = CharacterPreferences(characterId))
    val voices by viewModel.installedVoices.collectAsState()
    val ttsState by viewModel.ttsState.collectAsState()
    var confirmAdultOverride by remember { mutableStateOf(false) }
    var showVoicePicker by remember { mutableStateOf(false) }
    val characterName = characters.firstOrNull { it.id == characterId }?.name ?: "Personaje"
    val selectedVoice = voices.firstOrNull { it.id == preferences.voiceId }

    fun setOverride(value: CharacterContentOverride) {
        if (value == CharacterContentOverride.ADULT_ENABLED && !adultConfirmed) {
            confirmAdultOverride = true
        } else {
            viewModel.saveCharacterPreferences(preferences.copy(contentOverride = value))
        }
    }

    Scaffold(
        topBar = {
            DetailTopBar("Voz y contenido", onBack)
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text(characterName, style = MaterialTheme.typography.headlineSmall) }
            item { SectionHeader("Contenido") }
            item {
                Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Preferencia para este personaje", style = MaterialTheme.typography.titleMedium)
                        CharacterContentOverride.entries.forEach { option ->
                            FilterChip(
                                selected = preferences.contentOverride == option,
                                onClick = { setOverride(option) },
                                label = {
                                    Text(when (option) {
                                        CharacterContentOverride.USE_GLOBAL -> "Usar configuración global (${if (globalMode == ContentMode.STANDARD) "Estándar" else "Adulto"})"
                                        CharacterContentOverride.STANDARD -> "Estándar"
                                        CharacterContentOverride.ADULT_ENABLED -> "Adulto habilitado"
                                    })
                                },
                            )
                        }
                        Text(
                            "Esta preferencia sólo cambia las instrucciones internas de la app. No modifica las políticas de servicios externos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { SectionHeader("Voz") }
            item {
                Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(selectedVoice?.name ?: "Sin voz", style = MaterialTheme.typography.titleMedium)
                        Text(
                            selectedVoice?.let { "${it.engine} · ${it.language}" }
                                ?: "Puedes usar el fallback de Android o asignar una voz local.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showVoicePicker = true }) { Text("Cambiar voz") }
                            if (selectedVoice != null) {
                                FilledTonalButton(onClick = { viewModel.testVoice(selectedVoice) }) { Text("▶ Probar") }
                            }
                            if (ttsState !is TtsPlaybackState.Idle) {
                                TextButton(onClick = viewModel::stopVoice) { Text("Detener") }
                            }
                        }
                        Text("Auto reproducir", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VoiceAutoplayOverride.entries.forEach { value ->
                                FilterChip(
                                    selected = preferences.autoplayOverride == value,
                                    onClick = { viewModel.saveCharacterPreferences(preferences.copy(autoplayOverride = value)) },
                                    label = { Text(when (value) {
                                        VoiceAutoplayOverride.USE_GLOBAL -> "Global"
                                        VoiceAutoplayOverride.ALWAYS -> "Siempre"
                                        VoiceAutoplayOverride.NEVER -> "Nunca"
                                    }) },
                                )
                            }
                        }
                        VoiceSettingSlider("Velocidad", preferences.speed, .5f..2f) {
                            viewModel.saveCharacterPreferences(preferences.copy(speed = it))
                        }
                        VoiceSettingSlider("Tono", preferences.pitch, .5f..2f) {
                            viewModel.saveCharacterPreferences(preferences.copy(pitch = it))
                        }
                        VoiceSettingSlider("Volumen", preferences.volume, 0f..1f) {
                            viewModel.saveCharacterPreferences(preferences.copy(volume = it))
                        }
                        Text("Qué leer", style = MaterialTheme.typography.titleSmall)
                        TtsReadMode.entries.forEach { value ->
                            FilterChip(
                                selected = preferences.readMode == value,
                                onClick = { viewModel.saveCharacterPreferences(preferences.copy(readMode = value)) },
                                label = { Text(if (value == TtsReadMode.DIALOGUE_ONLY) "Sólo diálogo" else "Diálogo y acciones") },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmAdultOverride) {
        AlertDialog(
            onDismissRequest = { confirmAdultOverride = false },
            title = { Text("Confirmación para adultos") },
            text = { Text("Confirma que eres una persona adulta y deseas habilitar esta preferencia sólo para $characterName.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmAdultContent()
                    viewModel.saveCharacterPreferences(
                        preferences.copy(contentOverride = CharacterContentOverride.ADULT_ENABLED),
                    )
                    confirmAdultOverride = false
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { confirmAdultOverride = false }) { Text("Cancelar") } },
        )
    }
    if (showVoicePicker) {
        AlertDialog(
            onDismissRequest = { showVoicePicker = false },
            title = { Text("Voz de $characterName") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = preferences.voiceId == null,
                            onClick = {
                                viewModel.saveCharacterPreferences(preferences.copy(voiceId = null))
                                showVoicePicker = false
                            },
                            label = { Text("Sin voz") },
                        )
                    }
                    items(voices, key = { it.id }) { voice ->
                        FilterChip(
                            selected = preferences.voiceId == voice.id,
                            onClick = {
                                viewModel.saveCharacterPreferences(preferences.copy(voiceId = voice.id))
                                showVoicePicker = false
                            },
                            label = { Text("${voice.name} · ${voice.language} · ${voice.engine}") },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVoicePicker = false }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun VoiceSettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChangeFinished: (Float) -> Unit,
) {
    var local by remember(value) { mutableStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f)); Text("%.2f".format(local)) }
        Slider(
            value = local.coerceIn(range.start, range.endInclusive),
            onValueChange = { local = it },
            onValueChangeFinished = { onChangeFinished(local) },
            valueRange = range,
        )
    }
}
