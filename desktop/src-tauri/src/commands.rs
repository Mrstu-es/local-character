use crate::card_parser;
use crate::engine::{
    generate_stream, resolve_llama_cli, ChatMessage, EngineLog, EngineStatus, GenerationStats,
    LocalLlmEngine, RuntimeState,
};
use crate::hardware::{detect, HardwareSnapshot};
use crate::model_registry::inspect_gguf;
use crate::models::{
    CharacterRecord, ChatMessageRecord, ConversationRecord, ConversationSummaryRecord,
    ExploreFilterCatalog, GroupRecord, ModelRecord, ProviderRecord, RemoteCharacterRecord,
    RepositoryProbe, RepositorySourceRecord, RepositorySyncResult, SuspiciousMessageRecord,
    VoiceModelRecord, VoiceRepositoryRecord, VoiceRepositorySyncResult,
};
use crate::native_process;
use crate::repository;
use crate::state::AppState;
use base64::Engine as _;
use serde::{Deserialize, Serialize};
use std::io::Read;
use std::path::Path;
use std::process::Stdio;
use std::sync::Arc;
use std::time::Instant;
use tauri::{AppHandle, Emitter, State};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AppDiagnostics {
    pub data_path: String,
    pub schema_version: i64,
    pub engine: EngineStatus,
    pub llama_release: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GenerateRequest {
    pub prompt: String,
    #[serde(default)]
    pub messages: Option<Vec<ChatMessage>>,
    #[serde(default)]
    pub character_name: Option<String>,
    #[serde(default)]
    pub user_name: Option<String>,
    #[serde(default)]
    pub generation_id: Option<String>,
    #[serde(default)]
    pub conversation_id: Option<String>,
    #[serde(default)]
    pub message_id: Option<String>,
    pub max_output: Option<u32>,
    pub context: Option<u32>,
    pub gpu_layers: Option<i32>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct GenerationErrorEvent {
    generation_id: String,
    conversation_id: String,
    message_id: String,
    error: String,
}

#[tauri::command]
pub fn get_hardware_snapshot() -> HardwareSnapshot {
    detect()
}

#[tauri::command]
pub fn get_diagnostics(state: State<'_, AppState>) -> Result<AppDiagnostics, String> {
    let database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    let engine = state
        .engine
        .lock()
        .map_err(|_| "Motor bloqueado".to_string())?;
    Ok(AppDiagnostics {
        data_path: database.path.to_string_lossy().to_string(),
        schema_version: database.schema_version()?,
        engine: engine.get_status(),
        llama_release: "b10218 / de69995".into(),
    })
}

#[tauri::command]
pub fn list_models(state: State<'_, AppState>) -> Result<Vec<ModelRecord>, String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    let mut models = database.list_models()?;
    for model in &mut models {
        model.exists = Path::new(&model.path).is_file();
    }
    Ok(models)
}

#[tauri::command]
pub fn add_model(path: String, state: State<'_, AppState>) -> Result<ModelRecord, String> {
    let path = Path::new(&path);
    if !path.is_absolute() {
        return Err("Selecciona una ruta absoluta de Windows".into());
    }
    let canonical = path
        .canonicalize()
        .map_err(|error| format!("No se pudo validar la ruta del modelo: {error}"))?;
    let model = inspect_gguf(&canonical)?;
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.upsert_model(&model)?;
    Ok(model)
}

#[tauri::command]
pub fn remove_model(id: String, state: State<'_, AppState>) -> Result<(), String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.delete_model(&id)
}

#[tauri::command]
pub fn list_characters(state: State<'_, AppState>) -> Result<Vec<CharacterRecord>, String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.list_characters()
}

#[tauri::command]
pub fn save_character(
    character: CharacterRecord,
    state: State<'_, AppState>,
) -> Result<CharacterRecord, String> {
    if character.name.trim().is_empty() {
        return Err("El personaje necesita un nombre".into());
    }
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.upsert_character(&character)?;
    Ok(character)
}

#[tauri::command]
pub fn delete_character(id: String, state: State<'_, AppState>) -> Result<(), String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.delete_character(&id)
}

#[tauri::command]
pub fn list_groups(state: State<'_, AppState>) -> Result<Vec<GroupRecord>, String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.list_groups()
}

#[tauri::command]
pub fn save_group(group: GroupRecord, state: State<'_, AppState>) -> Result<GroupRecord, String> {
    if group.name.trim().is_empty() {
        return Err("El grupo necesita un nombre".into());
    }
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.upsert_group(&group)?;
    Ok(group)
}

#[tauri::command]
pub fn delete_group(id: String, state: State<'_, AppState>) -> Result<(), String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.delete_group(&id)
}

#[tauri::command]
pub fn list_providers(state: State<'_, AppState>) -> Result<Vec<ProviderRecord>, String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.list_providers()
}

#[tauri::command]
pub fn save_provider(
    provider: ProviderRecord,
    state: State<'_, AppState>,
) -> Result<ProviderRecord, String> {
    if provider.name.trim().is_empty() {
        return Err("La API necesita un nombre".into());
    }
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.upsert_provider(&provider)?;
    Ok(provider)
}

#[tauri::command]
pub fn delete_provider(id: String, state: State<'_, AppState>) -> Result<(), String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.delete_provider(&id)
}

#[tauri::command]
pub async fn discover_provider_models(provider: ProviderRecord) -> Result<Vec<String>, String> {
    let key = provider
        .api_key
        .as_deref()
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .ok_or_else(|| "Añade una API key para consultar los modelos".to_string())?;
    let base = provider
        .endpoint
        .as_deref()
        .unwrap_or_default()
        .trim_end_matches('/');
    if base.is_empty() {
        return Err("No se pudo determinar la API seleccionada".into());
    }
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .map_err(|error| format!("No se pudo preparar la conexión: {error}"))?;
    let mut request = client.get(if base.ends_with("/models") {
        base.to_string()
    } else {
        format!("{base}/models")
    });
    if provider.kind == "gemini" {
        request = request.query(&[("key", key)]);
    } else if provider.kind == "anthropic" {
        request = request
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01");
    } else {
        request = request.bearer_auth(key);
    }
    let response = request
        .send()
        .await
        .map_err(|error| format!("No se pudo consultar el catálogo: {error}"))?;
    let status = response.status();
    let payload: serde_json::Value = response
        .json()
        .await
        .map_err(|error| format!("La API devolvió una respuesta no válida: {error}"))?;
    if !status.is_success() {
        let detail = payload
            .get("error")
            .and_then(|value| value.get("message").or(Some(value)))
            .and_then(serde_json::Value::as_str)
            .unwrap_or("respuesta de error");
        return Err(format!("La API respondió {status}: {detail}"));
    }
    let mut models = Vec::new();
    for item in payload
        .get("data")
        .and_then(serde_json::Value::as_array)
        .into_iter()
        .flatten()
    {
        if let Some(id) = item.get("id").and_then(serde_json::Value::as_str) {
            models.push(id.to_string());
        }
    }
    for item in payload
        .get("models")
        .and_then(serde_json::Value::as_array)
        .into_iter()
        .flatten()
    {
        if let Some(name) = item.get("name").and_then(serde_json::Value::as_str) {
            models.push(name.strip_prefix("models/").unwrap_or(name).to_string());
        }
    }
    models.sort_unstable();
    models.dedup();
    if models.is_empty() {
        return Err("La API no devolvió modelos disponibles".into());
    }
    Ok(models)
}

#[tauri::command]
pub fn read_avatar_data(path: String) -> Result<String, String> {
    let source = Path::new(&path);
    if !source.is_absolute() {
        return Err("La imagen debe estar en una ruta absoluta".into());
    }
    let canonical = source
        .canonicalize()
        .map_err(|e| format!("No se pudo abrir el avatar: {e}"))?;
    let extension = canonical
        .extension()
        .and_then(|value| value.to_str())
        .unwrap_or("")
        .to_ascii_lowercase();
    let mime = match extension.as_str() {
        "png" => "image/png",
        "jpg" | "jpeg" => "image/jpeg",
        "webp" => "image/webp",
        "gif" => "image/gif",
        _ => return Err("Formato de avatar no permitido".into()),
    };
    let bytes = std::fs::read(&canonical).map_err(|e| format!("No se pudo leer el avatar: {e}"))?;
    if bytes.len() > 8 * 1024 * 1024 {
        return Err("El avatar supera 8 MB".into());
    }
    Ok(format!(
        "data:{mime};base64,{}",
        base64::engine::general_purpose::STANDARD.encode(bytes)
    ))
}

#[tauri::command]
pub fn import_character_card(
    path: String,
    state: State<'_, AppState>,
) -> Result<CharacterRecord, String> {
    let path = Path::new(&path);
    if !path.is_absolute() {
        return Err("Selecciona una ruta absoluta de Windows".into());
    }
    let character = card_parser::import(path)?;
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.upsert_character(&character)?;
    Ok(character)
}

#[tauri::command]
pub fn import_character_repository(
    path: String,
    state: State<'_, AppState>,
) -> Result<Vec<CharacterRecord>, String> {
    let path = Path::new(&path);
    if !path.is_absolute() {
        return Err("Selecciona una ruta absoluta de Windows".into());
    }
    let characters = card_parser::import_repository(path)?;
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    for character in &characters {
        database.upsert_character(character)?;
    }
    Ok(characters)
}

#[tauri::command]
pub async fn import_character_repository_url(
    url: String,
    state: State<'_, AppState>,
) -> Result<Vec<CharacterRecord>, String> {
    let parsed = reqwest::Url::parse(url.trim())
        .map_err(|_| "La URL del repositorio no es válida".to_string())?;
    if !matches!(parsed.scheme(), "https" | "http") {
        return Err("El repositorio debe usar una URL http:// o https://".into());
    }
    let client = reqwest::Client::builder()
        .user_agent("LocalCharacterDesktop/0.1")
        .redirect(reqwest::redirect::Policy::limited(5))
        .build()
        .map_err(|error| format!("No se pudo preparar la descarga: {error}"))?;
    let response = client
        .get(parsed)
        .send()
        .await
        .map_err(|error| format!("No se pudo descargar el repositorio: {error}"))?;
    if !response.status().is_success() {
        return Err(format!(
            "El repositorio respondió con HTTP {}",
            response.status()
        ));
    }
    if response.content_length().unwrap_or(0) > 50 * 1024 * 1024 {
        return Err("El repositorio supera 50 MB".into());
    }
    let bytes = response
        .bytes()
        .await
        .map_err(|error| format!("No se pudo leer el repositorio: {error}"))?;
    let characters = card_parser::import_repository_bytes(&bytes)?;
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    for character in &characters {
        database.upsert_character(character)?;
    }
    Ok(characters)
}

#[tauri::command]
pub async fn probe_character_repository_url(url: String) -> Result<RepositoryProbe, String> {
    repository::probe(&url).await
}

#[tauri::command]
pub fn list_character_repositories(
    state: State<'_, AppState>,
) -> Result<Vec<RepositorySourceRecord>, String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.list_repositories()
}

#[tauri::command]
pub fn list_remote_characters(
    state: State<'_, AppState>,
) -> Result<Vec<RemoteCharacterRecord>, String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.list_remote_characters()
}

#[tauri::command]
pub fn list_voice_repositories(
    state: State<'_, AppState>,
) -> Result<Vec<VoiceRepositoryRecord>, String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.list_voice_repositories()
}

#[tauri::command]
pub fn list_voice_models(state: State<'_, AppState>) -> Result<Vec<VoiceModelRecord>, String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.list_voice_models()
}

#[tauri::command]
pub async fn sync_voice_repository(
    url: String,
    name: Option<String>,
    state: State<'_, AppState>,
) -> Result<VoiceRepositorySyncResult, String> {
    let endpoint = repository::normalize_url(&url)?.to_string();
    let voices = repository::fetch_voice_catalog(&endpoint).await?;
    let (id, enabled) = {
        let mut database = state
            .database
            .lock()
            .map_err(|_| "SQLite bloqueado".to_string())?;
        match database.voice_repository_by_endpoint(&endpoint)? {
            Some(existing) => (existing.id, existing.enabled),
            None => (Uuid::new_v4().to_string(), true),
        }
    };
    let repository = VoiceRepositoryRecord {
        id,
        name: name
            .filter(|value| !value.trim().is_empty())
            .unwrap_or_else(|| "Repositorio TTS".to_string()),
        endpoint,
        language: None,
        enabled,
        updated_at: chrono_like_now(),
    };
    let mut voices = voices;
    for voice in &mut voices {
        voice.repository_id = Some(repository.id.clone());
    }
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.replace_voice_repository(&repository, &voices)?;
    Ok(VoiceRepositorySyncResult { repository, voices })
}

#[tauri::command]
pub fn delete_voice_repository(id: String, state: State<'_, AppState>) -> Result<(), String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.delete_voice_repository(&id)
}

#[tauri::command]
pub fn get_explore_filter_catalog(
    state: State<'_, AppState>,
) -> Result<ExploreFilterCatalog, String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.explore_filter_catalog()
}

#[tauri::command]
pub async fn sync_character_repository(
    url: String,
    source_id: Option<String>,
    name: Option<String>,
    query: Option<String>,
    language: Option<String>,
    tags: Option<Vec<String>>,
    safe_only: Option<bool>,
    state: State<'_, AppState>,
) -> Result<RepositorySyncResult, String> {
    let normalized_url = repository::normalize_url(&url)?.to_string();
    let source_id = if let Some(source_id) = source_id {
        source_id
    } else {
        let existing = state
            .database
            .lock()
            .map_err(|_| "SQLite bloqueado".to_string())?
            .repository_by_url(&normalized_url)?;
        existing
            .map(|repository| repository.id)
            .unwrap_or_else(|| Uuid::new_v4().to_string())
    };
    let url_for_error = url.clone();
    let fetched = repository::fetch_catalog(
        &normalized_url,
        query.as_deref().unwrap_or_default(),
        language.as_deref(),
        tags.as_deref().unwrap_or_default(),
        safe_only.unwrap_or(true),
    )
    .await;
    let (probe, mut items) = match fetched {
        Ok(value) => value,
        Err(error) => {
            if let Ok(mut database) = state.database.lock() {
                let _ = database.upsert_repository(&RepositorySourceRecord {
                    id: source_id.clone(),
                    provider_id: repository::probe_without_network(&normalized_url)
                        .map(|probe| probe.provider_id)
                        .unwrap_or_else(|_| "unknown".into()),
                    name: name
                        .clone()
                        .filter(|value| !value.trim().is_empty())
                        .unwrap_or_else(|| url_for_error.clone()),
                    url: normalized_url.clone(),
                    enabled: true,
                    status: "ERROR".into(),
                    status_message: error.clone(),
                    last_sync_at: None,
                    updated_at: String::new(),
                });
            }
            return Err(error);
        }
    };
    let now = chrono_like_now();
    for item in &mut items {
        item.source_id = source_id.clone();
        item.cached_at = now.clone();
    }
    let source = RepositorySourceRecord {
        id: source_id.clone(),
        provider_id: probe.provider_id.clone(),
        name: name
            .filter(|value| !value.trim().is_empty())
            .unwrap_or_else(|| probe.provider_name.clone()),
        url: normalized_url,
        enabled: true,
        status: "READY".into(),
        status_message: probe.message.clone(),
        last_sync_at: Some(now),
        updated_at: String::new(),
    };
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.upsert_repository(&source)?;
    database.replace_remote_characters(&source_id, &items)?;
    Ok(RepositorySyncResult {
        source,
        probe,
        items,
    })
}

#[tauri::command]
pub fn delete_character_repository(id: String, state: State<'_, AppState>) -> Result<(), String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.delete_repository(&id)
}

#[tauri::command]
pub fn set_character_repository_enabled(
    id: String,
    enabled: bool,
    state: State<'_, AppState>,
) -> Result<(), String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.set_repository_enabled(&id, enabled)
}

#[tauri::command]
pub async fn install_remote_character(
    provider_id: String,
    remote_id: String,
    state: State<'_, AppState>,
) -> Result<CharacterRecord, String> {
    let record = {
        let mut database = state
            .database
            .lock()
            .map_err(|_| "SQLite bloqueado".to_string())?;
        database
            .get_remote_character(&provider_id, &remote_id)?
            .ok_or_else(|| "El personaje remoto ya no está en la caché".to_string())?
    };
    let card_bytes = match repository::download_card(&record).await {
        Ok(bytes) => bytes,
        Err(_error) if record.provider_id == "generic_json" => record.raw_json.as_bytes().to_vec(),
        Err(error) => return Err(error),
    };
    let mut character = card_parser::import_bytes(&card_bytes).or_else(|_| {
        let fallback = serde_json::json!({
            "name": record.name,
            "description": record.description,
            "first_mes": "",
            "tags": record.tags,
        });
        card_parser::import_bytes(&serde_json::to_vec(&fallback).unwrap_or_default())
    })?;
    let local_id = Uuid::new_v4().to_string();
    let root = {
        let database = state
            .database
            .lock()
            .map_err(|_| "SQLite bloqueado".to_string())?;
        database
            .path
            .parent()
            .ok_or_else(|| "No se pudo resolver la carpeta de datos".to_string())?
            .join("characters")
    };
    std::fs::create_dir_all(&root)
        .map_err(|error| format!("No se pudo preparar el almacenamiento local: {error}"))?;
    let directory = root.join(&local_id);
    std::fs::create_dir_all(&directory)
        .map_err(|error| format!("No se pudo crear la carpeta del personaje: {error}"))?;
    let card_path = directory.join(if card_bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        "original_card.png"
    } else {
        "original_card.json"
    });
    std::fs::write(&card_path, &card_bytes)
        .map_err(|error| format!("No se pudo guardar la Character Card: {error}"))?;
    let avatar_path = if let Some(url) = record.avatar_url.as_deref() {
        match repository::download_asset(url).await {
            Ok(bytes) => {
                let extension = reqwest::Url::parse(url)
                    .ok()
                    .and_then(|value| {
                        value
                            .path_segments()
                            .and_then(|mut segments| segments.next_back().map(str::to_string))
                    })
                    .and_then(|value| value.rsplit('.').next().map(str::to_string))
                    .filter(|value| {
                        matches!(value.as_str(), "png" | "jpg" | "jpeg" | "webp" | "gif")
                    })
                    .unwrap_or_else(|| "png".to_string());
                let path = directory.join(format!("avatar.{extension}"));
                std::fs::write(&path, bytes)
                    .map_err(|error| format!("No se pudo guardar el avatar: {error}"))?;
                Some(path.to_string_lossy().to_string())
            }
            Err(_) if card_bytes.starts_with(b"\x89PNG\r\n\x1a\n") => {
                Some(card_path.to_string_lossy().to_string())
            }
            Err(_) => None,
        }
    } else if card_bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        Some(card_path.to_string_lossy().to_string())
    } else {
        None
    };
    character.id = local_id.clone();
    character.avatar_path = avatar_path;
    character.created_at = chrono_like_now();
    character.updated_at = character.created_at.clone();
    let result = (|| -> Result<CharacterRecord, String> {
        let mut database = state
            .database
            .lock()
            .map_err(|_| "SQLite bloqueado".to_string())?;
        database.upsert_character(&character)?;
        database.set_remote_installed(&provider_id, &remote_id, &local_id)?;
        Ok(character.clone())
    })();
    match result {
        Ok(character) => Ok(character),
        Err(error) => {
            let _ = std::fs::remove_dir_all(&directory);
            Err(error)
        }
    }
}

fn chrono_like_now() -> String {
    use std::time::{SystemTime, UNIX_EPOCH};
    let seconds = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_secs())
        .unwrap_or_default();
    seconds.to_string()
}

#[tauri::command]
pub async fn load_model(
    app: AppHandle,
    id: String,
    gpu_layers: Option<i32>,
    state: State<'_, AppState>,
) -> Result<EngineStatus, String> {
    let model = {
        let mut database = state
            .database
            .lock()
            .map_err(|_| "SQLite bloqueado".to_string())?;
        database
            .get_model(&id)?
            .ok_or_else(|| "El modelo ya no está registrado".to_string())?
    };
    let runtime = Arc::clone(&state.engine);
    {
        let mut engine = runtime.lock().map_err(|_| "Motor bloqueado".to_string())?;
        if matches!(
            engine.runtime_state,
            RuntimeState::Starting
                | RuntimeState::LoadingModel
                | RuntimeState::Stopping
                | RuntimeState::Generating
        ) {
            return Err("El motor ya está ocupado con otro modelo o generación".into());
        }
        if engine.child.is_some() || engine.loaded_model.is_some() {
            engine.runtime_state = RuntimeState::Stopping;
            engine.emit_runtime_state(&app);
        }
        engine.loaded_model = Some(model.clone());
        engine.runtime_state = RuntimeState::Starting;
        engine.error = None;
        engine.emit_runtime_state(&app);
    }
    let app_for_task = app.clone();
    let gpu_layers = gpu_layers.unwrap_or(0).clamp(-1, 999);
    let result = tauri::async_runtime::spawn_blocking(move || {
        let mut engine = runtime.lock().map_err(|_| "Motor bloqueado".to_string())?;
        engine.load_model(model, gpu_layers)
    })
    .await
    .map_err(|error| format!("La tarea de carga terminó inesperadamente: {error}"))?;
    match &result {
        Ok(status) => {
            let _ = app_for_task.emit("llm://runtime-state", status);
        }
        Err(error) => {
            if let Ok(engine) = state.engine.lock() {
                let mut status = engine.status();
                status.error = Some(error.clone());
                let _ = app_for_task.emit("llm://runtime-state", status);
            }
        }
    }
    result
}

#[tauri::command]
pub async fn unload_model(app: AppHandle, state: State<'_, AppState>) -> Result<(), String> {
    let runtime = Arc::clone(&state.engine);
    {
        let mut engine = runtime.lock().map_err(|_| "Motor bloqueado".to_string())?;
        if engine.child.is_none() && engine.loaded_model.is_none() {
            return Ok(());
        }
        engine.runtime_state = RuntimeState::Stopping;
        engine.emit_runtime_state(&app);
    }
    let app_for_task = app.clone();
    let result = tauri::async_runtime::spawn_blocking(move || {
        let mut engine = runtime.lock().map_err(|_| "Motor bloqueado".to_string())?;
        engine.unload_model()
    })
    .await
    .map_err(|error| format!("La tarea de descarga terminó inesperadamente: {error}"))?;
    if result.is_ok() {
        if let Ok(engine) = state.engine.lock() {
            let _ = app_for_task.emit("llm://runtime-state", engine.status());
        }
    }
    result
}

#[tauri::command]
pub fn get_engine_status(state: State<'_, AppState>) -> Result<EngineStatus, String> {
    let engine = state
        .engine
        .lock()
        .map_err(|_| "Motor bloqueado".to_string())?;
    Ok(engine.get_status())
}

#[tauri::command]
pub fn get_engine_logs(state: State<'_, AppState>) -> Result<Vec<EngineLog>, String> {
    let engine = state
        .engine
        .lock()
        .map_err(|_| "Motor bloqueado".to_string())?;
    engine
        .logs
        .lock()
        .map(|logs| logs.clone())
        .map_err(|_| "Diagnóstico del motor bloqueado".to_string())
}

#[tauri::command]
pub fn clear_engine_logs(state: State<'_, AppState>) -> Result<(), String> {
    let engine = state
        .engine
        .lock()
        .map_err(|_| "Motor bloqueado".to_string())?;
    engine
        .logs
        .lock()
        .map_err(|_| "Diagnóstico del motor bloqueado".to_string())?
        .clear();
    Ok(())
}

#[tauri::command]
pub fn list_conversations(state: State<'_, AppState>) -> Result<Vec<ConversationRecord>, String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.list_conversations()
}

#[tauri::command]
pub fn save_conversation(
    conversation: ConversationRecord,
    state: State<'_, AppState>,
) -> Result<ConversationRecord, String> {
    if conversation.title.trim().is_empty() {
        return Err("El chat necesita un título".into());
    }
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.upsert_conversation(&conversation)?;
    Ok(conversation)
}

#[tauri::command]
pub fn delete_conversation(id: String, state: State<'_, AppState>) -> Result<(), String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.delete_conversation(&id)
}

#[tauri::command]
pub fn list_messages(
    conversation_id: String,
    state: State<'_, AppState>,
) -> Result<Vec<ChatMessageRecord>, String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.list_messages(&conversation_id)
}

#[tauri::command]
pub fn get_conversation_summary(
    conversation_id: String,
    state: State<'_, AppState>,
) -> Result<Option<ConversationSummaryRecord>, String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.get_conversation_summary(&conversation_id)
}

#[tauri::command]
pub fn save_conversation_summary(
    summary: ConversationSummaryRecord,
    state: State<'_, AppState>,
) -> Result<ConversationSummaryRecord, String> {
    if summary.conversation_id.trim().is_empty() {
        return Err("El resumen necesita una conversaciÃ³n".into());
    }
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.upsert_conversation_summary(&summary)?;
    Ok(summary)
}

#[tauri::command]
pub fn save_message(
    message: ChatMessageRecord,
    state: State<'_, AppState>,
) -> Result<ChatMessageRecord, String> {
    if message.content.trim().is_empty() {
        return Err("El mensaje no puede estar vacío".into());
    }
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.upsert_message(&message)?;
    Ok(message)
}

#[tauri::command]
pub fn delete_message(id: String, state: State<'_, AppState>) -> Result<(), String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.delete_message(&id)
}

#[tauri::command]
pub fn find_suspicious_messages(
    state: State<'_, AppState>,
) -> Result<Vec<SuspiciousMessageRecord>, String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.list_suspicious_messages()
}

#[tauri::command]
pub fn delete_suspicious_messages(
    ids: Vec<String>,
    state: State<'_, AppState>,
) -> Result<usize, String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.delete_suspicious_messages(&ids)
}

#[tauri::command]
pub fn branch_from_message(
    conversation_id: String,
    message_id: String,
    state: State<'_, AppState>,
) -> Result<ConversationRecord, String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    let parent = database
        .list_conversations()?
        .into_iter()
        .find(|conversation| conversation.id == conversation_id)
        .ok_or_else(|| "No se encontró la conversación".to_string())?;
    let messages = database.list_messages(&conversation_id)?;
    let Some(end) = messages.iter().position(|message| message.id == message_id) else {
        return Err("No se encontró el mensaje para crear la rama".into());
    };
    let branch_id = uuid::Uuid::new_v4().to_string();
    let branch = ConversationRecord {
        id: branch_id.clone(),
        character_id: parent.character_id.clone(),
        model_id: parent.model_id.clone(),
        persona_id: parent.persona_id.clone(),
        title: format!("{} · Rama", parent.title),
        pinned: false,
        archived: false,
        kind: "branch".into(),
        parent_conversation_id: Some(parent.id.clone()),
        branch_point_message_id: Some(message_id),
        last_message_preview: messages[end].content.chars().take(180).collect(),
        created_at: parent.created_at.clone(),
        updated_at: parent.updated_at.clone(),
    };
    database.upsert_conversation(&branch)?;
    for message in messages.into_iter().take(end + 1) {
        let mut copy = message.clone();
        copy.id = uuid::Uuid::new_v4().to_string();
        copy.conversation_id = branch_id.clone();
        copy.reply_to_id = None;
        let mut metadata = serde_json::from_str::<serde_json::Value>(&message.metadata_json)
            .unwrap_or_else(|_| serde_json::json!({}));
        if let Some(object) = metadata.as_object_mut() {
            object.insert(
                "sourceMessageId".into(),
                serde_json::Value::String(message.id.clone()),
            );
            object.insert("branch".into(), serde_json::Value::Bool(true));
        }
        copy.metadata_json = metadata.to_string();
        database.upsert_message(&copy)?;
    }
    Ok(branch)
}

#[tauri::command]
pub fn rewind_to_message(
    conversation_id: String,
    message_id: String,
    state: State<'_, AppState>,
) -> Result<(), String> {
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.delete_messages_after(&conversation_id, &message_id)
}

#[tauri::command]
pub async fn send_chat_message(
    app: AppHandle,
    state: State<'_, AppState>,
    request: GenerateRequest,
) -> Result<String, String> {
    if request.prompt.trim().is_empty() {
        return Err("El mensaje no puede estar vacío".into());
    }
    {
        let engine = state
            .engine
            .lock()
            .map_err(|_| "Motor bloqueado".to_string())?;
        if !matches!(engine.runtime_state, RuntimeState::Ready)
            || engine.child.is_none()
            || engine.server_port.is_none()
            || engine.loaded_model.is_none()
        {
            return Err(match &engine.runtime_state {
                RuntimeState::Starting | RuntimeState::LoadingModel => {
                    "El modelo local todavía se está cargando.".into()
                }
                RuntimeState::Error => engine
                    .error
                    .clone()
                    .unwrap_or_else(|| "El motor local está en estado de error.".into()),
                _ => "Carga un modelo GGUF antes de conversar.".into(),
            });
        }
    }
    let generation_id = request
        .generation_id
        .clone()
        .unwrap_or_else(|| Uuid::new_v4().to_string());
    let conversation_id = request.conversation_id.clone().unwrap_or_default();
    let message_id = request.message_id.clone().unwrap_or_default();
    let runtime = Arc::clone(&state.engine);
    let id_for_task = generation_id.clone();
    let id_for_error = generation_id.clone();
    let conversation_for_error = conversation_id.clone();
    let message_for_error = message_id.clone();
    let app_for_error = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        if let Err(error) = generate_stream(
            app,
            runtime,
            id_for_task,
            conversation_id,
            message_id,
            request.prompt,
            request.messages,
            request.character_name,
            request.user_name,
            request.max_output.unwrap_or(512),
            request.context.unwrap_or(8192),
            request.gpu_layers.unwrap_or(0),
        ) {
            let _ = app_for_error.emit(
                "llm://error",
                GenerationErrorEvent {
                    generation_id: id_for_error,
                    conversation_id: conversation_for_error,
                    message_id: message_for_error,
                    error,
                },
            );
        }
    });
    Ok(generation_id)
}

#[tauri::command]
pub fn stop_generation(state: State<'_, AppState>) -> Result<(), String> {
    let mut engine = state
        .engine
        .lock()
        .map_err(|_| "Motor bloqueado".to_string())?;
    engine.stop()
}

#[tauri::command]
pub fn run_benchmark(
    model_id: String,
    context: Option<u32>,
    gpu_layers: Option<i32>,
    state: State<'_, AppState>,
) -> Result<GenerationStats, String> {
    let model = {
        let mut database = state
            .database
            .lock()
            .map_err(|_| "SQLite bloqueado".to_string())?;
        database
            .get_model(&model_id)?
            .ok_or_else(|| "El modelo no está registrado".to_string())?
    };
    if !Path::new(&model.path).is_file() {
        return Err("El archivo GGUF ya no existe".into());
    }
    let executable =
        resolve_llama_cli().ok_or_else(|| "No se encontró llama-cli.exe".to_string())?;
    let context = context.unwrap_or(2048);
    let gpu_layers = gpu_layers.unwrap_or(0);
    let started = Instant::now();
    let mut child = native_process::command(executable)
        .args([
            "-m",
            &model.path,
            "-p",
            "Write a short sentence about local AI.",
            "-n",
            "32",
            "-c",
            &context.to_string(),
            "-ngl",
            &gpu_layers.to_string(),
            "--no-display-prompt",
        ])
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| format!("No se pudo iniciar el benchmark: {e}"))?;
    let stderr_task = child.stderr.take().map(|mut stderr| {
        std::thread::spawn(move || {
            let mut bytes = Vec::new();
            let _ = stderr.read_to_end(&mut bytes);
            String::from_utf8_lossy(&bytes).trim().to_string()
        })
    });
    let mut output = Vec::new();
    if let Some(mut stdout) = child.stdout.take() {
        stdout
            .read_to_end(&mut output)
            .map_err(|e| format!("No se pudo leer benchmark: {e}"))?;
    }
    let status = child
        .wait()
        .map_err(|e| format!("No se pudo finalizar benchmark: {e}"))?;
    let stderr = stderr_task
        .and_then(|task| task.join().ok())
        .unwrap_or_default();
    let _ = &stderr;
    if !status.success() {
        return Err(format!("llama-cli terminó con código {:?}", status.code()));
    }
    let elapsed_ms = started.elapsed().as_secs_f64() * 1000.0;
    let generated_tokens = String::from_utf8_lossy(&output).split_whitespace().count() as u64;
    let stats = GenerationStats {
        time_to_first_token_ms: Some(elapsed_ms),
        prompt_tokens_per_second: None,
        generation_tokens_per_second: (elapsed_ms > 0.0)
            .then(|| generated_tokens as f64 / (elapsed_ms / 1000.0)),
        generated_tokens,
    };
    let mut database = state
        .database
        .lock()
        .map_err(|_| "SQLite bloqueado".to_string())?;
    database.insert_benchmark(
        &model_id,
        i64::from(context),
        i64::from(gpu_layers),
        generated_tokens as i64,
        stats.generation_tokens_per_second.unwrap_or(0.0),
        stats.time_to_first_token_ms.unwrap_or(0.0),
    )?;
    Ok(stats)
}
