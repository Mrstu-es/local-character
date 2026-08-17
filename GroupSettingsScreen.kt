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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcharacter.app.domain.model.GroupConversation
import com.localcharacter.app.domain.model.GroupTurnMode
import com.localcharacter.app.domain.model.GroupContext
import com.localcharacter.app.domain.model.GroupLorePolicy
import com.localcharacter.app.domain.model.GroupParticipantContext
import com.localcharacter.app.ui.AppViewModel
import com.localcharacter.app.ui.components.CharacterAvatar
import com.localcharacter.app.ui.components.DetailTopBar

@Composable
fun GroupSettingsScreen(groupId: String, viewModel: AppViewModel, onBack: () -> Unit) {
    val group = viewModel.groups.collectAsStateWithLifecycle().value.firstOrNull { it.id == groupId }
    val members = viewModel.groupMembers.collectAsStateWithLifecycle().value[groupId].orEmpty()
    val allCharacters = viewModel.characters.collectAsStateWithLifecycle().value
    val contextFlow = remember(groupId) { viewModel.groupContext(groupId) }
    val savedContext = contextFlow.collectAsStateWithLifecycle(initialValue = GroupContext(groupId)).value ?: GroupContext(groupId)
    if (group == null) return
    LaunchedEffect(groupId) { viewModel.refreshGroupMembers(groupId) }
    var name by rememberSaveable(group.id) { mutableStateOf(group.name) }
    var mode by rememberSaveable(group.id) { mutableStateOf(group.turnMode) }
    var maxResponses by rememberSaveable(group.id) { mutableIntStateOf(group.maxAutoResponses) }
    var maxChain by rememberSaveable(group.id) { mutableIntStateOf(group.maxBotChain) }
    var sharedMemory by rememberSaveable(group.id) { mutableStateOf(group.sharedMemoryEnabled) }
    var contextTitle by rememberSaveable(group.id) { mutableStateOf(savedContext.title) }
    var description by rememberSaveable(group.id) { mutableStateOf(savedContext.description) }
    var scenario by rememberSaveable(group.id) { mutableStateOf(savedContext.scenario) }
    var userRole by rememberSaveable(group.id) { mutableStateOf(savedContext.userRole) }
    var worldRules by rememberSaveable(group.id) { mutableStateOf(savedContext.worldRules) }
    var initialSituation by rememberSaveable(group.id) { mutableStateOf(savedContext.initialSituation) }
    var openingMessage by rememberSaveable(group.id) { mutableStateOf(savedContext.openingMessage) }
    var notes by rememberSaveable(group.id) { mutableStateOf(savedContext.notes) }
    var currentLocation by rememberSaveable(group.id) { mutableStateOf(savedContext.currentLocation) }
    var currentSituation by rememberSaveable(group.id) { mutableStateOf(savedContext.currentSituation) }
    var stateSummary by rememberSaveable(group.id) { mutableStateOf(savedContext.stateSummary) }
    var lorePolicy by rememberSaveable(group.id) { mutableStateOf(savedContext.lorePolicy) }
    var selectedParticipant by rememberSaveable(group.id) { mutableStateOf<String?>(null) }
    var participantRole by rememberSaveable(group.id) { mutableStateOf("") }
    var participantScenario by rememberSaveable(group.id) { mutableStateOf("") }
    var participantRelationship by rememberSaveable(group.id) { mutableStateOf("") }
    var participantNotes by rememberSaveable(group.id) { mutableStateOf("") }
    LaunchedEffect(savedContext.groupId, savedContext.updatedAt) {
        if (contextTitle.isBlank()) contextTitle = savedContext.title
        if (description.isBlank()) description = savedContext.description
        if (scenario.isBlank()) scenario = savedContext.scenario
        if (userRole.isBlank()) userRole = savedContext.userRole
        if (worldRules.isBlank()) worldRules = savedContext.worldRules
        if (initialSituation.isBlank()) initialSituation = savedContext.initialSituation
        if (openingMessage.isBlank()) openingMessage = savedContext.openingMessage
        if (notes.isBlank()) notes = savedContext.notes
        if (currentLocation.isBlank()) currentLocation = savedContext.currentLocation
        if (currentSituation.isBlank()) currentSituation = savedContext.currentSituation
        if (stateSummary.isBlank()) stateSummary = savedContext.stateSummary
        lorePolicy = savedContext.lorePolicy
    }
    Scaffold(topBar = { DetailTopBar("Detalles del grupo", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nombre") }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                Button(onClick = { viewModel.renameGroup(group.id, name); viewModel.updateGroup(group.copy(turnMode = mode, maxAutoResponses = maxResponses.coerceIn(1, 3), maxBotChain = maxChain.coerceIn(1, 3), sharedMemoryEnabled = sharedMemory)) }, modifier = Modifier.fillMaxWidth()) { Text("Guardar cambios") }
            }
            item { Text("Contexto y escenario del grupo", style = MaterialTheme.typography.titleMedium) }
            item {
                OutlinedTextField(contextTitle, { contextTitle = it }, Modifier.fillMaxWidth(), label = { Text("Título del contexto") }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Descripción del grupo") }, minLines = 2)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(scenario, { scenario = it }, Modifier.fillMaxWidth(), label = { Text("Escenario compartido") }, minLines = 3)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(userRole, { userRole = it }, Modifier.fillMaxWidth(), label = { Text("Rol del usuario") }, minLines = 2)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(worldRules, { worldRules = it }, Modifier.fillMaxWidth(), label = { Text("Reglas del mundo") }, minLines = 2)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(initialSituation, { initialSituation = it }, Modifier.fillMaxWidth(), label = { Text("Situación inicial") }, minLines = 2)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(openingMessage, { openingMessage = it }, Modifier.fillMaxWidth(), label = { Text("Mensaje de apertura opcional") }, minLines = 2)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notas internas") }, minLines = 2)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(currentLocation, { currentLocation = it }, Modifier.fillMaxWidth(), label = { Text("Ubicación actual") }, minLines = 1)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(currentSituation, { currentSituation = it }, Modifier.fillMaxWidth(), label = { Text("Situación actual") }, minLines = 2)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(stateSummary, { stateSummary = it }, Modifier.fillMaxWidth(), label = { Text("Estado/lore del grupo") }, minLines = 2)
                Spacer(Modifier.height(8.dp))
                Text("Lore de las fichas", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupLorePolicy.values().forEach { policy ->
                        FilterChip(
                            selected = lorePolicy == policy,
                            onClick = { lorePolicy = policy },
                            label = { Text(when (policy) { GroupLorePolicy.ADAPTIVE -> "Adaptativo"; GroupLorePolicy.ORIGINAL -> "Original"; GroupLorePolicy.DISABLED -> "Desactivado" }) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.saveGroupContext(GroupContext(group.id, contextTitle, description, scenario, userRole, worldRules, initialSituation, openingMessage, notes, lorePolicy, currentLocation, currentSituation, stateSummary, savedContext.version, savedContext.createdAt)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Guardar contexto") }
            }
            item { Text("Turnos", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupTurnMode.values().forEach { item -> FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(when (item) { GroupTurnMode.SMART -> "Inteligentes"; GroupTurnMode.ROUND_ROBIN -> "Por orden"; GroupTurnMode.MANUAL -> "Manuales" }) }) }
                }
            }
            item { Text("Respuestas automáticas por turno: $maxResponses", style = MaterialTheme.typography.bodyMedium); Row(verticalAlignment = Alignment.CenterVertically) { TextButton(enabled = maxResponses > 1, onClick = { maxResponses-- }) { Text("−") }; TextButton(enabled = maxResponses < 3, onClick = { maxResponses++ }) { Text("+") } } }
            item { Text("Cadena bot-a-bot máxima: $maxChain", style = MaterialTheme.typography.bodyMedium); Row(verticalAlignment = Alignment.CenterVertically) { TextButton(enabled = maxChain > 1, onClick = { maxChain-- }) { Text("−") }; TextButton(enabled = maxChain < 3, onClick = { maxChain++ }) { Text("+") } } }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Memoria compartida del grupo", Modifier.weight(1f)); Switch(sharedMemory, { sharedMemory = it }) } }
            item { Text("Participantes", style = MaterialTheme.typography.titleMedium) }
            items(allCharacters, key = { it.id }) { character ->
                val active = members.any { it.id == character.id }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = active,
                        onCheckedChange = { checked ->
                            if (checked) viewModel.addGroupParticipant(group.id, character.id, members.size)
                            else if (members.size > 2) viewModel.removeGroupParticipant(group.id, character.id)
                        },
                    )
                    CharacterAvatar(character.name, Modifier.size(42.dp), character.avatarUri)
                    Text(character.name, Modifier.weight(1f).padding(horizontal = 10.dp))
                    if (active) Text("Activo", color = MaterialTheme.colorScheme.primary)
                }
            }
            item {
                Text("Overrides por participante", style = MaterialTheme.typography.titleMedium)
                Text("Opcional: ajusta el rol o escenario de un personaje sin modificar su ficha.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    members.forEach { member ->
                        val character = allCharacters.firstOrNull { it.id == member.id }
                        character?.let {
                            FilterChip(selected = selectedParticipant == it.id, onClick = { selectedParticipant = it.id }, label = { Text(it.name) })
                        }
                    }
                }
                if (selectedParticipant != null) {
                    OutlinedTextField(participantRole, { participantRole = it }, Modifier.fillMaxWidth(), label = { Text("Rol en este grupo") }, minLines = 2)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(participantScenario, { participantScenario = it }, Modifier.fillMaxWidth(), label = { Text("Escenario específico") }, minLines = 2)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(participantRelationship, { participantRelationship = it }, Modifier.fillMaxWidth(), label = { Text("Relación con el usuario/grupo") }, minLines = 2)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(participantNotes, { participantNotes = it }, Modifier.fillMaxWidth(), label = { Text("Notas del participante") }, minLines = 2)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = {
                        viewModel.saveParticipantContext(GroupParticipantContext(group.id, selectedParticipant!!, participantRole, participantScenario, participantRelationship, participantRelationship, participantNotes))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Guardar override") }
                }
            }
            item { Text("Los personajes se referencian desde tu biblioteca; quitarlos del grupo no borra sus chats ni memorias privadas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
