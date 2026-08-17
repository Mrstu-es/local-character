package com.localcharacter.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localcharacter.app.domain.model.ChatMessage
import com.localcharacter.app.llm.provider.PricingType
import com.localcharacter.app.llm.provider.ProviderModelSelection
import com.localcharacter.app.ui.chat.ChatViewModel
import com.localcharacter.app.ui.chat.ChatListPolicy
import com.localcharacter.app.ui.components.CharacterChatTopBar
import com.localcharacter.app.ui.components.ChatBubble
import com.localcharacter.app.ui.components.MessageComposer
import com.localcharacter.app.ui.components.MessageActionsBottomSheet
import com.localcharacter.app.ui.components.MessageActionTarget
import com.localcharacter.app.domain.conversation.ComposerMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.localcharacter.app.tts.TtsPlaybackState
import com.localcharacter.app.performance.NavigationPerformance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onMemory: () -> Unit,
    onCharacter: (String) -> Unit,
    onBranchCreated: (String) -> Unit = {},
) {
    val header by viewModel.header.collectAsStateWithLifecycle()
    val canRegenerate by viewModel.canRegenerate.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val brain by viewModel.brain.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2200)
            viewModel.dismissNotice()
        }
    }
    val listState = rememberLazyListState()
    var showBrainPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CharacterChatTopBar(
                characterName = header.character?.name.orEmpty(),
                avatarUri = header.character?.avatarUri,
                generating = generating,
                canRegenerate = canRegenerate,
                providerLabel = brain.label,
                processingLocally = brain.isLocal,
                onBack = onBack,
                onCharacterClick = { header.character?.id?.let(onCharacter) },
                onMemory = onMemory,
                onRegenerate = viewModel::regenerate,
                onProviderClick = { if (!generating) showBrainPicker = true },
            )
        },
        bottomBar = {
            ChatComposer(viewModel, generating)
        },
        snackbarHost = {
            AnimatedVisibility(error != null || notice != null) {
                Snackbar(
                    modifier = Modifier.padding(12.dp),
                    action = if (error != null) ({ TextButton(onClick = { showBrainPicker = true }) { Text("Cambiar IA") } }) else null,
                ) { Text(error ?: notice.orEmpty()) }
            }
        },
    ) { padding ->
        ChatConversation(
            viewModel = viewModel,
            characterName = header.character?.name ?: "Personaje",
            characterAvatarUri = header.character?.avatarUri,
            processingLocally = brain.isLocal,
            modifier = Modifier.fillMaxSize().padding(padding),
            listState = listState,
            onBranchCreated = onBranchCreated,
            onMemory = onMemory,
        )
    }
    if (showBrainPicker) {
        ModalBottomSheet(onDismissRequest = { showBrainPicker = false }) {
            Text(
                "Motor de IA",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            )
            TextButton(
                onClick = { viewModel.selectBrain(null); showBrainPicker = false },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Usar configuración global o del personaje") }
            HorizontalDivider()
            if (brain.options.isEmpty()) {
                Text("Configura un proveedor y actualiza sus modelos desde Ajustes.", modifier = Modifier.padding(22.dp))
            } else {
                LazyColumn {
                    items(brain.options, key = { "${it.providerId}/${it.modelId}" }) { model ->
                        ListItem(
                            headlineContent = { Text(model.displayName) },
                            supportingContent = { Text("${model.providerId} · ${pricingLabel(model.pricingType)}") },
                            trailingContent = {
                                if (brain.selection.providerId == model.providerId &&
                                    (brain.selection.modelId == model.modelId || brain.isLocal && model.providerId == "local")) {
                                    Text("Activo", color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.selectBrain(ProviderModelSelection(model.providerId, model.modelId))
                                showBrainPicker = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatComposer(viewModel: ChatViewModel, generating: Boolean) {
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var mode by rememberSaveable { mutableStateOf(ComposerMode.NORMAL) }
    MessageComposer(
        value = input,
        onValueChange = { input = it },
        generating = generating,
        mode = mode,
        onToggleAction = {
            val enabling = mode != ComposerMode.ACTION
            input = input.withActionMarks(enabling)
            mode = if (enabling) ComposerMode.ACTION else ComposerMode.NORMAL
        },
        onSend = { val sent = input.text; input = TextFieldValue(""); viewModel.send(sent, mode) },
        onNext = viewModel::continueAsCharacter,
        onStop = viewModel::stop,
    )
}

private fun TextFieldValue.withActionMarks(enabling: Boolean): TextFieldValue {
    if (!enabling) return withoutWholeActionMarks()
    if (text.isBlank()) return TextFieldValue("**", selection = TextRange(2))
    if (isWholeAction()) return this
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    return if (start != end) {
        val marked = text.replaceRange(start, end, "**${text.substring(start, end)}**")
        TextFieldValue(marked, selection = TextRange(end + 4))
    } else {
        TextFieldValue("**$text**", selection = TextRange(text.length + 4))
    }
}

private fun TextFieldValue.withoutWholeActionMarks(): TextFieldValue {
    if (text == "**") return TextFieldValue("")
    if (!isWholeAction()) return this
    val unwrapped = text.substring(2, text.length - 2)
    return TextFieldValue(unwrapped, selection = TextRange(unwrapped.length))
}

private fun TextFieldValue.isWholeAction(): Boolean =
    text.length >= 4 && text.startsWith("**") && text.endsWith("**")

/** Streaming state is collected here so token updates do not recompose the top bar or composer. */
@Composable
private fun ChatConversation(
    viewModel: ChatViewModel,
    characterName: String,
    characterAvatarUri: String?,
    processingLocally: Boolean,
    listState: LazyListState,
    onBranchCreated: (String) -> Unit,
    onMemory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val pinnedMemories by viewModel.pinnedMemories.collectAsStateWithLifecycle()
    val streamingMessage by viewModel.streamingMessage.collectAsStateWithLifecycle()
    val hasOlderMessages by viewModel.hasOlderMessages.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val pinnedMessageIds = remember(pinnedMemories) {
        pinnedMemories.mapNotNullTo(mutableSetOf()) { it.sourceMessageId }
    }
    LaunchedEffect(messages.size) { NavigationPerformance.dataReady("chat", messages.size) }
    var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val clipboard = LocalClipboardManager.current
    MessageList(
        messages = messages,
        streamingMessage = streamingMessage,
        characterName = characterName,
        characterAvatarUri = characterAvatarUri,
        processingLocally = processingLocally,
        hasOlderMessages = hasOlderMessages,
        listState = listState,
        onLoadOlder = viewModel::loadOlderMessages,
        onDelete = viewModel::deleteMessage,
        onSpeak = viewModel::speakMessage,
        onStopAudio = viewModel::stopAudio,
        ttsState = ttsState,
        modifier = modifier,
        onLongPress = { selectedMessage = it },
        pinnedMessageIds = pinnedMessageIds,
        onOpenMemory = onMemory,
    )
    selectedMessage?.let { message ->
        MessageActionsBottomSheet(
            target = MessageActionTarget(message.id, message.content, characterName, message.role == com.localcharacter.app.domain.model.MessageRole.CHARACTER),
            canSpeak = message.role == com.localcharacter.app.domain.model.MessageRole.CHARACTER && message.content.isNotBlank(),
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
private fun MessageList(
    messages: List<ChatMessage>,
    streamingMessage: ChatMessage?,
    characterName: String,
    characterAvatarUri: String?,
    processingLocally: Boolean,
    hasOlderMessages: Boolean,
    listState: LazyListState,
    onLoadOlder: () -> Unit,
    onDelete: (String) -> Unit,
    onSpeak: (ChatMessage) -> Unit,
    onStopAudio: () -> Unit,
    ttsState: TtsPlaybackState,
    onLongPress: (ChatMessage) -> Unit,
    pinnedMessageIds: Set<String>,
    onOpenMemory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleStreamingMessage = streamingMessage?.takeIf {
        ChatListPolicy.shouldRenderStreaming(messages.asSequence().map(ChatMessage::id).asIterable(), it.id)
    }
    val isNearBottom by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            ChatListPolicy.isNearBottom(lastVisible, info.totalItemsCount)
        }
    }
    var followNewContent by remember { mutableStateOf(true) }
    val listScope = rememberCoroutineScope()

    LaunchedEffect(listState) {
        snapshotFlow { isNearBottom to listState.isScrollInProgress }.collect { (nearBottom, scrolling) ->
            if (nearBottom) followNewContent = true
            else if (scrolling) followNewContent = false
        }
    }
    LaunchedEffect(messages.size, visibleStreamingMessage?.content?.length, followNewContent) {
        if (!followNewContent) return@LaunchedEffect
        withFrameNanos { }
        ChatListPolicy.autoScrollTargetIndex(listState.layoutInfo.totalItemsCount)?.let { lastIndex ->
            listState.scrollToItem(lastIndex)
        }
    }

    Box(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            item(key = "privacy", contentType = "notice") {
                Text(
                    if (processingLocally) {
                        "🔒 Procesamiento local. El contenido se procesa en este dispositivo."
                    } else {
                        "☁ Procesamiento online. Los mensajes y el contexto necesario se envían al proveedor seleccionado."
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 18.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasOlderMessages) {
                item(key = "older", contentType = "action") {
                    TextButton(onClick = onLoadOlder, modifier = Modifier.fillMaxWidth()) {
                        Text("Cargar mensajes anteriores")
                    }
                }
            }
            items(messages, key = { it.id }, contentType = { it.role }) { message ->
                val activeAudio = when (ttsState) {
                    is TtsPlaybackState.Synthesizing -> ttsState.messageId == message.id
                    is TtsPlaybackState.Playing -> ttsState.messageId == message.id
                    else -> false
                }
                ChatBubble(
                    message = message,
                    characterName = characterName,
                    characterAvatarUri = characterAvatarUri,
                    audioActive = activeAudio,
                    onAudio = { if (activeAudio) onStopAudio() else onSpeak(message) },
                    onDelete = { onDelete(message.id) },
                    onLongPress = { onLongPress(message) },
                    isPinned = message.id in pinnedMessageIds,
                    onOpenMemory = onOpenMemory,
                )
            }
            visibleStreamingMessage?.let { streaming ->
                item(key = streaming.id, contentType = streaming.role) {
                    ChatBubble(streaming, characterName, characterAvatarUri, onDelete = {}, onLongPress = {})
                }
            }
        }
        AnimatedVisibility(
            visible = !isNearBottom,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    followNewContent = true
                    val lastIndex = listState.layoutInfo.totalItemsCount - 1
                    if (lastIndex >= 0) listScope.launch { listState.animateScrollToItem(lastIndex) }
                },
            ) { Icon(Icons.Default.KeyboardArrowDown, "Ir al final") }
        }
    }
}

private fun pricingLabel(type: PricingType): String = when (type) {
    PricingType.LOCAL -> "Local"
    PricingType.FREE -> "Gratis"
    PricingType.FREE_TIER -> "Free tier"
    PricingType.PAID -> "Pago"
    PricingType.UNKNOWN -> "Precio no disponible"
}
