package com.localcharacter.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localcharacter.app.data.settings.AiBudgetSettings
import com.localcharacter.app.data.settings.CustomAiProviderSettings
import com.localcharacter.app.llm.provider.LlmModelInfo
import com.localcharacter.app.llm.provider.PricingType
import com.localcharacter.app.llm.provider.ProviderKind
import com.localcharacter.app.llm.provider.ProviderModelSelection
import com.localcharacter.app.llm.provider.ProviderStatus
import com.localcharacter.app.llm.provider.ProviderSummary
import com.localcharacter.app.ui.AppViewModel
import com.localcharacter.app.ui.components.DetailTopBar
import com.localcharacter.app.ui.components.SectionHeader
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProvidersScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val summaries by viewModel.providerSummaries.collectAsState()
    val settings by viewModel.aiProviderSettings.collectAsState()
    val operation by viewModel.providerOperation.collectAsState()
    val monthlySpend by viewModel.monthlyAiSpend.collectAsState()
    var configuring by remember { mutableStateOf<ProviderSummary?>(null) }
    var addCustom by rememberSaveable { mutableStateOf(false) }
    var budgetText by rememberSaveable(settings.budget.monthlyBudgetUsd) {
        mutableStateOf(settings.budget.monthlyBudgetUsd?.toString().orEmpty())
    }
    var warningText by rememberSaveable(settings.budget.warningPercent) {
        mutableStateOf(settings.budget.warningPercent.toString())
    }

    Scaffold(
        topBar = {
            DetailTopBar("Proveedores de IA", onBack)
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("El personaje no cambia de identidad", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Personalidad, historial, lore, memoria y relación permanecen locales aunque cambies el cerebro.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item { SectionHeader("Proveedores") }
            items(summaries, key = { it.definition.providerId }) { summary ->
                ProviderCard(summary, onConfigure = { configuring = summary })
            }
            item {
                OutlinedButton(onClick = { addCustom = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Añadir OpenAI-compatible personalizado")
                }
            }
            operation?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }

            item { SectionHeader("Preferencias") }
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        SettingToggle(
                            "Preferir modelos gratuitos",
                            "Ordena Local, Gratis y Free tier antes de Pago.",
                            settings.preferFreeModels,
                        ) { viewModel.updateAiPreferences(preferFree = it) }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        SettingToggle(
                            "Fallback automático",
                            "Desactivado por defecto. Nunca reintenta tras recibir texto ni ante una key inválida.",
                            settings.automaticFallback,
                        ) { viewModel.updateAiPreferences(automaticFallback = it) }
                        if (settings.cachedModels.isNotEmpty() || settings.fallbackChain.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text("Cadena de fallback", style = MaterialTheme.typography.titleSmall)
                            Text("Toca modelos para añadirlos o quitarlos en el orden mostrado.", style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                (listOf(LlmModelInfo("local", "active", "Local GGUF", PricingType.LOCAL)) +
                                    settings.cachedModels.values.flatten()).take(40).forEach { model ->
                                    val selection = ProviderModelSelection(model.providerId, model.modelId)
                                    FilterChip(
                                        selected = selection in settings.fallbackChain,
                                        onClick = {
                                            val next = if (selection in settings.fallbackChain) {
                                                settings.fallbackChain - selection
                                            } else settings.fallbackChain + selection
                                            viewModel.updateAiPreferences(fallbackChain = next)
                                        },
                                        label = { Text(model.displayName, maxLines = 1) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Uso y presupuesto") }
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Este mes", style = MaterialTheme.typography.titleMedium)
                        Text("Costo estimado: $${"%.4f".format(monthlySpend)}")
                        Text(
                            "Solo se calcula cuando el proveedor entrega tokens y existe precio verificable. Local cuesta $0 de API.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = budgetText,
                            onValueChange = { budgetText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Presupuesto mensual USD (opcional)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = warningText,
                            onValueChange = { warningText = it.filter(Char::isDigit).take(3) },
                            label = { Text("Avisar al porcentaje") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SettingToggle(
                            "Aviso de presupuesto",
                            "OFF por defecto. Muestra el aviso al alcanzar el porcentaje configurado.",
                            settings.budget.warnAtLimit,
                        ) {
                            viewModel.updateAiPreferences(
                                budget = settings.budget.copy(
                                    monthlyBudgetUsd = budgetText.toDoubleOrNull(),
                                    warningPercent = warningText.toIntOrNull()?.coerceIn(1, 100) ?: 80,
                                    warnAtLimit = it,
                                ),
                            )
                        }
                        val budget = budgetText.toDoubleOrNull()
                        val warningPercent = warningText.toIntOrNull()?.coerceIn(1, 100) ?: 80
                        if (settings.budget.warnAtLimit && budget != null && budget > 0.0 && monthlySpend >= budget * warningPercent / 100.0) {
                            Text(
                                "Aviso: alcanzaste al menos el $warningPercent% del presupuesto.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        SettingToggle(
                            "Bloquear APIs de pago al 100%",
                            "OFF por defecto. No afecta modelos Local, Gratis o Free tier.",
                            settings.budget.blockAtLimit,
                        ) {
                            viewModel.updateAiPreferences(
                                budget = settings.budget.copy(
                                    monthlyBudgetUsd = budgetText.toDoubleOrNull(),
                                    warningPercent = warningText.toIntOrNull()?.coerceIn(1, 100) ?: 80,
                                    blockAtLimit = it,
                                ),
                            )
                        }
                        Button(
                            onClick = {
                                viewModel.updateAiPreferences(
                                    budget = settings.budget.copy(
                                        monthlyBudgetUsd = budgetText.toDoubleOrNull(),
                                        warningPercent = warningText.toIntOrNull()?.coerceIn(1, 100) ?: 80,
                                    ),
                                )
                            },
                        ) { Text("Guardar presupuesto") }
                    }
                }
            }
            item { Text("Información de precio sujeta a cambios.", style = MaterialTheme.typography.bodySmall) }
            item { Spacer(Modifier.height(70.dp)) }
        }
    }

    configuring?.let { summary ->
        ProviderConfigDialog(
            summary = summary,
            cachedModelCount = settings.cachedModels[summary.definition.providerId]?.size ?: 0,
            onDismiss = { configuring = null },
            onSaveKey = { viewModel.saveAiCredential(summary.definition.providerId, it) },
            onDeleteKey = { viewModel.deleteAiCredential(summary.definition.providerId) },
            onTest = { viewModel.testAiProvider(summary.definition.providerId) },
            onRefresh = { viewModel.refreshAiModels(summary.definition.providerId) },
            onDeleteProvider = if (summary.definition.kind == ProviderKind.CUSTOM) {
                { viewModel.deleteCustomAiProvider(summary.definition.providerId); configuring = null }
            } else null,
        )
    }
    if (addCustom) {
        CustomProviderDialog(
            onDismiss = { addCustom = false },
            onSave = { config, key -> viewModel.saveCustomAiProvider(config, key); addCustom = false },
        )
    }
}

@Composable
private fun ProviderCard(summary: ProviderSummary, onConfigure: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onConfigure),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (summary.definition.kind == ProviderKind.LOCAL) Icons.Default.Lock else Icons.AutoMirrored.Filled.Send,
                null, tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(summary.definition.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${if (summary.definition.kind == ProviderKind.LOCAL) "Local" else "Online"} · ${pricingLabel(summary.definition.pricingType)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(statusLabel(summary.status), color = statusColor(summary.status), style = MaterialTheme.typography.labelMedium)
                summary.maskedKey?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                summary.selectedModelId?.let { Text("Modelo: $it", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Text(if (summary.definition.kind == ProviderKind.LOCAL) "Info" else "Configurar", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ProviderConfigDialog(
    summary: ProviderSummary,
    cachedModelCount: Int,
    onDismiss: () -> Unit,
    onSaveKey: (String) -> Unit,
    onDeleteKey: () -> Unit,
    onTest: () -> Unit,
    onRefresh: () -> Unit,
    onDeleteProvider: (() -> Unit)?,
) {
    var key by rememberSaveable(summary.definition.providerId) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(summary.definition.displayName) },
        text = {
            Column {
                Text(summary.definition.pricingNote)
                Text("Verificado: ${summary.definition.verifiedAt}", style = MaterialTheme.typography.bodySmall)
                Text(summary.definition.baseUrl, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                if (summary.definition.requiresApiKey) {
                    if (summary.keyConfigured) Text("Key guardada: ${summary.maskedKey}")
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text(if (summary.keyConfigured) "Nueva API Key" else "API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Modelos en caché: $cachedModelCount", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (summary.definition.requiresApiKey) {
                        FilledTonalButton(onClick = { if (key.isNotBlank()) onSaveKey(key) }, enabled = key.isNotBlank()) { Text("Guardar") }
                    }
                    OutlinedButton(onClick = onTest) { Text("Probar") }
                    OutlinedButton(onClick = onRefresh) { Text("Actualizar modelos") }
                }
                if (summary.keyConfigured) TextButton(onClick = onDeleteKey) { Text("Eliminar credencial") }
                onDeleteProvider?.let { TextButton(onClick = it) { Text("Eliminar proveedor") } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun CustomProviderDialog(onDismiss: () -> Unit, onSave: (CustomAiProviderSettings, String?) -> Unit) {
    val id = remember { "custom_${UUID.randomUUID()}" }
    var name by rememberSaveable { mutableStateOf("") }
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var modelId by rememberSaveable { mutableStateOf("") }
    var key by rememberSaveable { mutableStateOf("") }
    var requiresKey by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OpenAI-compatible") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL, por ejemplo http://192.168.1.50:1234/v1") }, modifier = Modifier.fillMaxWidth())
                if (baseUrl.startsWith("http://")) Text("Conexión HTTP sin cifrar.", color = MaterialTheme.colorScheme.error)
                OutlinedTextField(modelId, { modelId = it }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Requiere API Key", modifier = Modifier.weight(1f))
                    Switch(requiresKey, { requiresKey = it })
                }
                if (requiresKey) OutlinedTextField(
                    key, { key = it }, label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        CustomAiProviderSettings(id, name.trim(), baseUrl.trim(), modelId.trim(), requiresKey),
                        key.takeIf(String::isNotBlank),
                    )
                },
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && modelId.isNotBlank() && (!requiresKey || key.isNotBlank()),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterAiSettingsScreen(characterId: String, viewModel: AppViewModel, onBack: () -> Unit) {
    val characters by viewModel.characters.collectAsState()
    val localModels by viewModel.models.collectAsState()
    val settings by viewModel.aiProviderSettings.collectAsState()
    val character = characters.firstOrNull { it.id == characterId }
    val models = remember(settings.cachedModels, localModels) {
        val activeLocal = localModels.firstOrNull { it.isActive }
        listOf(
            LlmModelInfo(
                providerId = "local",
                modelId = "active",
                displayName = activeLocal?.let { "Local GGUF · ${it.displayName}" } ?: "Local GGUF (carga un modelo)",
                pricingType = PricingType.LOCAL,
                contextLength = activeLocal?.contextSize,
            ),
        ) + settings.cachedModels.values.flatten()
    }
    val selected = settings.characterSelections[characterId]
    val localOnly = characterId in settings.localOnlyCharacterIds
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(models, search, settings.preferFreeModels, settings.favoriteModels) {
        models.filter { (!localOnly || it.providerId == "local") &&
            (search.isBlank() || it.displayName.contains(search, true) || it.modelId.contains(search, true)) }
            .sortedWith(compareBy<LlmModelInfo> {
                if ("${it.providerId}/${it.modelId}" in settings.favoriteModels) 0 else 1
            }.thenBy { if (settings.preferFreeModels) pricingRank(it.pricingType) else 0 }.thenBy { it.displayName.lowercase() })
    }
    Scaffold(
        topBar = {
            DetailTopBar("IA de ${character?.name ?: "personaje"}", onBack)
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        SettingToggle(
                            "Solo modelos locales",
                            "Si está activo, este personaje nunca envía contexto a una API.",
                            localOnly,
                        ) { viewModel.setCharacterLocalOnly(characterId, it) }
                        TextButton(onClick = { viewModel.setCharacterAiModel(characterId, null) }) {
                            Text(if (selected == null) "✓ Usar modelo global" else "Usar modelo global")
                        }
                    }
                }
            }
            item {
                OutlinedTextField(search, { search = it }, label = { Text("Buscar modelos") }, modifier = Modifier.fillMaxWidth())
            }
            if (models.isEmpty()) item {
                Text("Configura un proveedor y actualiza su catálogo desde Ajustes → Proveedores de IA.")
            }
            items(filtered.take(300), key = { "${it.providerId}/${it.modelId}" }) { model ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.setCharacterAiModel(characterId, model) },
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                            Text("${model.providerId} · ${pricingLabel(model.pricingType)}", style = MaterialTheme.typography.bodySmall)
                            Text("Contexto: ${model.contextLength ?: "desconocido"}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (selected?.providerId == model.providerId && selected.modelId == model.modelId) Text("✓")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

private fun statusLabel(status: ProviderStatus) = when (status) {
    ProviderStatus.LOCAL -> "● Local"
    ProviderStatus.CONNECTED -> "✓ Conectado"
    ProviderStatus.NOT_CONFIGURED -> "○ No configurado"
    ProviderStatus.RATE_LIMITED -> "⚠ Rate limit"
    ProviderStatus.OFFLINE -> "○ Sin conexión"
    ProviderStatus.ERROR -> "× Error"
}

@Composable
private fun statusColor(status: ProviderStatus) = when (status) {
    ProviderStatus.ERROR, ProviderStatus.RATE_LIMITED -> MaterialTheme.colorScheme.error
    ProviderStatus.CONNECTED, ProviderStatus.LOCAL -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun pricingLabel(type: PricingType) = when (type) {
    PricingType.LOCAL -> "Local"
    PricingType.FREE -> "Gratis"
    PricingType.FREE_TIER -> "Free tier"
    PricingType.PAID -> "Pago"
    PricingType.UNKNOWN -> "Precio no disponible"
}

private fun pricingRank(type: PricingType) = when (type) {
    PricingType.FREE -> 0
    PricingType.FREE_TIER -> 1
    PricingType.LOCAL -> 2
    PricingType.UNKNOWN -> 3
    PricingType.PAID -> 4
}
