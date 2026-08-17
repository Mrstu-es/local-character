package com.localcharacter.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.localcharacter.app.data.settings.ThemeMode
import com.localcharacter.app.ui.theme.LocalCharacterTheme

@Composable
fun CharacterChatTopBar(
    characterName: String,
    avatarUri: String?,
    generating: Boolean,
    canRegenerate: Boolean,
    providerLabel: String,
    processingLocally: Boolean,
    onBack: () -> Unit,
    onCharacterClick: () -> Unit,
    onMemory: () -> Unit,
    onRegenerate: () -> Unit,
    onProviderClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val metrics = chatTopBarMetrics(maxWidth)
            var menuExpanded by rememberSaveable { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = metrics.minHeight)
                    .padding(horizontal = metrics.horizontalPadding, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(ChatTopBarTokens.TouchTarget),
                ) {
                    Icon(Icons.Default.ArrowBack, "Volver")
                }
                Row(
                    modifier = Modifier.clickable(onClick = onCharacterClick),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CharacterAvatar(characterName.ifBlank { "?" }, Modifier.size(metrics.avatarSize), avatarUri)
                    Spacer(Modifier.width(metrics.avatarGap))
                    Column(Modifier.widthIn(max = metrics.titleMaxWidth)) {
                        Text(
                            characterName.ifBlank { "Chat" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (metrics.showSubtitle) {
                            Row(
                                modifier = Modifier.clickable(onClick = onProviderClick),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (processingLocally) Icons.Default.Lock else Icons.Default.Send,
                                    null,
                                    Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    providerLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(metrics.actionGap))
                if (metrics.showInlineActions) {
                    ChatTopBarAction(Icons.Default.Star, "Memoria", onClick = onMemory)
                    ChatTopBarAction(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Regenerar",
                        enabled = !generating && canRegenerate,
                        onClick = onRegenerate,
                    )
                } else {
                    Box {
                        ChatTopBarAction(Icons.Default.MoreVert, "Más acciones", onClick = { menuExpanded = true })
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Memoria") },
                                leadingIcon = { Icon(Icons.Default.Star, null) },
                                onClick = {
                                    menuExpanded = false
                                    onMemory()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Regenerar") },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                enabled = !generating && canRegenerate,
                                onClick = {
                                    menuExpanded = false
                                    onRegenerate()
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

internal data class ChatTopBarMetrics(
    val avatarSize: Dp,
    val avatarGap: Dp,
    val actionGap: Dp,
    val horizontalPadding: Dp,
    val minHeight: Dp,
    val titleMaxWidth: Dp,
    val showSubtitle: Boolean,
    val showInlineActions: Boolean,
)

internal fun chatTopBarMetrics(width: Dp): ChatTopBarMetrics {
    val veryNarrow = width < 340.dp
    val inlineActions = width >= 360.dp
    val avatarSize = when {
        width < 300.dp -> 36.dp
        width < 600.dp -> 40.dp
        else -> 48.dp
    }
    val avatarGap = if (width < 300.dp) 6.dp else 8.dp
    val horizontalPadding = if (width < 340.dp) 0.dp else 4.dp
    val actionGap = if (inlineActions) 6.dp else 4.dp
    val actionWidth = if (inlineActions) ChatTopBarTokens.TouchTarget + ChatTopBarTokens.TouchTarget else ChatTopBarTokens.TouchTarget
    val reservedWidth =
        horizontalPadding + horizontalPadding +
            ChatTopBarTokens.TouchTarget +
            avatarSize +
            avatarGap +
            actionGap +
            actionWidth
    val titleMaxWidth = (width - reservedWidth).coerceAtLeast(ChatTopBarTokens.MinTitleWidth)
    return ChatTopBarMetrics(
        avatarSize = avatarSize,
        avatarGap = avatarGap,
        actionGap = actionGap,
        horizontalPadding = horizontalPadding,
        minHeight = if (width >= 600.dp) 64.dp else 56.dp,
        titleMaxWidth = titleMaxWidth,
        showSubtitle = !veryNarrow && titleMaxWidth >= 108.dp,
        showInlineActions = inlineActions,
    )
}

private object ChatTopBarTokens {
    val TouchTarget = 48.dp
    val MinTitleWidth = 72.dp
}

@Composable
private fun ChatTopBarAction(
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(ChatTopBarTokens.TouchTarget)
            .alpha(if (enabled) 1f else 0.38f)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
                if (!enabled) disabled()
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Icon(imageVector, null, Modifier.size(24.dp))
    }
}

@Preview(name = "Top bar small", widthDp = 320, heightDp = 96)
@Composable
private fun CharacterChatTopBarSmallPreview() {
    LocalCharacterTheme(ThemeMode.DARK) {
        CharacterChatTopBar(
            characterName = "Este es un personaje con un nombre extremadamente largo",
            avatarUri = null,
            generating = false,
            canRegenerate = true,
            providerLabel = "openrouter/provider-super-model-version-very-long-name",
            processingLocally = false,
            onBack = {},
            onCharacterClick = {},
            onMemory = {},
            onRegenerate = {},
            onProviderClick = {},
        )
    }
}

@Preview(name = "Top bar normal", widthDp = 393, heightDp = 96)
@Composable
private fun CharacterChatTopBarNormalPreview() {
    LocalCharacterTheme(ThemeMode.DARK) {
        CharacterChatTopBar(
            characterName = "Sophie: Necesitamos Hablar",
            avatarUri = null,
            generating = false,
            canRegenerate = true,
            providerLabel = "Groq · ALLaM-2-7b",
            processingLocally = false,
            onBack = {},
            onCharacterClick = {},
            onMemory = {},
            onRegenerate = {},
            onProviderClick = {},
        )
    }
}

@Preview(name = "Top bar tablet", widthDp = 840, heightDp = 112, fontScale = 1.3f)
@Composable
private fun CharacterChatTopBarTabletPreview() {
    LocalCharacterTheme(ThemeMode.DARK) {
        CharacterChatTopBar(
            characterName = "Sophie: Necesitamos Hablar",
            avatarUri = null,
            generating = false,
            canRegenerate = true,
            providerLabel = "Groq · openrouter/provider-super-model-version-very-long-name",
            processingLocally = false,
            onBack = {},
            onCharacterClick = {},
            onMemory = {},
            onRegenerate = {},
            onProviderClick = {},
        )
    }
}
