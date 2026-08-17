package com.localcharacter.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.ui.AppViewModel
import com.localcharacter.app.ui.components.CharacterAvatar
import com.localcharacter.app.ui.components.DetailTopBar
import com.localcharacter.app.ui.components.EmptyState
import com.localcharacter.app.ui.components.SectionHeader
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    id: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onChat: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAiSettings: (String) -> Unit,
    onBehaviorSettings: (String) -> Unit,
) {
    val characters by viewModel.characters.collectAsState()
    val installedVoices by viewModel.installedVoices.collectAsState()
    val remoteVoices by viewModel.remoteVoices.collectAsState()
    val character = characters.firstOrNull { it.id == id }
    val recommendedInstalled = character?.recommendedVoiceId?.let { recommendation ->
        installedVoices.firstOrNull { it.id == recommendation || it.remoteId == recommendation }
    }
    val recommendedRemote = character?.recommendedVoiceId?.let { recommendation ->
        remoteVoices.firstOrNull { it.remoteId == recommendation }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportCharacter(id, uri)
    }
    Scaffold(topBar = {
        DetailTopBar(character?.name.orEmpty(), onBack)
    }) { padding ->
        if (character == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { EmptyState("Personaje no encontrado", "Puede que haya sido eliminado.") }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                CharacterAvatar(character.name, Modifier.size(128.dp), character.avatarUri)
                Spacer(Modifier.height(16.dp))
                Text(character.name, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(character.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { character.tags.take(4).forEach { AssistChip(onClick = {}, label = { Text(it) }) } }
                Spacer(Modifier.height(22.dp))
                Button(onClick = { viewModel.openChat(character.id, onReady = onChat) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Chatear")
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onEdit(character.id) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(5.dp)); Text("Editar") }
                    OutlinedButton(onClick = { viewModel.duplicateCharacter(character) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Duplicar") }
                }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { onAiSettings(character.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Motor de IA de este personaje") }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { onBehaviorSettings(character.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Voz y contenido") }
                if (character.recommendedVoiceId != null) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f)),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Este personaje tiene una voz recomendada", style = MaterialTheme.typography.titleSmall)
                            Text(recommendedInstalled?.name ?: recommendedRemote?.name ?: character.recommendedVoiceId)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (recommendedInstalled != null) {
                                    TextButton(onClick = { viewModel.testVoice(recommendedInstalled) }) { Text("Escuchar") }
                                } else if (recommendedRemote != null) {
                                    TextButton(onClick = { viewModel.installVoice(recommendedRemote) }) { Text("Descargar") }
                                }
                                TextButton(onClick = {}) { Text("Ahora no") }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { export.launch("${character.name}.json") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Exportar") }
                    OutlinedButton(onClick = { viewModel.deleteCharacter(character, onBack) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(5.dp)); Text("Eliminar") }
                }
                Spacer(Modifier.height(28.dp))
                DetailSection("Personalidad", character.personality.ifBlank { "Sin personalidad definida." })
                DetailSection("Escenario", character.scenario.ifBlank { "Sin escenario definido." })
                DetailSection("Mensaje inicial", character.firstMessage.ifBlank { "Sin mensaje inicial." })
                if (character.creatorNotes.isNotBlank()) DetailSection("Notas del creador", character.creatorNotes)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: String) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f))) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(7.dp))
            Text(content, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditorScreen(id: String?, viewModel: AppViewModel, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val characters by viewModel.characters.collectAsState()
    val existing = characters.firstOrNull { it.id == id }
    val stableId = remember(id) { id ?: UUID.randomUUID().toString() }
    var initialized by remember(id) { mutableStateOf(id == null) }
    var name by rememberSaveable(id) { mutableStateOf("") }
    var description by rememberSaveable(id) { mutableStateOf("") }
    var personality by rememberSaveable(id) { mutableStateOf("") }
    var scenario by rememberSaveable(id) { mutableStateOf("") }
    var firstMessage by rememberSaveable(id) { mutableStateOf("") }
    var examples by rememberSaveable(id) { mutableStateOf("") }
    var systemPrompt by rememberSaveable(id) { mutableStateOf("") }
    var lore by rememberSaveable(id) { mutableStateOf("") }
    var greetings by rememberSaveable(id) { mutableStateOf("") }
    var tags by rememberSaveable(id) { mutableStateOf("") }
    var creatorNotes by rememberSaveable(id) { mutableStateOf("") }
    var avatarUri by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var selectedAvatarUri by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var removeAvatar by rememberSaveable(id) { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            avatarUri = uri.toString()
            selectedAvatarUri = uri.toString()
            removeAvatar = false
        }
    }
    LaunchedEffect(existing?.id) {
        if (!initialized && existing != null) {
            name = existing.name; description = existing.description; personality = existing.personality
            scenario = existing.scenario; firstMessage = existing.firstMessage; examples = existing.exampleMessages
            systemPrompt = existing.systemPrompt; greetings = existing.alternateGreetings.joinToString("\n")
            tags = existing.tags.joinToString(", "); creatorNotes = existing.creatorNotes
            avatarUri = existing.avatarUri; selectedAvatarUri = null; removeAvatar = false; initialized = true
        }
    }
    val save = {
        val now = System.currentTimeMillis()
        val character = Character(
            id = stableId, name = name.trim(), avatarUri = avatarUri, description = description.trim(), personality = personality.trim(),
            scenario = scenario.trim(), firstMessage = firstMessage.trim(), exampleMessages = examples.trim(),
            systemPrompt = systemPrompt.trim(), creatorNotes = creatorNotes.trim(),
            tags = tags.split(',').map(String::trim).filter(String::isNotBlank).distinct(),
            alternateGreetings = greetings.lines().map(String::trim).filter(String::isNotBlank),
            createdAt = existing?.createdAt ?: now, updatedAt = now,
        )
        if (character.name.isNotBlank()) {
            viewModel.saveCharacterWithAvatar(
                character = character,
                selectedAvatar = selectedAvatarUri?.let(android.net.Uri::parse),
                removeAvatar = removeAvatar,
                onSaved = onSaved,
            )
        }
    }
    Scaffold(topBar = {
        DetailTopBar(if (id == null) "Crear personaje" else "Editar personaje", onBack) {
            IconButton(onClick = save, enabled = name.isNotBlank()) { Icon(Icons.Default.Check, "Guardar") }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CharacterAvatar(name.ifBlank { "?" }, Modifier.size(112.dp), avatarUri)
                    Text("Identidad", style = MaterialTheme.typography.titleLarge)
                    Text("Dale una voz, una foto y una presencia reconocibles.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Edit, null)
                            Spacer(Modifier.width(7.dp))
                            Text(if (avatarUri == null) "Elegir foto" else "Cambiar foto")
                        }
                        if (avatarUri != null) {
                            OutlinedButton(
                                onClick = {
                                    avatarUri = null
                                    selectedAvatarUri = null
                                    removeAvatar = true
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Delete, null)
                                Spacer(Modifier.width(7.dp))
                                Text("Quitar")
                            }
                        }
                    }
                }
            }
            item { EditorSection("Esencial") {
                EditorField(name, { name = it }, "Nombre", singleLine = true)
                EditorField(description, { description = it }, "Descripción corta", minLines = 2)
                EditorField(tags, { tags = it }, "Etiquetas separadas por comas", singleLine = true)
            } }
            item { EditorSection("Voz y personalidad") {
                EditorField(personality, { personality = it }, "Personalidad", minLines = 4)
                EditorField(systemPrompt, { systemPrompt = it }, "System prompt personalizado", minLines = 4)
                EditorField(examples, { examples = it }, "Ejemplos de conversación", minLines = 4)
            } }
            item { EditorSection("Mundo y comienzo") {
                EditorField(scenario, { scenario = it }, "Escenario", minLines = 4)
                EditorField(firstMessage, { firstMessage = it }, "Mensaje inicial", minLines = 4)
                EditorField(greetings, { greetings = it }, "Saludos alternativos (uno por línea)", minLines = 3)
            } }
            item { EditorSection("Lorebook rápido") {
                EditorField(lore, { lore = it }, "Notas de lore (puedes refinarlas después)", minLines = 4)
                EditorField(creatorNotes, { creatorNotes = it }, "Notas del creador", minLines = 3)
            } }
            item { Button(onClick = save, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("Guardar personaje") } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun EditorSection(title: String, fields: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(title)
            fields()
        }
    }
}

@Composable
private fun EditorField(value: String, onChange: (String) -> Unit, label: String, singleLine: Boolean = false, minLines: Int = 1) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, minLines = minLines, shape = RoundedCornerShape(16.dp))
}
