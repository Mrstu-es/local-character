package com.localcharacter.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.localcharacter.app.domain.conversation.ComposerMode
import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.ChatMessage
import com.localcharacter.app.domain.model.MessageRole
import com.localcharacter.app.ui.motion.AppMotion
import com.localcharacter.app.ui.theme.LocalCharacterColors

@Composable
fun CharacterAvatar(name: String, modifier: Modifier = Modifier, avatarUri: String? = null, selected: Boolean = false) {
    val resolved = remember(name, avatarUri) { CharacterAvatarResolver.resolve(name, avatarUri) }
    val context = LocalContext.current
    val imageRequest = remember(context, resolved.imageData) {
        resolved.imageData?.let { data ->
            ImageRequest.Builder(context)
                .data(data)
                .size(256, 256)
                .crossfade(AppMotion.FastMillis)
                .build()
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = AppMotion.pressSpring(),
        label = "avatarScale",
    )
    val colors = listOf(LocalCharacterColors.Lavender, Color(0xFF7AB8BD))
    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors))
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (resolved.imageData == null) {
            AvatarFallback(resolved.fallbackInitial)
        } else {
            AvatarFallback(resolved.fallbackInitial)
            AsyncImage(
                model = imageRequest,
                contentDescription = "Avatar de $name",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun AvatarFallback(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            name.take(1).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF241C3D),
        )
    }
}

@Composable
fun CharacterCard(character: Character, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = AppMotion.pressSpring(),
        label = "characterCardPress",
    )
    Card(
        onClick = onClick,
        modifier = modifier.widthIn(min = 180.dp, max = 224.dp).scale(scale),
        interactionSource = interactionSource,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            CharacterAvatar(character.name, Modifier.size(62.dp), character.avatarUri)
            Spacer(Modifier.height(16.dp))
            Text(character.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(
                character.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                character.tags.take(2).forEach { tag ->
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)) {
                        Text(
                            tag,
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onAction).padding(8.dp),
            )
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(Modifier.size(72.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Info, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            AssistChip(
                onClick = onAction,
                label = { Text(action) },
                colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    characterName: String,
    characterAvatarUri: String? = null,
    audioActive: Boolean = false,
    onAudio: () -> Unit = {},
    onDelete: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    isPinned: Boolean = false,
    onOpenMemory: () -> Unit = {},
) {
    val user = message.role == MessageRole.USER
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val contentWidth = maxWidth.coerceAtMost(ChatLayoutTokens.MaxContentWidth)
        val sidePadding = when {
            maxWidth < 360.dp -> 8.dp
            maxWidth < 600.dp -> 12.dp
            else -> 16.dp
        }
        val avatarSize = when {
            maxWidth < 360.dp -> 28.dp
            maxWidth < 600.dp -> 30.dp
            else -> 32.dp
        }
        val avatarGap = if (maxWidth < 360.dp) 6.dp else 8.dp
        val reservedForAvatar = if (user) 0.dp else avatarSize + avatarGap
        val availableBubbleWidth = (contentWidth - sidePadding - sidePadding - reservedForAvatar)
            .coerceAtLeast(ChatLayoutTokens.MinBubbleWidth)
        val bubbleFraction = when {
            maxWidth < 360.dp -> 0.94f
            maxWidth < 600.dp -> 0.90f
            maxWidth < 840.dp -> 0.86f
            else -> 0.72f
        }
        val bubbleMaxWidth = (availableBubbleWidth * bubbleFraction)
            .coerceAtMost(ChatLayoutTokens.MaxBubbleWidth)
            .coerceAtLeast(ChatLayoutTokens.MinBubbleWidth)

        Box(Modifier.width(contentWidth).align(Alignment.Center)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding, vertical = 6.dp),
                horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom,
            ) {
                if (!user) {
                    CharacterAvatar(characterName, Modifier.size(avatarSize), characterAvatarUri)
                    Spacer(Modifier.width(avatarGap))
                }
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (user) 20.dp else 6.dp,
                        bottomEnd = if (user) 6.dp else 20.dp,
                    ),
                    color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.widthIn(max = bubbleMaxWidth).combinedClickable(onClick = {}, onLongClick = (onLongPress ?: onDelete)),
                ) {
                    Column(Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
                        if (message.content.isBlank() && !message.isComplete) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("$characterName está escribiendo…", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            Text(message.content, style = MaterialTheme.typography.bodyLarge)
                        }
                        if (isPinned && message.content.isNotBlank()) {
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
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Abrir memoria",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (!user && message.isComplete && message.content.isNotBlank()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = onAudio, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        if (audioActive) Icons.Default.Close else Icons.Default.PlayArrow,
                                        if (audioActive) "Detener audio" else "Reproducir mensaje",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageComposer(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    generating: Boolean,
    mode: ComposerMode,
    onToggleAction: () -> Unit,
    onSend: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
) {
    val actionContainerColor by animateColorAsState(
        targetValue = if (mode == ComposerMode.ACTION) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = AppMotion.fastTween(),
        label = "actionModeContainer",
    )
    val actionTextColor by animateColorAsState(
        targetValue = if (mode == ComposerMode.ACTION) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = AppMotion.fastTween(),
        label = "actionModeText",
    )
    Surface(modifier = Modifier.fillMaxWidth().imePadding(), tonalElevation = 3.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val contentWidth = maxWidth.coerceAtMost(ChatLayoutTokens.MaxContentWidth)
            val sidePadding = if (maxWidth < 360.dp) 8.dp else 12.dp
            val nextSize = if (maxWidth < 360.dp) 46.dp else 48.dp
            val maxInputLines = if (maxWidth < 360.dp) 4 else 5
            Row(
                Modifier.width(contentWidth).align(Alignment.Center).padding(horizontal = sidePadding, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (mode == ComposerMode.ACTION) "Acción…" else "Escribe un mensaje…") },
                    maxLines = maxInputLines,
                    shape = RoundedCornerShape(24.dp),
                    trailingIcon = {
                        Surface(
                            onClick = onToggleAction,
                            enabled = !generating,
                            shape = CircleShape,
                            color = actionContainerColor,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "**",
                                    color = actionTextColor,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    },
                )
                Spacer(Modifier.width(6.dp))
                if (generating) {
                    IconButton(onClick = onStop, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, "Detener generación")
                    }
                } else {
                    if (value.text.isNotBlank()) {
                        IconButton(onClick = onSend, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Enviar")
                        }
                    }
                    Surface(onClick = onNext, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(nextSize).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next: continuar como personaje")
                        }
                    }
                }
            }
        }
    }
}

private object ChatLayoutTokens {
    val MinBubbleWidth = 96.dp
    val MaxBubbleWidth = 720.dp
    val MaxContentWidth = 840.dp
}

@Composable
fun LoadingIndicator(show: Boolean, label: String) {
    AnimatedVisibility(show) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
