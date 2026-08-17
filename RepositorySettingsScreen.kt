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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localcharacter.app.data.catalog.LocalCharacterCatalogProvider
import com.localcharacter.app.data.settings.CustomRepositorySettings
import com.localcharacter.app.domain.character.ProviderAvailability
import com.localcharacter.app.domain.character.ProviderDescriptor
import com.localcharacter.app.ui.AppViewModel
import com.localcharacter.app.ui.components.DetailTopBar
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositorySettingsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val customRepositories by viewModel.customRepositories.collectAsState()
    val enabledProviders by viewModel.enabledCatalogProviderIds.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val testStatus by viewModel.repositoryTestStatus.collectAsState()
    var editing by remember { mutableStateOf<CustomRepositorySettings?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            DetailTopBar("Repositorios", onBack)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Fuentes integradas", style = MaterialTheme.typography.titleLarge)
            }
            catalog.providers.filterNot { it.id.startsWith("repository_") }.forEach { provider ->
                item(key = provider.id) {
                    BuiltInRepositoryCard(
                        provider = provider,
                        enabled = provider.id == LocalCharacterCatalogProvider.ID || provider.id in enabledProviders,
                        onEnabled = { viewModel.setCatalogProviderEnabled(provider.id, it) },
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Repositorios personalizados", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { editing = null; showEditor = true; viewModel.clearRepositoryTestStatus() }) {
                        Icon(Icons.Default.Add, null)
                        Text("Añadir")
                    }
                }
            }
            if (customRepositories.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            "Añade la URL HTTPS de un repository.json. Se validará el esquema antes de mostrarlo en Explorar.",
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            }
            customRepositories.forEach { repository ->
                item(key = repository.id) {
                    CustomRepositoryCard(
                        repository = repository,
                        onEnabled = { viewModel.setCustomRepositoryEnabled(repository.id, it) },
                        onSync = { viewModel.syncCustomRepository(repository.id) },
                        onEdit = { editing = repository; showEditor = true; viewModel.clearRepositoryTestStatus() },
                        onDelete = { viewModel.deleteCustomRepository(repository.id) },
                    )
                }
            }
            item {
                Text(
                    "Solo se aceptan HTTPS y los recursos deben pertenecer al mismo host del índice. Desactivar o borrar una fuente no elimina personajes ya instalados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showEditor) {
        RepositoryEditorDialog(
            repository = editing,
            testStatus = testStatus,
            onDismiss = { showEditor = false; viewModel.clearRepositoryTestStatus() },
            onTest = viewModel::testCustomRepository,
            onSave = { name, url ->
                viewModel.saveCustomRepository(editing?.id, name, url)
                showEditor = false
                viewModel.clearRepositoryTestStatus()
            },
        )
    }
}

@Composable
private fun BuiltInRepositoryCard(provider: ProviderDescriptor, enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    val local = provider.id == LocalCharacterCatalogProvider.ID
    val supported = provider.capabilities.search && provider.availability != ProviderAvailability.UNSUPPORTED
    Card(shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        local -> "Siempre activo · datos guardados en el dispositivo"
                        supported -> provider.statusMessage
                        else -> "No compatible · ${provider.statusMessage}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = enabled && supported || local, onCheckedChange = onEnabled, enabled = supported && !local)
        }
    }
}

@Composable
private fun CustomRepositoryCard(
    repository: CustomRepositorySettings,
    onEnabled: (Boolean) -> Unit,
    onSync: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(repository.name, style = MaterialTheme.typography.titleMedium)
                    Text(repository.indexUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(checked = repository.enabled, onCheckedChange = onEnabled)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    append(repository.lastStatus)
                    repository.characterCount?.let { append(" · $it personajes") }
                    repository.lastCheckedAt?.let { append(" · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (repository.lastStatus.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(top = 10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onSync) { Icon(Icons.Default.Refresh, "Sincronizar") }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar") }
            }
        }
    }
}

@Composable
private fun RepositoryEditorDialog(
    repository: CustomRepositorySettings?,
    testStatus: String?,
    onDismiss: () -> Unit,
    onTest: (String, String) -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember(repository?.id) { mutableStateOf(repository?.name.orEmpty()) }
    var url by remember(repository?.id) { mutableStateOf(repository?.indexUrl.orEmpty()) }
    val validDraft = name.isNotBlank() && url.startsWith("https://", ignoreCase = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (repository == null) "Añadir repositorio" else "Editar repositorio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("URL de repository.json") }, singleLine = true)
                Text("Ejemplo: https://ejemplo.org/repository.json", style = MaterialTheme.typography.bodySmall)
                testStatus?.let {
                    Text(it, color = if (it.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                Button(onClick = { onTest(name, url) }, enabled = validDraft) { Text("Comprobar conexión") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, url) }, enabled = validDraft) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
