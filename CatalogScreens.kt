package com.localcharacter.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localcharacter.app.data.catalog.LocalCharacterCatalogProvider
import com.localcharacter.app.domain.character.CatalogSort
import com.localcharacter.app.domain.character.ProviderAvailability
import com.localcharacter.app.domain.character.RemoteCharacterSummary
import com.localcharacter.app.domain.model.ContentMode
import com.localcharacter.app.ui.AppViewModel
import com.localcharacter.app.ui.catalog.RemoteDetailUiState
import com.localcharacter.app.ui.components.CharacterAvatar
import com.localcharacter.app.ui.components.DetailTopBar
import com.localcharacter.app.ui.components.EmptyState
import com.localcharacter.app.ui.components.LoadingIndicator
import com.localcharacter.app.ui.components.MainScreenTopBar
import com.localcharacter.app.ui.components.SectionHeader
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: AppViewModel,
    onCharacter: (String) -> Unit,
    onRemoteCharacter: (String, String) -> Unit,
    onCreate: () -> Unit,
) {
    val state by viewModel.catalog.collectAsState()
    val contentMode by viewModel.contentMode.collectAsState()
    LaunchedEffect(Unit) { viewModel.startCatalog() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importCharacter(it, onCharacter) }
    }
    val selected = state.providers.firstOrNull { it.id == state.selectedProviderId }
    val resultTags = state.items.flatMap { it.tags }.distinct().take(12)
    Scaffold(topBar = { MainScreenTopBar("Explorar") }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setCatalogQuery,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Busca personajes o etiquetas") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                )
            }
            item {
                Column {
                    Text("Fuente", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.selectedProviderId == AppViewModel.ALL_PROVIDERS_ID,
                            onClick = { viewModel.selectCatalogProvider(AppViewModel.ALL_PROVIDERS_ID) },
                            label = { Text("Todos") },
                        )
                        state.providers.forEach { provider ->
                            FilterChip(
                                selected = state.selectedProviderId == provider.id,
                                onClick = { viewModel.selectCatalogProvider(provider.id) },
                                label = {
                                    Text(
                                        provider.displayName + if (provider.availability == ProviderAvailability.UNSUPPORTED) " · no compatible" else "",
                                    )
                                },
                            )
                        }
                    }
                }
            }
            if (resultTags.isNotEmpty()) {
                item {
                    Column {
                        Text("Categorías", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            resultTags.forEach { tag ->
                                FilterChip(
                                    selected = tag in state.selectedTags,
                                    onClick = { viewModel.toggleCatalogTag(tag) },
                                    label = { Text(tag) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.safeOnly || contentMode == ContentMode.STANDARD,
                        onClick = { viewModel.setCatalogSafeOnly(!state.safeOnly) },
                        enabled = contentMode == ContentMode.ADULT_ENABLED,
                        label = {
                            Text(
                                if (contentMode == ContentMode.STANDARD) "Solo SFW · bloqueado en Ajustes"
                                else if (state.safeOnly) "Solo SFW" else "SFW + NSFW",
                            )
                        },
                    )
                    CatalogSort.entries.forEach { sort ->
                        FilterChip(
                            selected = state.sort == sort,
                            onClick = { viewModel.setCatalogSort(sort) },
                            label = { Text(sort.label()) },
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onCreate, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Crear")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "image/png", "*/*")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Importar")
                    }
                }
            }
            item {
                val health = state.health[state.selectedProviderId]
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(selected?.displayName ?: "Todos los proveedores disponibles", style = MaterialTheme.typography.titleMedium)
                        Text(
                            health?.message ?: selected?.statusMessage ?: "Los resultados llegan de forma progresiva y cada fuente falla de manera independiente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.selectedProviderId != LocalCharacterCatalogProvider.ID) {
                            Text(
                                "Idioma: ${state.language.displayName} · Solo se consulta catálogo público. Chats, memorias y modelos nunca se envían.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            item { SectionHeader(if (state.query.isBlank()) "Catálogo" else "Resultados (${state.total ?: state.items.size})") }
            item { LoadingIndicator(state.loading, "Consultando ${selected?.displayName ?: "proveedores"}…") }
            state.error?.let { message ->
                item {
                    CatalogError(message, selected?.availability != ProviderAvailability.UNSUPPORTED, viewModel::retryCatalog)
                }
            }
            if (!state.loading && state.error == null && state.items.isEmpty()) {
                item { EmptyState("Sin coincidencias", "Prueba otra búsqueda o selecciona una fuente distinta.") }
            }
            itemsIndexed(state.items, key = { _, item -> "${item.providerId}:${item.remoteId}" }) { index, item ->
                RemoteCharacterCard(item, state.providers.firstOrNull { it.id == item.providerId }?.displayName ?: item.providerId) {
                    viewModel.preloadCatalogImage(item.avatarUrl)
                    item.installedCharacterId?.let(onCharacter) ?: onRemoteCharacter(item.providerId, item.remoteId)
                }
                if (index >= state.items.lastIndex - 2 && state.nextCursor != null) {
                    LaunchedEffect(item.providerId, item.remoteId, state.nextCursor) { viewModel.loadMoreCatalog() }
                }
            }
            item { LoadingIndicator(state.loadingMore, "Cargando más…") }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }
}

@Composable
private fun RemoteCharacterCard(item: RemoteCharacterSummary, providerName: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            CharacterAvatar(item.name, Modifier.size(68.dp), item.avatarUrl)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.installedCharacterId != null) Icon(Icons.Default.CheckCircle, "Instalado", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Text(item.description, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    listOfNotNull(item.author?.let { "por $it" }, providerName, item.language?.uppercase(), item.downloadCount?.let { "$it descargas" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CatalogError(message: String, retryable: Boolean, retry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            if (retryable) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = retry) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Reintentar") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteCharacterDetailScreen(
    providerId: String,
    remoteId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onLocalCharacter: (String) -> Unit,
    onChat: (String) -> Unit,
) {
    val state by viewModel.remoteDetail.collectAsState()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(providerId, remoteId) { viewModel.loadRemoteDetail(providerId, remoteId) }
    Scaffold(
        topBar = {
            DetailTopBar("Personaje remoto", onBack)
        },
    ) { padding ->
        when (val current = state) {
            RemoteDetailUiState.Idle, RemoteDetailUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is RemoteDetailUiState.Error -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CatalogError(current.message, true) { viewModel.loadRemoteDetail(providerId, remoteId) } }
            is RemoteDetailUiState.Ready -> {
                val detail = current.detail
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { CharacterAvatar(detail.summary.name, Modifier.size(148.dp), detail.summary.avatarUrl) }
                    item { Text(detail.summary.name, style = MaterialTheme.typography.headlineMedium) }
                    item {
                        Text(
                            listOfNotNull(detail.summary.author?.let { "por $it" }, detail.version?.let { "versión $it" }).joinToString(" · "),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            detail.summary.tags.take(10).forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                current.installedCharacterId?.let { viewModel.openChat(it, onReady = onChat) }
                                    ?: viewModel.installRemoteCharacter(onLocalCharacter)
                            },
                            enabled = !current.installing,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            if (current.installing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(if (current.installedCharacterId != null) Icons.Default.CheckCircle else Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (current.installedCharacterId != null) "Chatear" else "Instalar para usar sin conexión")
                        }
                    }
                    current.installStep?.let { step -> item { Text(step, color = MaterialTheme.colorScheme.primary) } }
                    item { RemoteDetailSection("Descripción", detail.summary.description.ifBlank { "Sin descripción." }) }
                    detail.firstMessagePreview?.takeIf(String::isNotBlank)?.let { intro ->
                        item { RemoteDetailSection("Mensaje inicial", intro) }
                    }
                    item {
                        RemoteDetailSection(
                            "Fuente y privacidad",
                            "${detail.summary.sourceUrl}\n\nSe descargan únicamente la ficha pública, el PNG original y el avatar. Ningún chat, memoria, modelo ni dato personal sale del dispositivo.",
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = { runCatching { uriHandler.openUri(detail.summary.sourceUrl) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Ver página original") }
                    }
                    detail.summary.updatedAt?.let { updated ->
                        item { Text("Actualizado: ${DateFormat.getDateInstance().format(Date(updated))}", style = MaterialTheme.typography.labelSmall) }
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RemoteDetailSection(title: String, body: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(17.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(7.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun CatalogSort.label() = when (this) {
    CatalogSort.RECENT -> "Recientes"
    CatalogSort.TRENDING -> "Tendencia"
    CatalogSort.MOST_DOWNLOADED -> "Descargados"
}
