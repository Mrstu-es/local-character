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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localcharacter.app.domain.model.Memory
import com.localcharacter.app.domain.model.MemoryType
import com.localcharacter.app.ui.components.DetailTopBar
import com.localcharacter.app.ui.components.EmptyState
import com.localcharacter.app.ui.memory.MemoryViewModel

private enum class MemorySection(val label: String) { MEMORIES("Recuerdos"), RELATIONSHIPS("Relaciones"), EVENTS("Eventos"), PREFERENCES("Preferencias") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(viewModel: MemoryViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var sectionName by rememberSaveable { mutableStateOf(MemorySection.MEMORIES.name) }
    var editing by remember { mutableStateOf<Memory?>(null) }
    val section = MemorySection.valueOf(sectionName)
    val filtered = remember(state.memories, section) {
        state.memories.filter { memory ->
            when (section) {
                MemorySection.MEMORIES -> memory.type in setOf(MemoryType.FACT, MemoryType.EMOTIONAL)
                MemorySection.RELATIONSHIPS -> memory.type in setOf(MemoryType.RELATIONSHIP, MemoryType.CHARACTER_RELATIONSHIP)
                MemorySection.EVENTS -> memory.type in setOf(MemoryType.EVENT, MemoryType.SHARED_EVENT, MemoryType.PROMISE, MemoryType.GOAL)
                MemorySection.PREFERENCES -> memory.type == MemoryType.PREFERENCE
            }
        }
    }
    Scaffold(
        topBar = {
            DetailTopBar("Memoria · ${state.characterName}", onBack)
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Memoria local", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Estos recuerdos permanecen únicamente en tu dispositivo.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MemorySection.entries.forEach { item ->
                        FilterChip(
                            selected = section == item,
                            onClick = { sectionName = item.name },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
            if (section == MemorySection.RELATIONSHIPS && state.relationship != null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Evolución con el personaje", style = MaterialTheme.typography.titleMedium)
                            Text(
                                state.relationship?.relationshipSummary.orEmpty().ifBlank { "La relación aún está empezando." },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (!state.loading && filtered.isEmpty()) {
                item { EmptyState("Sin recuerdos en esta sección", "Aparecerán aquí cuando sean útiles para futuras conversaciones.") }
            }
            items(filtered, key = { it.id }) { memory ->
                if (memory.isPinned) {
                    PinnedMemoryCard(memory, viewModel::togglePinned, viewModel::delete)
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(memory.content, style = MaterialTheme.typography.bodyLarge)
                                Text(memory.type.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { viewModel.togglePinned(memory) }) {
                                Icon(Icons.Default.Star, "Fijar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editing = memory }) { Icon(Icons.Default.Edit, "Editar") }
                            IconButton(onClick = { viewModel.delete(memory) }) { Icon(Icons.Default.Delete, "Eliminar") }
                        }
                    }
                }
            }
        }
    }
    editing?.let { memory ->
        var value by remember(memory.id) { mutableStateOf(memory.content) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Editar recuerdo") },
            text = { OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth(), minLines = 3) },
            confirmButton = { TextButton(onClick = { viewModel.edit(memory, value); editing = null }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PinnedMemoryCard(memory: Memory, onUnpin: (Memory) -> Unit, onDelete: (Memory) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, top = 14.dp, end = 8.dp, bottom = 8.dp)) {
            Text(
                memory.content,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("✚ Recordatorio fijado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconButton(onClick = { onUnpin(memory) }) { Icon(Icons.Default.Star, "Desfijar", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { onDelete(memory) }) { Icon(Icons.Default.Delete, "Eliminar") }
            }
        }
    }
}
