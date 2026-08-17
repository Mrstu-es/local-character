package com.localcharacter.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class MessageActionTarget(
    val id: String,
    val text: String,
    val senderName: String,
    val isCharacter: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsBottomSheet(
    target: MessageActionTarget,
    canSpeak: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onPlayVoice: () -> Unit,
    onBranch: () -> Unit,
    onRewind: () -> Unit,
    onPinMemory: () -> Unit,
    onReport: () -> Unit,
) {
    var confirmation by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
        ) {
            Text("Acciones del mensaje", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
            Text(target.text.take(160), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            action("Copiar") { onCopy(); onDismiss() }
            if (canSpeak) action("Reproducir voz") { onPlayVoice(); onDismiss() }
            action("Crear rama desde aquí") { confirmation = "branch" }
            action("Rebobinar hasta aquí", destructive = true) { confirmation = "rewind" }
            action("Fijar como memoria") { onPinMemory(); onDismiss() }
            action("Reportar localmente") { onReport(); onDismiss() }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
        }
    }
    when (confirmation) {
        "branch" -> AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text("Crear una rama") },
            text = { Text("Se conservarán los mensajes hasta este punto en una conversación nueva.") },
            confirmButton = { TextButton(onClick = { confirmation = null; onBranch(); onDismiss() }) { Text("Crear") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancelar") } },
        )
        "rewind" -> AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text("Rebobinar conversación") },
            text = { Text("Se eliminarán este mensaje y todos los posteriores. Esta acción no se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirmation = null; onRewind(); onDismiss() }) { Text("Rebobinar") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun action(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
