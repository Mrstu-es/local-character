package com.localcharacter.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import com.localcharacter.app.data.settings.ThemeMode
import com.localcharacter.app.data.settings.MemoryLevel
import com.localcharacter.app.data.settings.CatalogLanguage
import com.localcharacter.app.data.model.ModelLoadPolicy
import com.localcharacter.app.device.CompatibilityLevel
import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.Conversation
import com.localcharacter.app.domain.model.ModelDescriptor
import com.localcharacter.app.domain.model.GenerationSettings
import com.localcharacter.app.domain.model.ContentMode
import com.localcharacter.app.domain.model.GroupContext
import com.localcharacter.app.domain.model.GroupLorePolicy
import com.localcharacter.app.llm.LlmState
import com.localcharacter.app.llm.provider.LlmModelInfo
import com.localcharacter.app.llm.provider.PricingType
import com.localcharacter.app.ui.AppViewModel
import com.localcharacter.app.ui.components.CharacterAvatar
import com.localcharacter.app.ui.components.CharacterCard
import com.localcharacter.app.ui.components.EmptyState
import com.localcharacter.app.ui.components.HomeTopBar
import com.localcharacter.app.ui.components.LoadingIndicator
import com.localcharacter.app.ui.components.MainScreenTopBar
import com.localcharacter.app.ui.components.SectionHeader
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: AppViewModel, onCharacter: (String) -> Unit, onExplore: () -> Unit, onSettings: () -> Unit) {
    val characters by viewModel.characters.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val recentCharacters = remember(characters, conversations) {
        val recentIds = conversations.asSequence().map { it.characterId }.toSet()
        characters.filter { it.id in recentIds }
    }
    Scaffold(
        topBar = {
            HomeTopBar(
                title = "Local Character",
                subtitle = "Procesamiento local",
            ) {
                IconButton(onClick = onExplore) { Icon(Icons.Default.Search, "Buscar") }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Ajustes") }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 112.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(12.dp))
                    Text("Para ti", style = MaterialTheme.typography.headlineMedium)
                    Text("Historias privadas que viven en tu dispositivo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(26.dp))
                }
            }
            if (recentCharacters.isNotEmpty()) {
                item { Column(Modifier.padding(horizontal = 20.dp)) { SectionHeader("Continúa conversando") } }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recentCharacters, key = { it.id }, contentType = { "character" }) { CharacterCard(it, { onCharacter(it.id) }) }
                    }
                }
            }
            item { Column(Modifier.padding(horizontal = 20.dp)) { Spacer(Modifier.height(12.dp)); SectionHeader("Tus personajes", "Explorar", onExplore) } }
            if (characters.isEmpty()) item { EmptyState("Aún no hay personajes", "Crea uno o importa una Character Card para empezar.", "Explorar", onExplore) }
            else item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(characters, key = { it.id }, contentType = { "character" }) { CharacterCard(it, { onCharacter(it.id) }) }
                }
            }
            item {
                Card(
                    Modifier.padding(20.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Privado por diseño", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Tus personajes, recuerdos y conversaciones permanecen en este dispositivo. Sin cuentas, analítica ni telemetría.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyExploreScreen(viewModel: AppViewModel, onCharacter: (String) -> Unit, onCreate: () -> Unit) {
    val characters by viewModel.characters.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Todos") }
    val categories = listOf("Todos", "Anime", "Videojuegos", "Películas", "Original", "RPG", "Aventura", "Romance", "Fantasía", "Ciencia ficción")
    val filtered = characters.filter { character ->
        (query.isBlank() || character.name.contains(query, true) || character.description.contains(query, true) || character.tags.any { it.contains(query, true) }) &&
            (category == "Todos" || character.tags.any { it.equals(category, true) })
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.importCharacter(it, onCharacter) } }
    Scaffold(topBar = { MainScreenTopBar("Explorar") }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Busca personajes, géneros o etiquetas") },
                    singleLine = true, shape = RoundedCornerShape(20.dp),
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { item -> FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) }) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onCreate, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Crear") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "image/png", "*/*")) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Importar") }
                }
            }
            item { SectionHeader(if (query.isBlank()) "Personajes locales" else "Resultados") }
            if (filtered.isEmpty()) item { EmptyState("Sin coincidencias", "Prueba otra búsqueda o crea un personaje original.") }
            items(filtered, key = { it.id }) { character ->
                CharacterListRow(character, onClick = { onCharacter(character.id) })
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }
}

@Composable
private fun CharacterListRow(character: Character, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            CharacterAvatar(character.name, Modifier.size(58.dp), character.avatarUri)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(character.name, style = MaterialTheme.typography.titleMedium)
                Text(character.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (character.tags.isNotEmpty()) Text(character.tags.take(3).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(viewModel: AppViewModel, onConversation: (String) -> Unit, onGroup: (String) -> Unit) {
    val conversations by viewModel.conversations.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val groupMembers by viewModel.groupMembers.collectAsState()
    val characters by viewModel.characters.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var showCreateMenu by rememberSaveable { mutableStateOf(false) }
    var showCreateGroup by rememberSaveable { mutableStateOf(false) }
    val names = remember(characters) { characters.associateBy { it.id } }
    val filtered = remember(conversations, names, query) {
        conversations.filter { it.title.contains(query, true) || names[it.characterId]?.name?.contains(query, true) == true }
    }
    Scaffold(topBar = {
        MainScreenTopBar("Chats") {
            IconButton(onClick = { showCreateMenu = true }) { Icon(Icons.Default.Add, "Nuevo chat o grupo") }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Buscar chats") }, singleLine = true, shape = RoundedCornerShape(20.dp)) }
            if (filtered.isEmpty()) item { EmptyState("No hay conversaciones", "Elige un personaje y su primer mensaje aparecerá aquí.") }
            items(filtered, key = { it.id }, contentType = { "conversation" }) { conversation ->
                val character = names[conversation.characterId]
                ConversationRow(conversation, character?.name ?: conversation.title, character?.avatarUri, { onConversation(conversation.id) }, { viewModel.togglePinned(conversation.id) }, { viewModel.deleteConversation(conversation.id) })
            }
            val filteredGroups = groups.filter { it.name.contains(query, true) }
            if (filteredGroups.isNotEmpty()) item { SectionHeader("Grupos") }
            items(filteredGroups, key = { "group-${it.id}" }, contentType = { "group" }) { group ->
                val members = groupMembers[group.id].orEmpty()
                GroupRow(group.name, group.avatarPath, members, group.isPinned, { onGroup(group.id) }, { viewModel.toggleGroupPinned(group.id) }, { viewModel.deleteGroup(group.id) })
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }
    if (showCreateMenu) {
        AlertDialog(
            onDismissRequest = { showCreateMenu = false }, title = { Text("Nuevo chat") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Elige el tipo de conversación")
                Button(onClick = { showCreateMenu = false; showCreateGroup = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Nuevo grupo") }
                OutlinedButton(onClick = { showCreateMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("Nuevo chat individual desde Explorar") }
            } }, confirmButton = { TextButton(onClick = { showCreateMenu = false }) { Text("Cerrar") } },
        )
    }
    if (showCreateGroup) {
        CreateGroupDialog(
            characters = characters,
            onDismiss = { showCreateGroup = false },
            onCreate = { name, selected, avatar, context -> showCreateGroup = false; viewModel.createGroup(name, selected, avatar, context, onGroup) },
        )
    }
}

@Composable
private fun GroupRow(name: String, avatarUri: String?, members: List<Character>, pinned: Boolean, onClick: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CharacterAvatar(name, Modifier.size(52.dp), avatarUri)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (members.isEmpty()) "Grupo" else members.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onPin) { Icon(Icons.Default.Star, if (pinned) "Desfijar" else "Fijar") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar") }
        }
    }
}

@Composable
private fun CreateGroupDialog(characters: List<Character>, onDismiss: () -> Unit, onCreate: (String, List<String>, String?, GroupContext) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var avatarUri by rememberSaveable { mutableStateOf<String?>(null) }
    var description by rememberSaveable { mutableStateOf("") }
    var scenario by rememberSaveable { mutableStateOf("") }
    var userRole by rememberSaveable { mutableStateOf("") }
    var worldRules by rememberSaveable { mutableStateOf("") }
    var openingMessage by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> avatarUri = uri?.toString() }
    val filtered = remember(characters, query) { characters.filter { it.name.contains(query, true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo grupo") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Descripción del grupo") }, minLines = 2)
                OutlinedTextField(scenario, { scenario = it }, Modifier.fillMaxWidth(), label = { Text("Escenario compartido") }, minLines = 2)
                OutlinedTextField(userRole, { userRole = it }, Modifier.fillMaxWidth(), label = { Text("Rol del usuario") }, minLines = 2)
                OutlinedTextField(worldRules, { worldRules = it }, Modifier.fillMaxWidth(), label = { Text("Reglas del mundo") }, minLines = 2)
                OutlinedTextField(openingMessage, { openingMessage = it }, Modifier.fillMaxWidth(), label = { Text("Mensaje de apertura opcional") }, minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Text(if (avatarUri == null) "👥" else "✓") } }
                    OutlinedButton(onClick = { avatarLauncher.launch("image/*") }) { Text(if (avatarUri == null) "Añadir avatar" else "Cambiar avatar") }
                }
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Buscar personajes") }, singleLine = true)
                Text("Selecciona al menos 2 personajes", style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.height(230.dp)) {
                    items(filtered, key = { it.id }) { character ->
                        Row(Modifier.fillMaxWidth().clickable { selected = if (character.id in selected) selected - character.id else selected + character.id }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = character.id in selected, onCheckedChange = { checked -> selected = if (checked) selected + character.id else selected - character.id })
                            CharacterAvatar(character.name, Modifier.size(38.dp), character.avatarUri)
                            Spacer(Modifier.width(8.dp)); Text(character.name)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(enabled = selected.size >= 2 && name.isNotBlank(), onClick = {
            onCreate(name, selected.toList(), avatarUri, GroupContext(groupId = "", title = name, description = description, scenario = scenario, userRole = userRole, worldRules = worldRules, openingMessage = openingMessage))
        }) { Text("Crear grupo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ConversationRow(conversation: Conversation, characterName: String, avatarUri: String?, onClick: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CharacterAvatar(characterName, Modifier.size(52.dp), avatarUri)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conversation.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                    if (conversation.isPinned) Icon(Icons.Default.Star, "Fijado", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(characterName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(conversation.updatedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onPin) { Icon(Icons.Default.Star, "Fijar") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(viewModel: AppViewModel) {
    val models by viewModel.models.collectAsState()
    val aiSettings by viewModel.aiProviderSettings.collectAsState()
    val providerSummaries by viewModel.providerSummaries.collectAsState()
    val generation by viewModel.generationSettings.collectAsState()
    val engineState by viewModel.llmState.collectAsState()
    val modelPreparation by viewModel.modelPreparation.collectAsState()
    var onlineSearch by rememberSaveable { mutableStateOf("") }
    var onlineFilter by rememberSaveable { mutableStateOf("Todos") }
    var paidModelToConfirm by remember { mutableStateOf<LlmModelInfo?>(null) }
    val providerNames = remember(providerSummaries) {
        providerSummaries.associate { it.definition.providerId to it.definition.displayName }
    }
    val onlineModels = remember(aiSettings.cachedModels, aiSettings.favoriteModels, aiSettings.preferFreeModels, onlineSearch, onlineFilter) {
        aiSettings.cachedModels.values.flatten()
            .asSequence()
            .filter { onlineSearch.isBlank() || it.displayName.contains(onlineSearch, true) || it.modelId.contains(onlineSearch, true) }
            .filter {
                when (onlineFilter) {
                    "Gratis" -> it.pricingType == PricingType.FREE
                    "Free tier" -> it.pricingType == PricingType.FREE_TIER
                    "Pago" -> it.pricingType == PricingType.PAID
                    else -> true
                }
            }
            .sortedWith(compareBy<LlmModelInfo> {
                if ("${it.providerId}/${it.modelId}" in aiSettings.favoriteModels) 0 else 1
            }.thenBy {
                if (aiSettings.preferFreeModels) onlinePricingRank(it.pricingType) else 0
            }.thenBy { it.displayName.lowercase() })
            .take(300)
            .toList()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.addModel(it) } }
    Scaffold(topBar = { MainScreenTopBar("Modelos") }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Tu dispositivo", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(7.dp))
                        Text(viewModel.device.hardware, style = MaterialTheme.typography.bodyMedium)
                        Text("RAM ${formatBytes(viewModel.device.totalRamBytes)} · disponible ${formatBytes(viewModel.device.availableRamBytes)}")
                        Text("${viewModel.device.cpuCores} núcleos · ${viewModel.device.abis.joinToString()} · ${viewModel.device.androidVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Column {
                    Button(onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) }, Modifier.fillMaxWidth(), enabled = modelPreparation == null) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Añadir modelo GGUF")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Admite modelos GGUF de texto compatibles con llama.cpp. El contexto se ajusta al teléfono y la carga tiene fallback local.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                val label = modelPreparation ?: when (val state = engineState) {
                    is LlmState.LoadingModel -> "Cargando ${state.name}…"
                    is LlmState.Generating -> "${state.name} está generando"
                    is LlmState.Stopping -> "Deteniendo inferencia…"
                    else -> ""
                }
                LoadingIndicator(label.isNotBlank(), label)
            }
            item { SectionHeader("LOCAL · Instalados") }
            if (models.isEmpty()) item { EmptyState("No tienes modelos instalados", "Añade un GGUF compatible con llama.cpp. Si Android no permite abrirlo directamente, la app prepara una copia local.", "Seleccionar GGUF") { picker.launch(arrayOf("*/*")) } }
            items(models, key = { it.id }, contentType = { "model" }) { model ->
                ModelRow(viewModel, model, engineState, modelPreparation != null, generation.contextSize)
            }
            item { SectionHeader("ONLINE") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = onlineSearch,
                        onValueChange = { onlineSearch = it },
                        label = { Text("Buscar modelos online") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("Todos", "Gratis", "Free tier", "Pago").forEach { filter ->
                            FilterChip(selected = onlineFilter == filter, onClick = { onlineFilter = filter }, label = { Text(filter) })
                        }
                    }
                }
            }
            if (aiSettings.cachedModels.isEmpty()) {
                item {
                    Text(
                        "Configura una API Key y pulsa Actualizar modelos en Ajustes → Proveedores de IA.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (onlineModels.isEmpty()) {
                item { Text("No hay modelos que coincidan con este filtro.") }
            }
            items(onlineModels, key = { "${it.providerId}/${it.modelId}" }, contentType = { "online-model" }) { model ->
                OnlineModelRow(
                    model = model,
                    providerName = providerNames[model.providerId] ?: model.providerId,
                    selected = aiSettings.globalSelection.providerId == model.providerId && aiSettings.globalSelection.modelId == model.modelId,
                    favorite = "${model.providerId}/${model.modelId}" in aiSettings.favoriteModels,
                    onSelect = {
                        if (model.pricingType == PricingType.PAID) paidModelToConfirm = model
                        else viewModel.setGlobalAiModel(model)
                    },
                    onFavorite = { viewModel.toggleFavoriteAiModel(model) },
                )
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }
    paidModelToConfirm?.let { model ->
        AlertDialog(
            onDismissRequest = { paidModelToConfirm = null },
            title = { Text("Modelo de pago") },
            text = { Text("${model.displayName} puede generar cargos según la cuenta del proveedor. La información de precio está sujeta a cambios.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setGlobalAiModel(model)
                    paidModelToConfirm = null
                }) { Text("Usar modelo") }
            },
            dismissButton = { TextButton(onClick = { paidModelToConfirm = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun OnlineModelRow(
    model: LlmModelInfo,
    providerName: String,
    selected: Boolean,
    favorite: Boolean,
    onSelect: () -> Unit,
    onFavorite: () -> Unit,
) {
    Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("$providerName · ${onlinePricingLabel(model.pricingType)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Contexto: ${model.contextLength ?: "desconocido"}", style = MaterialTheme.typography.bodySmall)
                    val prices = listOfNotNull(
                        model.inputPrice?.let { "Entrada $${"%.4f".format(it)} / 1M" },
                        model.outputPrice?.let { "Salida $${"%.4f".format(it)} / 1M" },
                    )
                    Text(
                        prices.joinToString(" · ").ifBlank { "Precio no disponible" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onFavorite) {
                    Icon(Icons.Default.Star, if (favorite) "Quitar favorito" else "Añadir favorito", tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(9.dp))
            FilledTonalButton(onClick = onSelect, enabled = !selected) {
                Text(if (selected) "Predeterminado" else "Usar como predeterminado")
            }
        }
    }
}

private fun onlinePricingLabel(type: PricingType) = when (type) {
    PricingType.LOCAL -> "Local"
    PricingType.FREE -> "Gratis"
    PricingType.FREE_TIER -> "Free tier"
    PricingType.PAID -> "Pago"
    PricingType.UNKNOWN -> "Precio no disponible"
}

private fun onlinePricingRank(type: PricingType) = when (type) {
    PricingType.FREE -> 0
    PricingType.FREE_TIER -> 1
    PricingType.LOCAL -> 2
    PricingType.UNKNOWN -> 3
    PricingType.PAID -> 4
}

@Composable
private fun ModelRow(
    viewModel: AppViewModel,
    model: ModelDescriptor,
    engineState: LlmState,
    preparingModel: Boolean,
    configuredContext: Int,
) {
    var showInfo by rememberSaveable(model.id) { mutableStateOf(false) }
    val compatibility = remember(model, viewModel.device.availableRamBytes) { viewModel.run {
        val ratio = model.sizeBytes.toDouble() / device.availableRamBytes.coerceAtLeast(1)
        if (ratio < .55) CompatibilityLevel.RECOMMENDED else if (ratio < .78) CompatibilityLevel.CAUTION else CompatibilityLevel.HIGH_RISK
    } }
    Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(listOfNotNull(model.architecture, model.quantization, formatBytes(model.sizeBytes)).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    model.contextSize?.let {
                        Text(
                            "Máximo del GGUF: $it · uso al recargar: ${ModelLoadPolicy.effectiveContextSize(configuredContext, it)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (model.uri.startsWith("file:")) Text("Copia local optimizada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (model.isActive) Icon(Icons.Default.CheckCircle, "Activo", tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when (compatibility) { CompatibilityLevel.RECOMMENDED -> "✓ Recomendado"; CompatibilityLevel.CAUTION -> "⚠ Puede consumir mucha memoria"; CompatibilityLevel.HIGH_RISK -> "⚠ Riesgo de memoria insuficiente" },
                style = MaterialTheme.typography.labelMedium,
                color = if (compatibility == CompatibilityLevel.RECOMMENDED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { viewModel.loadModel(model) }, enabled = !preparingModel && engineState !is LlmState.LoadingModel) { Text(if (model.isActive) "Recargar" else "Cargar") }
                if (model.isActive) {
                    OutlinedButton(onClick = viewModel::unloadModel, enabled = engineState !is LlmState.Generating) {
                        Icon(Icons.Default.Close, null)
                        Text("Descargar RAM")
                    }
                }
                IconButton(onClick = { showInfo = true }) { Icon(Icons.Default.Info, "Información del modelo") }
                IconButton(onClick = { viewModel.deleteModel(model) }) { Icon(Icons.Default.Delete, "Quitar modelo") }
            }
        }
    }
    if (showInfo) {
        ModelInfoDialog(model, onDismiss = { showInfo = false }) { mode, custom ->
            viewModel.setModelChatTemplate(model, mode, custom)
            showInfo = false
        }
    }
}

@Composable
private fun ModelInfoDialog(
    model: ModelDescriptor,
    onDismiss: () -> Unit,
    onSaveTemplate: (String, String?) -> Unit,
) {
    var mode by remember(model.id) { mutableStateOf(model.chatTemplateMode) }
    var custom by remember(model.id) { mutableStateOf(model.customChatTemplate.orEmpty()) }
    val modes = listOf("AUTO", "CHAT_ML", "LLAMA_3", "GEMMA", "QWEN", "RAW", "CUSTOM")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(model.displayName) },
        text = {
            Column {
                Text("Arquitectura: ${model.architecture ?: "no declarada"}")
                Text("Cuantización: ${model.quantization ?: "no declarada"}")
                Text("Tamaño: ${formatBytes(model.sizeBytes)}")
                Text("Contexto declarado: ${model.contextSize ?: "no declarado"}")
                Text("Tensores: ${model.tensorCount.takeIf { it > 0 } ?: "no declarado"}")
                Text("Parámetros: ${model.parameterCount?.let(::formatParameters) ?: "no calculados"}")
                Text("Tokenizer: ${model.tokenizer ?: "no declarado"}")
                Text("Plantilla incluida: ${if (model.embeddedChatTemplate.isNullOrBlank()) "no detectada" else "sí (${model.embeddedChatTemplate.length} caracteres)"}")
                Spacer(Modifier.height(14.dp))
                Text("Plantilla de chat", style = MaterialTheme.typography.titleSmall)
                Text("AUTO usa primero la plantilla embebida del GGUF.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modes.forEach { value ->
                        FilterChip(selected = mode == value, onClick = { mode = value }, label = { Text(value) })
                    }
                }
                if (mode == "CUSTOM") {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        label = { Text("Plantilla con {{prompt}}") },
                        minLines = 3,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSaveTemplate(mode, custom) },
                enabled = mode != "CUSTOM" || custom.contains("{{prompt}}"),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

private fun formatParameters(value: Long): String = when {
    value >= 1_000_000_000 -> "%.2f B".format(value / 1_000_000_000.0)
    value >= 1_000_000 -> "%.1f M".format(value / 1_000_000.0)
    else -> value.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onProfile: () -> Unit,
    onVoices: () -> Unit,
    onRepositories: () -> Unit,
    onAiProviders: () -> Unit,
) {
    val theme by viewModel.themeMode.collectAsState()
    val catalogLanguage by viewModel.catalogLanguage.collectAsState()
    val generation by viewModel.generationSettings.collectAsState()
    val memory by viewModel.memorySettings.collectAsState()
    val contentMode by viewModel.contentMode.collectAsState()
    val adultConfirmed by viewModel.adultContentConfirmed.collectAsState()
    var advanced by rememberSaveable { mutableStateOf(false) }
    var showAdultConfirmation by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { MainScreenTopBar("Ajustes") }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { SectionHeader("Perfil") }
            item {
                Card(onClick = onProfile, shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Mi perfil", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Nombre, avatar y una persona breve que los personajes conocen mediante {{user}}.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Abrir", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { SectionHeader("Apariencia") }
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Tema", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode -> FilterChip(selected = theme == mode, onClick = { viewModel.setTheme(mode) }, label = { Text(when (mode) { ThemeMode.SYSTEM -> "Sistema"; ThemeMode.LIGHT -> "Claro"; ThemeMode.DARK -> "Oscuro" }) }) }
                        }
                    }
                }
            }
            item { SectionHeader("Idioma del contenido") }
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Personajes y respuestas", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "El repositorio se filtra automáticamente por el idioma elegido y el modelo responderá en ese idioma.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CatalogLanguage.entries.forEach { language ->
                                FilterChip(
                                    selected = catalogLanguage == language,
                                    onClick = { viewModel.setCatalogLanguage(language) },
                                    label = { Text(language.displayName) },
                                )
                            }
                        }
                        if (catalogLanguage == CatalogLanguage.SPANISH) {
                            Text(
                                "Explorar mostrará personajes en español.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            item { SectionHeader("Inteligencia artificial") }
            item {
                Card(onClick = onAiProviders, shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Proveedores de IA", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Configura Local GGUF, Groq, OpenRouter, Gemini, OpenAI, Anthropic, Mistral o un servidor compatible.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Abrir", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            item { SectionHeader("Voces") }
            item {
                Card(onClick = onVoices, shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Voces y TTS", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Voces instaladas, reproducción automática, motor local y repositorios.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Abrir", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { SectionHeader("Privacidad") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f))) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Memoria local y proveedor visible", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(7.dp))
                        Text("La memoria, el historial y los personajes permanecen en este dispositivo. Con GGUF nada sale del teléfono; al elegir un proveedor online, se envían el mensaje y solo el contexto necesario a ese proveedor.")
                    }
                }
            }
            item { SectionHeader("Repositorios de personajes") }
            item {
                Card(onClick = onRepositories, shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Administrar fuentes", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Activa catálogos o añade cualquier repository.json seguro por HTTPS.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Abrir", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            item { SectionHeader("Memoria local") }
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        SettingSwitch(
                            "Memoria inteligente",
                            "Extrae y recupera recuerdos útiles con el modelo local después de responder.",
                            memory.intelligentMemory,
                        ) { viewModel.setMemorySettings(memory.copy(intelligentMemory = it)) }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        SettingSwitch(
                            "Seguimientos automáticos",
                            "Permite preguntar de forma natural por eventos pendientes, con cooldown.",
                            memory.automaticFollowUps,
                        ) { viewModel.setMemorySettings(memory.copy(automaticFollowUps = it)) }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        SettingSwitch(
                            "Resumen de conversaciones",
                            "Resume bloques antiguos sin borrar los mensajes originales.",
                            memory.conversationSummaries,
                        ) { viewModel.setMemorySettings(memory.copy(conversationSummaries = it)) }
                        Spacer(Modifier.height(12.dp))
                        Text("Nivel de memoria", style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MemoryLevel.entries.forEach { level ->
                                FilterChip(
                                    selected = memory.level == level,
                                    onClick = { viewModel.setMemorySettings(memory.copy(level = level)) },
                                    label = { Text(when (level) { MemoryLevel.MINIMAL -> "Mínimo"; MemoryLevel.NORMAL -> "Normal"; MemoryLevel.DETAILED -> "Detallado" }) },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        SettingSwitch(
                            "Compartir memoria entre chats",
                            "Usa la misma memoria para este personaje y persona en conversaciones distintas. Desactivado por defecto.",
                            memory.shareAcrossChats,
                        ) { viewModel.setMemorySettings(memory.copy(shareAcrossChats = it)) }
                        Text(
                            "Estos recuerdos permanecen únicamente en tu dispositivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item { SectionHeader("Conversaciones") }
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        SettingSwitch(
                            "Contenido adulto",
                            "Permite contenido adulto dentro de las preferencias de la app. Los proveedores externos mantienen sus propias políticas.",
                            contentMode == ContentMode.ADULT_ENABLED,
                        ) { enabled ->
                            if (!enabled) viewModel.setContentMode(ContentMode.STANDARD)
                            else if (adultConfirmed) viewModel.setContentMode(ContentMode.ADULT_ENABLED)
                            else showAdultConfirmation = true
                        }
                    }
                }
            }
            item { SectionHeader("Motor") }
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("llama.cpp", style = MaterialTheme.typography.titleMedium)
                        Text(viewModel.nativeVersion(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("CPU · arm64-v8a / x86_64", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { SectionHeader("Generación") }
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Presets", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { viewModel.setGenerationSettings(GenerationSettings.Creative) }, label = { Text("Creativo") })
                            AssistChip(onClick = { viewModel.setGenerationSettings(GenerationSettings.Balanced) }, label = { Text("Equilibrado") })
                            AssistChip(onClick = { viewModel.setGenerationSettings(GenerationSettings.Precise) }, label = { Text("Preciso") })
                            AssistChip(onClick = { viewModel.setGenerationSettings(GenerationSettings.Roleplay) }, label = { Text("Roleplay") })
                        }
                        Spacer(Modifier.height(12.dp))
                        SettingSlider("Temperature", generation.temperature, 0f..1.5f, { "%.2f".format(it) }) { viewModel.setGenerationSettings(generation.copy(temperature = it)) }
                        SettingSlider("Max tokens", generation.maxTokens.toFloat(), 64f..1024f, { it.toInt().toString() }) { viewModel.setGenerationSettings(generation.copy(maxTokens = it.toInt())) }
                        SettingSlider("Context size", generation.contextSize.toFloat(), 512f..8192f, { it.toInt().toString() }) { viewModel.setGenerationSettings(generation.copy(contextSize = it.toInt())) }
                        FilterChip(selected = advanced, onClick = { advanced = !advanced }, label = { Text("Avanzado") })
                        if (advanced) {
                            Text(
                                "La app limita CPU y lotes para que Android siga respondiendo durante la inferencia.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SettingSlider("Top P", generation.topP, .1f..1f, { "%.2f".format(it) }) { viewModel.setGenerationSettings(generation.copy(topP = it)) }
                            SettingSlider("Top K", generation.topK.toFloat(), 1f..100f, { it.toInt().toString() }) { viewModel.setGenerationSettings(generation.copy(topK = it.toInt())) }
                            SettingSlider("Min P", generation.minP, 0f..0.5f, { "%.2f".format(it) }) { viewModel.setGenerationSettings(generation.copy(minP = it)) }
                            SettingSlider("Repeat penalty", generation.repeatPenalty, .8f..1.4f, { "%.2f".format(it) }) { viewModel.setGenerationSettings(generation.copy(repeatPenalty = it)) }
                            SettingSlider("Threads", generation.threads.toFloat(), 1f..viewModel.device.cpuCores.coerceIn(2, 4).toFloat(), { it.toInt().toString() }) { viewModel.setGenerationSettings(generation.copy(threads = it.toInt())) }
                            SettingSlider("Batch size", generation.batchSize.toFloat(), 32f..512f, { it.toInt().toString() }) { viewModel.setGenerationSettings(generation.copy(batchSize = it.toInt())) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    if (showAdultConfirmation) {
        AlertDialog(
            onDismissRequest = { showAdultConfirmation = false },
            title = { Text("Confirmación para adultos") },
            text = {
                Text(
                    "Confirma que eres una persona adulta y deseas habilitar esta preferencia. Esto no evita ni modifica las reglas de los proveedores de IA.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setContentMode(ContentMode.ADULT_ENABLED, confirmed = true)
                    showAdultConfirmation = false
                }) { Text("Soy adulto y confirmar") }
            },
            dismissButton = { TextButton(onClick = { showAdultConfirmation = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) { Text(label, modifier = Modifier.weight(1f)); Text(format(value), color = MaterialTheme.colorScheme.primary) }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SettingSwitch(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "Tamaño desconocido"
    val gb = bytes / 1_073_741_824.0
    return if (gb >= 1) "%.2f GB".format(gb) else "%.0f MB".format(bytes / 1_048_576.0)
}
