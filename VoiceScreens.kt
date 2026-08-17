package com.localcharacter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcharacter.app.data.voice.RemoteVoice
import com.localcharacter.app.domain.model.VoiceEngineType
import com.localcharacter.app.tts.TtsPlaybackState
import com.localcharacter.app.ui.AppViewModel
import com.localcharacter.app.ui.components.DetailTopBar
import com.localcharacter.app.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(viewModel: AppViewModel, onBack: () -> Unit, onRepositories: () -> Unit) {
    val settings by viewModel.ttsSettings.collectAsState()
    val installed by viewModel.installedVoices.collectAsState()
    val state by viewModel.ttsState.collectAsState()
    Scaffold(
        topBar = {
            DetailTopBar("Voces", onBack)
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionHeader("Reproducción") }
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        VoiceSwitch("Auto reproducir respuestas", settings.autoPlayResponses) {
                            viewModel.setTtsSettings(settings.copy(autoPlayResponses = it))
                        }
                        VoiceSwitch("Usar TTS de Android si falta la voz", settings.systemFallback) {
                            viewModel.setTtsSettings(settings.copy(systemFallback = it))
                        }
                        VoiceSwitch("Descargar voz de RAM cuando no se usa", settings.unloadWhenIdle) {
                            viewModel.setTtsSettings(settings.copy(unloadWhenIdle = it))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = viewModel::testSystemVoice) {
                                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Probar TTS del sistema")
                            }
                            if (state !is TtsPlaybackState.Idle) {
                                OutlinedButton(onClick = viewModel::stopVoice) { Text("Detener") }
                            }
                        }
                        if (state is TtsPlaybackState.Failed) {
                            Text((state as TtsPlaybackState.Failed).message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item {
                Button(onClick = onRepositories, modifier = Modifier.fillMaxWidth()) { Text("Repositorios de voces") }
            }
            item { SectionHeader("Voces instaladas") }
            if (installed.isEmpty()) {
                item { Text("No hay voces locales instaladas. El TTS del sistema puede usarse como fallback.") }
            } else {
                items(installed, key = { it.id }) { voice ->
                    Card(shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(voice.name, style = MaterialTheme.typography.titleMedium)
                            Text("${voice.language} · ${voice.engine} · ${formatVoiceBytes(voice.sizeBytes)}")
                            Text("${voice.author} · ${voice.license}", style = MaterialTheme.typography.bodySmall)
                            Text("Versión ${voice.version}", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { viewModel.testVoice(voice) }) {
                                    Icon(Icons.Default.PlayArrow, null); Text("Probar")
                                }
                                OutlinedButton(onClick = { viewModel.deleteVoice(voice) }) {
                                    Icon(Icons.Default.Delete, null); Text("Eliminar")
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(50.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRepositorySettingsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val repositories by viewModel.voiceRepositories.collectAsState()
    val remote by viewModel.remoteVoices.collectAsState()
    val installed by viewModel.installedVoices.collectAsState()
    val operation by viewModel.voiceOperation.collectAsState()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var engine by rememberSaveable { mutableStateOf<VoiceEngineType?>(null) }
    var language by rememberSaveable { mutableStateOf<String?>(null) }
    var installationFilter by rememberSaveable { mutableStateOf("TODAS") }
    val installedKeys = installed.map { it.repositoryId to it.remoteId }.toSet()
    val languages = remember(remote) { remote.map { it.language }.distinct().sorted() }
    LaunchedEffect(repositories.map { it.id to it.enabled }) {
        if (remote.isEmpty() && repositories.any { it.enabled } && operation == null) {
            viewModel.syncVoiceRepositories()
        }
    }
    val filtered = remote.filter { voice ->
        val isInstalled = voice.repositoryId to voice.remoteId in installedKeys
        voice.name.contains(query, ignoreCase = true) &&
            (engine == null || voice.engine == engine) &&
            (language == null || voice.language == language) &&
            (installationFilter == "TODAS" || installationFilter == "INSTALADAS" && isInstalled || installationFilter == "NO_INSTALADAS" && !isInstalled)
    }
    Scaffold(
        topBar = {
            DetailTopBar("Repositorios de voces", onBack) {
                IconButton(onClick = viewModel::syncVoiceRepositories, enabled = operation == null) {
                    Icon(Icons.Default.Refresh, "Sincronizar")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (operation != null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Añadir repositorio") } }
            item { SectionHeader("Repositorios activos") }
            if (repositories.isEmpty()) item { Text("Añade una URL HTTPS de voice-repository.json.") }
            items(repositories, key = { it.id }) { repository ->
                Card(shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(repository.name, style = MaterialTheme.typography.titleMedium)
                            Text(repository.manifestUrl, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                        Switch(repository.enabled, { viewModel.setVoiceRepositoryEnabled(repository.id, it) })
                        IconButton(onClick = { viewModel.deleteVoiceRepository(repository.id) }) {
                            Icon(Icons.Default.Delete, "Eliminar repositorio")
                        }
                    }
                }
            }
            item { SectionHeader("Catálogo") }
            item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Buscar voz") }, singleLine = true) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(engine == null, { engine = null }, { Text("Todos los motores") })
                    listOf(VoiceEngineType.KOKORO, VoiceEngineType.PIPER, VoiceEngineType.VITS).forEach { value ->
                        FilterChip(engine == value, { engine = value }, { Text(value.name) })
                    }
                }
            }
            if (languages.isNotEmpty()) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(language == null, { language = null }, { Text("Todos los idiomas") })
                    languages.forEach { value -> FilterChip(language == value, { language = value }, { Text(value) }) }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("TODAS" to "Todas", "INSTALADAS" to "Instaladas", "NO_INSTALADAS" to "No instaladas").forEach { (value, label) ->
                        FilterChip(installationFilter == value, { installationFilter = value }, { Text(label) })
                    }
                }
            }
            items(filtered, key = { "${it.repositoryId}/${it.remoteId}" }) { voice ->
                RemoteVoiceCard(voice, voice.repositoryId to voice.remoteId in installedKeys, operation != null) {
                    viewModel.installVoice(voice)
                }
            }
            item { Spacer(Modifier.height(50.dp)) }
        }
    }
    if (showAdd) {
        var url by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Añadir repositorio de voces") },
            text = { OutlinedTextField(url, { url = it }, label = { Text("URL HTTPS de voice-repository.json") }) },
            confirmButton = {
                TextButton(onClick = { viewModel.addVoiceRepository(url); showAdd = false }, enabled = url.startsWith("https://")) {
                    Text("Añadir y probar")
                }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun RemoteVoiceCard(voice: RemoteVoice, installed: Boolean, busy: Boolean, onInstall: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(voice.name, style = MaterialTheme.typography.titleMedium)
            Text("${voice.language} · ${voice.engine} · ${formatVoiceBytes(voice.sizeBytes)}")
            Text("Autor: ${voice.author}\nLicencia: ${voice.license}\nVersión: ${voice.version}", style = MaterialTheme.typography.bodySmall)
            Text("Fuente: ${voice.source}", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onInstall, enabled = !installed && !busy) { Text(if (installed) "Instalada" else "Descargar voz") }
        }
    }
}

@Composable
private fun VoiceSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChecked)
    }
}

private fun formatVoiceBytes(value: Long): String = if (value >= 1_073_741_824L) {
    "%.2f GB".format(value / 1_073_741_824.0)
} else "%.0f MB".format(value / 1_048_576.0)
