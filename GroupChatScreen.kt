package com.localcharacter.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Snackbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localcharacter.app.domain.conversation.ComposerMode
import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.GroupMessage
import com.localcharacter.app.domain.model.GroupMessageRole
import com.localcharacter.app.domain.model.GroupMemory
import com.localcharacter.app.ui.components.CharacterAvatar
import com.localcharacter.app.ui.components.DetailTopBar
import com.localcharacter.app.ui.components.MessageComposer
import com.localcharacter.app.ui.components.MessageActionsBottomSheet
import com.localcharacter.app.ui.components.MessageActionTarget
import com.localcharacter.app.ui.group.GroupChatViewModel
import com.localcharacter.app.performance.NavigationPerformance

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun GroupChatScreen(viewModel: GroupChatViewModel, onBack: () -> Unit, onMemory: () -> Unit, onSettings: () -> Unit, onBranchCreated: (String) -> Unit = {}) {
    val header by viewModel.header.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streaming by viewModel.streamingMessage.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val selecting by viewModel.selectingSpeaker.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val manualSpeaker by viewModel.manualSpeaker.collectAsStateWithLifecycle()
    val pinnedMemories by viewModel.pinnedMemories.collectAsStateWithLifecycle()
    val pinnedContents = remember(pinnedMemories) { pinnedMemories.mapTo(mutableSetOf()) { it.content } }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var selectedMessage by remember { mutableStateOf<GroupMessage?>(null) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(notice) {
        notice?.let { snackbarHostState.showSnackbar(it); viewModel.dismissNotice() }
    }
    LaunchedEffect(messages.size) { NavigationPerformance.dataReady("group", messages.size) }
    Scaffold(
        topBar = {
            DetailTopBar(
                title = header.group?.name ?: "Grupo",
                onBack = onBack,
                leading = { CharacterAvatar(header.group?.name ?: "Grupo", Modifier.fillMaxSize(), header.group?.avatarPath) },
                actions = {
                    IconButton(onClick = onMemory) { Icon(Icons.Default.Star, "Memoria del grupo") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Detalles del grupo") }
                },
            )
        },
        bottomBar = { GroupComposer(viewModel, generating, header.characters, header.group?.turnMode == com.localcharacter.app.domain.model.GroupTurnMode.MANUAL, manualSpeaker) },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            GroupMessageList(
                messages = messages,
                streaming = streaming,
                characters = header.characters,
                pinnedContents = pinnedContents,
                selecting = selecting,
                error = error,
                onDismissError = viewModel::dismissError,
                modifier = Modifier.fillMaxWidth().weight(1f),
                onLongPress = { selectedMessage = it },
                onOpenMemory = onMemory,
            )
        }
    }
    selectedMessage?.let { message ->
        val sender = header.characters.firstOrNull { it.id == message.senderCharacterId }?.name ?: "Grupo"
        MessageActionsBottomSheet(
            target = MessageActionTarget(message.id, message.content, sender, message.role == GroupMessageRole.CHARACTER),
            canSpeak = message.role == GroupMessageRole.CHARACTER && message.content.isNotBlank(),
            onDismiss = { selectedMessage = null },
            onCopy = { clipboard.setText(AnnotatedString(message.content)); viewModel.notify("Mensaje copiado.") },
            onPlayVoice = { viewModel.speakMessage(message) },
            onBranch = { viewModel.branchFrom(message.id, onBranchCreated) },
            onRewind = { viewModel.rewindTo(message.id) },
            onPinMemory = { viewModel.pinMessageAsMemory(message) },
            onReport = viewModel::reportMessage,
        )
    }
}

@Composable
private fun GroupPinnedMemoryPreview(memory: GroupMemory, onOpen: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 8.dp)) {
            Text(
                memory.content,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "✚ Recordatorio del grupo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Abrir memoria", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GroupComposer(viewModel: GroupChatViewModel, generating: Boolean, characters: List<Character>, manualMode: Boolean, manualSpeaker: String?) {
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var mode by rememberSaveable { mutableStateOf(ComposerMode.NORMAL) }
    Column {
        if (manualMode) {
            androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(characters, key = { it.id }) { character ->
                    FilterChip(selected = manualSpeaker == character.id, onClick = { viewModel.selectManualSpeaker(character.id) }, label = { Text(character.name) })
                }
            }
        }
        val mentionQuery = input.text.substringAfterLast('@', missingDelimiterValue = "").takeIf { '@' in input.text && !it.contains(' ') }
        if (mentionQuery != null) {
            androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(characters.filter { it.name.contains(mentionQuery, true) }, key = { it.id }) { character ->
                    androidx.compose.material3.AssistChip(onClick = { input = TextFieldValue(input.text.substringBeforeLast('@') + "@${character.name} ") }, label = { Text(character.name) })
                }
            }
        }
        MessageComposer(
            value = input,
            onValueChange = { input = it },
            generating = generating,
            mode = mode,
            onToggleAction = {
                val enabling = mode != ComposerMode.ACTION
                input = if (enabling) {
                    if (input.text.isBlank()) TextFieldValue("**", selection = TextRange(2))
                    else TextFieldValue("**${input.text}**", selection = TextRange(input.text.length + 4))
                } else if (input.text == "**") TextFieldValue("") else if (input.text.startsWith("**") && input.text.endsWith("**")) TextFieldValue(input.text.substring(2, input.text.length - 2)) else input
                mode = if (enabling) ComposerMode.ACTION else ComposerMode.NORMAL
            },
            onSend = { val text = input.text; input = TextFieldValue(""); viewModel.send(text, mode) },
            onNext = viewModel::continueGroup,
            onStop = viewModel::stop,
        )
    }
}

@Composable
private fun GroupMessageList(
    messages: List<GroupMessage>,
    streaming: GroupMessage?,
    characters: List<com.localcharacter.app.domain.model.Character>,
    pinnedContents: Set<String>,
    selecting: Boolean,
    error: String?,
    onDismissError: () -> Unit,
    onLongPress: (GroupMessage) -> Unit,
    onOpenMemory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    LaunchedEffect(messages.size, streaming?.content?.length) {
        withFrameNanos { }
        val total = state.layoutInfo.totalItemsCount
        if (total > 0) state.animateScrollToItem(total - 1)
    }
    LazyColumn(
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "privacy") { Text("🔒 El grupo se procesa y guarda en este dispositivo, según el proveedor seleccionado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
        items(messages, key = { it.id }) { message -> GroupMessageItem(message, characters, message.content in pinnedContents, onLongPress, onOpenMemory) }
        streaming?.let { item(key = it.id) { GroupMessageItem(it, characters, it.content in pinnedContents, onLongPress, onOpenMemory) } }
        if (selecting) item { Text("Decidiendo quién responde…", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp)) }
        if (error != null) item { Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer); TextButton(onClick = onDismissError) { Text("Cerrar") } } } }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GroupMessageItem(
    message: GroupMessage,
    characters: List<com.localcharacter.app.domain.model.Character>,
    isPinned: Boolean,
    onLongPress: (GroupMessage) -> Unit,
    onOpenMemory: () -> Unit,
) {
    val character = characters.firstOrNull { it.id == message.senderCharacterId }
    val isUser = message.role == GroupMessageRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!isUser) {
            CharacterAvatar(character?.name ?: "Personaje", Modifier.size(38.dp), character?.avatarUri)
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(if (isUser) .88f else .94f).combinedClickable(onClick = {}, onLongClick = { onLongPress(message) }),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(if (isUser) "Tú" else character?.name ?: "Personaje", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(message.content, style = MaterialTheme.typography.bodyLarge)
                if (isPinned) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = onOpenMemory),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("📌", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "Recordaron esto",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                        )
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Abrir memoria", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
