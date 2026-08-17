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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localcharacter.app.domain.model.GroupMemory
import com.localcharacter.app.ui.components.DetailTopBar
import com.localcharacter.app.ui.components.EmptyState
import com.localcharacter.app.ui.memory.GroupMemoryViewModel

private enum class GroupMemorySection(val label: String) {
    ALL("Recuerdos"), EVENTS("Eventos")
}

@Composable
fun GroupMemoryScreen(viewModel: GroupMemoryViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var sectionName by rememberSaveable { mutableStateOf(GroupMemorySection.ALL.name) }
    val section = GroupMemorySection.valueOf(sectionName)
    val filtered = state.memories.filter { memory ->
        section == GroupMemorySection.ALL || memory.type == "SHARED_EVENT"
    }
    Scaffold(topBar = { DetailTopBar("Memoria · ${state.groupName}", onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Memoria del grupo", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Estos recuerdos compartidos permanecen únicamente en tu dispositivo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupMemorySection.entries.forEach { item ->
                        FilterChip(selected = section == item, onClick = { sectionName = item.name }, label = { Text(item.label) })
                    }
                }
            }
            if (!state.loading && filtered.isEmpty()) {
                item { EmptyState("Sin recuerdos en esta sección", "Aparecerán aquí cuando fijes mensajes o el grupo registre eventos compartidos.") }
            }
            items(filtered, key = { it.id }) { memory ->
                GroupMemoryCard(memory, viewModel::delete)
            }
        }
    }
}

@Composable
private fun GroupMemoryCard(memory: GroupMemory, onDelete: (GroupMemory) -> Unit) {
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
                Text(
                    if (memory.type == "SHARED_EVENT") "✚ Evento compartido" else "✚ Recordatorio del grupo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onDelete(memory) }) {
                    Icon(Icons.Default.Delete, "Eliminar memoria")
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Abrir memoria", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
