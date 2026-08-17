package com.localcharacter.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcharacter.app.domain.model.UserPersona
import com.localcharacter.app.ui.AppViewModel
import com.localcharacter.app.ui.components.CharacterAvatar
import com.localcharacter.app.ui.components.DetailTopBar
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val personas by viewModel.userPersonas.collectAsState()
    val current = personas.firstOrNull { it.isDefault } ?: personas.firstOrNull()
    val stableId = remember { current?.id ?: UUID.randomUUID().toString() }
    var initialized by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var avatarUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAvatar by rememberSaveable { mutableStateOf<String?>(null) }
    var removeAvatar by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            avatarUri = uri.toString()
            selectedAvatar = uri.toString()
            removeAvatar = false
        }
    }
    LaunchedEffect(current?.id) {
        if (!initialized && current != null) {
            name = current.name
            description = current.description
            avatarUri = current.avatarUri
            initialized = true
        }
    }

    fun save() {
        val now = System.currentTimeMillis()
        viewModel.saveUserPersonaWithAvatar(
            persona = UserPersona(
                id = current?.id ?: stableId,
                name = name.trim(),
                avatarUri = avatarUri,
                description = description.trim(),
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
                isDefault = true,
            ),
            selectedAvatar = selectedAvatar?.let(android.net.Uri::parse),
            removeAvatar = removeAvatar,
            onSaved = onBack,
        )
    }

    Scaffold(
        topBar = {
            DetailTopBar("Mi perfil", onBack) {
                IconButton(onClick = ::save, enabled = name.isNotBlank()) { Icon(Icons.Default.Check, "Guardar") }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                CharacterAvatar(name.ifBlank { "U" }, Modifier.size(120.dp), avatarUri)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp))
                        Text(if (avatarUri == null) "Elegir avatar" else "Cambiar avatar")
                    }
                    if (avatarUri != null) {
                        OutlinedButton(
                            onClick = { avatarUri = null; selectedAvatar = null; removeAvatar = true },
                            modifier = Modifier.weight(1f),
                        ) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Quitar") }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        name, { name = it }, Modifier.fillMaxWidth(),
                        label = { Text("Nombre") }, singleLine = true,
                    )
                    OutlinedTextField(
                        description, { description = it.take(600) }, Modifier.fillMaxWidth(),
                        label = { Text("Descripción opcional") }, minLines = 4,
                        supportingText = { Text("${description.length}/600 · contexto breve para los personajes") },
                    )
                }
            }
            item {
                Button(onClick = ::save, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Guardar perfil")
                }
            }
        }
    }
}
