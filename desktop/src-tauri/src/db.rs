use crate::models::{
    CharacterRecord, ChatMessageRecord, ConversationRecord, ConversationSummaryRecord,
    ExploreFilterCatalog, ExploreFilterOption, GroupRecord, ModelRecord, ProviderRecord,
    RemoteCharacterRecord, RepositorySourceRecord, VoiceModelRecord, VoiceRepositoryRecord,
};
use directories::ProjectDirs;
use rusqlite::{params, Connection, OptionalExtension};
use std::collections::BTreeMap;
use std::fs;
use std::path::PathBuf;
use uuid::Uuid;

/// Returns true only for content that may be shown as a user/character turn.
/// Engine diagnostics and protocol markers are excluded before becoming
/// conversation previews.
pub fn is_real_chat_message(message: &ChatMessageRecord) -> bool {
    if message.content.trim().is_empty() || !matches!(message.role.as_str(), "user" | "assistant") {
        return false;
    }
    if let Some(source) = serde_json::from_str::<serde_json::Value>(&message.metadata_json)
        .ok()
        .and_then(|value| {
            value
                .get("source")
                .and_then(serde_json::Value::as_str)
                .map(str::to_ascii_lowercase)
        })
    {
        if matches!(
            source.as_str(),
            "runtime" | "engine" | "diagnostic" | "system"
        ) {
            return false;
        }
        if matches!(source.as_str(), "user" | "character" | "model") {
            return suspicious_message_reason(message).is_none();
        }
    }
    suspicious_message_reason(message).is_none()
}

/// Conservative detector for legacy, untyped assistant messages. Results are
/// presented for review; old history is never deleted automatically.
pub fn suspicious_message_reason(message: &ChatMessageRecord) -> Option<String> {
    if message.role != "assistant" || message.content.trim().is_empty() {
        return None;
    }
    let text = message.content.trim();
    let lower = text.to_ascii_lowercase();
    let markers = [
        ("loading model", "salida de carga del modelo"),
        ("llama.cpp", "salida técnica de llama.cpp"),
        ("llama-server", "salida técnica de llama-server"),
        ("available commands", "salida de comandos del motor"),
        ("system is thinking", "estado interno del motor"),
        ("el modelo está pensando", "estado interno del motor"),
        ("el modelo esta pensando", "estado interno del motor"),
        ("the model is thinking", "estado interno del motor"),
        ("el usuario está solicitando", "razonamiento del modelo"),
        ("el usuario esta solicitando", "razonamiento del modelo"),
        ("system:", "etiqueta de rol del prompt"),
        ("developer:", "etiqueta de rol del prompt"),
        ("thinking:", "bloque de razonamiento"),
        ("reasoning:", "bloque de razonamiento"),
        ("analysis:", "bloque de razonamiento"),
        ("prompt:", "metadatos de prompt"),
        ("generation:", "metadatos de generación"),
        ("gguf metadata", "metadatos GGUF"),
        ("[prompt:", "metadatos de prompt"),
        ("[generation:", "metadatos de generación"),
        ("<thinking>", "bloque de razonamiento"),
        ("<think>", "bloque de razonamiento"),
        ("</thinking>", "bloque de razonamiento"),
        ("</think>", "bloque de razonamiento"),
        ("<response>", "envoltorio de respuesta"),
        ("<|im_start|>", "token de protocolo"),
        ("<|assistant|>", "etiqueta de protocolo"),
        ("<|user|>", "etiqueta de protocolo"),
        ("{{user}}", "placeholder sin resolver"),
        ("{{ user }}", "placeholder sin resolver"),
        ("{{char}}", "placeholder sin resolver"),
        ("{{ char }}", "placeholder sin resolver"),
    ];
    if let Some((_, reason)) = markers.iter().find(|(marker, _)| lower.contains(marker)) {
        return Some((*reason).to_string());
    }
    let metadata = serde_json::from_str::<serde_json::Value>(&message.metadata_json).ok();
    if metadata
        .as_ref()
        .and_then(|value| value.get("source"))
        .and_then(serde_json::Value::as_str)
        .is_some()
    {
        return None;
    }
    if ["assistant:", "user:", "system:", "character:"]
        .iter()
        .any(|prefix| lower.starts_with(prefix))
    {
        return Some("respuesta con etiqueta de rol del prompt".to_string());
    }
    None
}

pub struct Database {
    connection: Connection,
    pub path: PathBuf,
}

impl Database {
    pub fn open() -> Result<Self, String> {
        let dirs = ProjectDirs::from("com", "LocalCharacter", "Local Character Desktop")
            .ok_or_else(|| "No se pudo resolver la carpeta de datos de Windows".to_string())?;
        let data_dir = dirs.data_local_dir();
        fs::create_dir_all(data_dir)
            .map_err(|e| format!("No se pudo crear la carpeta de datos: {e}"))?;
        let path = data_dir.join("local-character.sqlite3");
        let mut connection =
            Connection::open(&path).map_err(|e| format!("No se pudo abrir SQLite: {e}"))?;
        connection
            .pragma_update(None, "foreign_keys", "ON")
            .map_err(|e| format!("No se pudo activar foreign_keys: {e}"))?;
        connection
            .execute_batch(
                "BEGIN;
                 CREATE TABLE IF NOT EXISTS schema_migrations (
                     version INTEGER PRIMARY KEY,
                     applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                 );
                 CREATE TABLE IF NOT EXISTS models (
                     id TEXT PRIMARY KEY,
                     path TEXT NOT NULL UNIQUE,
                     name TEXT NOT NULL,
                     size_bytes INTEGER NOT NULL,
                     exists_now INTEGER NOT NULL,
                     architecture TEXT,
                     quantization TEXT,
                     parameter_count INTEGER,
                     context_length INTEGER,
                     chat_template TEXT,
                     backend TEXT,
                     created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                 );
                 CREATE TABLE IF NOT EXISTS characters (
                     id TEXT PRIMARY KEY,
                     name TEXT NOT NULL,
                     description TEXT NOT NULL DEFAULT '',
                     avatar_path TEXT,
                     card_json TEXT,
                     created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                 );
                 CREATE TABLE IF NOT EXISTS conversations (
                     id TEXT PRIMARY KEY,
                     character_id TEXT,
                     title TEXT NOT NULL,
                     created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                 );
                 CREATE TABLE IF NOT EXISTS messages (
                     id TEXT PRIMARY KEY,
                     conversation_id TEXT NOT NULL,
                     role TEXT NOT NULL,
                     content TEXT NOT NULL,
                     created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
                 );
                 CREATE TABLE IF NOT EXISTS memories (
                     id TEXT PRIMARY KEY,
                     character_id TEXT,
                     conversation_id TEXT,
                     content TEXT NOT NULL,
                     pinned INTEGER NOT NULL DEFAULT 0,
                     created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                 );
                 CREATE TABLE IF NOT EXISTS benchmarks (
                     id TEXT PRIMARY KEY,
                     model_id TEXT,
                     backend TEXT NOT NULL,
                     gpu_layers INTEGER,
                     context_length INTEGER,
                     batch INTEGER,
                     prompt_tokens_per_second REAL,
                     generation_tokens_per_second REAL,
                     time_to_first_token_ms REAL,
                     ram_bytes INTEGER,
                     vram_bytes INTEGER,
                     created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                 );
                 COMMIT;",
            )
            .map_err(|e| format!("No se pudo migrar SQLite: {e}"))?;
        apply_migrations(&mut connection)?;
        let mut database = Self { connection, path };
        let _ = database.repair_orphan_direct_conversations()?;
        Ok(database)
    }

    pub fn schema_version(&self) -> Result<i64, String> {
        self.connection
            .query_row(
                "SELECT COALESCE(MAX(version), 0) FROM schema_migrations",
                [],
                |row| row.get(0),
            )
            .map_err(|e| format!("No se pudo leer la versión de SQLite: {e}"))
    }

    pub fn list_models(&mut self) -> Result<Vec<ModelRecord>, String> {
        let mut statement = self
            .connection
            .prepare(
                "SELECT id, path, name, size_bytes, exists_now, architecture, quantization,
                        parameter_count, context_length, chat_template, backend,
                        created_at, updated_at FROM models ORDER BY updated_at DESC",
            )
            .map_err(|e| format!("No se pudo consultar modelos: {e}"))?;
        let rows = statement
            .query_map([], |row| {
                Ok(ModelRecord {
                    id: row.get(0)?,
                    path: row.get(1)?,
                    name: row.get(2)?,
                    size_bytes: row.get(3)?,
                    exists: row.get::<_, i64>(4)? != 0,
                    architecture: row.get(5)?,
                    quantization: row.get(6)?,
                    parameter_count: row.get(7)?,
                    context_length: row.get(8)?,
                    chat_template: row.get(9)?,
                    backend: row.get(10)?,
                    created_at: row.get(11)?,
                    updated_at: row.get(12)?,
                })
            })
            .map_err(|e| format!("No se pudo leer modelos: {e}"))?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|e| format!("No se pudo materializar modelos: {e}"))
    }

    pub fn upsert_model(&mut self, model: &ModelRecord) -> Result<(), String> {
        self.connection
            .execute(
                "INSERT INTO models (id, path, name, size_bytes, exists_now, architecture, quantization,
                                     parameter_count, context_length, chat_template, backend)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)
                 ON CONFLICT(path) DO UPDATE SET
                     name=excluded.name, size_bytes=excluded.size_bytes, exists_now=excluded.exists_now,
                     architecture=excluded.architecture, quantization=excluded.quantization,
                     parameter_count=excluded.parameter_count, context_length=excluded.context_length,
                     chat_template=excluded.chat_template, backend=excluded.backend,
                     updated_at=CURRENT_TIMESTAMP",
                params![
                    model.id,
                    model.path,
                    model.name,
                    model.size_bytes,
                    if model.exists { 1_i64 } else { 0_i64 },
                    model.architecture,
                    model.quantization,
                    model.parameter_count,
                    model.context_length,
                    model.chat_template,
                    model.backend,
                ],
            )
            .map_err(|e| format!("No se pudo guardar el modelo: {e}"))?;
        Ok(())
    }

    pub fn delete_model(&mut self, id: &str) -> Result<(), String> {
        self.connection
            .execute("DELETE FROM models WHERE id = ?1", [id])
            .map_err(|e| format!("No se pudo eliminar el modelo: {e}"))?;
        Ok(())
    }

    pub fn get_model(&mut self, id: &str) -> Result<Option<ModelRecord>, String> {
        self.connection
            .query_row(
                "SELECT id, path, name, size_bytes, exists_now, architecture, quantization,
                        parameter_count, context_length, chat_template, backend,
                        created_at, updated_at FROM models WHERE id = ?1",
                [id],
                |row| {
                    Ok(ModelRecord {
                        id: row.get(0)?,
                        path: row.get(1)?,
                        name: row.get(2)?,
                        size_bytes: row.get(3)?,
                        exists: row.get::<_, i64>(4)? != 0,
                        architecture: row.get(5)?,
                        quantization: row.get(6)?,
                        parameter_count: row.get(7)?,
                        context_length: row.get(8)?,
                        chat_template: row.get(9)?,
                        backend: row.get(10)?,
                        created_at: row.get(11)?,
                        updated_at: row.get(12)?,
                    })
                },
            )
            .optional()
            .map_err(|e| format!("No se pudo buscar el modelo: {e}"))
    }

    pub fn list_characters(&mut self) -> Result<Vec<CharacterRecord>, String> {
        let mut statement = self
            .connection
            .prepare(
                "SELECT id, name, description, avatar_path, card_json, created_at, updated_at
                 FROM characters ORDER BY updated_at DESC",
            )
            .map_err(|e| format!("No se pudo consultar personajes: {e}"))?;
        let rows = statement
            .query_map([], |row| {
                let card_json: Option<String> = row.get(4)?;
                let card = card_json
                    .as_deref()
                    .and_then(|value| serde_json::from_str::<serde_json::Value>(value).ok());
                Ok(CharacterRecord {
                    id: row.get(0)?,
                    name: row.get(1)?,
                    description: row.get(2)?,
                    personality: card
                        .as_ref()
                        .and_then(|value| value.get("personality"))
                        .and_then(serde_json::Value::as_str)
                        .unwrap_or_default()
                        .to_string(),
                    greeting: card
                        .as_ref()
                        .and_then(|value| value.get("greeting"))
                        .and_then(serde_json::Value::as_str)
                        .unwrap_or_default()
                        .to_string(),
                    scenario: card
                        .as_ref()
                        .and_then(|value| value.get("scenario"))
                        .and_then(serde_json::Value::as_str)
                        .unwrap_or_default()
                        .to_string(),
                    first_message: card
                        .as_ref()
                        .and_then(|value| value.get("firstMessage"))
                        .and_then(serde_json::Value::as_str)
                        .unwrap_or_default()
                        .to_string(),
                    example_messages: card
                        .as_ref()
                        .and_then(|value| value.get("exampleMessages"))
                        .and_then(serde_json::Value::as_str)
                        .unwrap_or_default()
                        .to_string(),
                    system_prompt: card
                        .as_ref()
                        .and_then(|value| value.get("systemPrompt"))
                        .and_then(serde_json::Value::as_str)
                        .unwrap_or_default()
                        .to_string(),
                    creator_notes: card
                        .as_ref()
                        .and_then(|value| value.get("creatorNotes"))
                        .and_then(serde_json::Value::as_str)
                        .unwrap_or_default()
                        .to_string(),
                    tags: card
                        .as_ref()
                        .and_then(|value| value.get("tags"))
                        .and_then(serde_json::Value::as_array)
                        .map(|items| {
                            items
                                .iter()
                                .filter_map(serde_json::Value::as_str)
                                .map(str::to_string)
                                .collect()
                        })
                        .unwrap_or_default(),
                    alternate_greetings: card
                        .as_ref()
                        .and_then(|value| value.get("alternateGreetings"))
                        .and_then(serde_json::Value::as_array)
                        .map(|items| {
                            items
                                .iter()
                                .filter_map(serde_json::Value::as_str)
                                .map(str::to_string)
                                .collect()
                        })
                        .unwrap_or_default(),
                    lore: card
                        .as_ref()
                        .and_then(|value| value.get("lore"))
                        .and_then(serde_json::Value::as_array)
                        .cloned()
                        .unwrap_or_default(),
                    avatar_path: row.get(3)?,
                    voice_id: card
                        .as_ref()
                        .and_then(|value| value.get("voiceId"))
                        .and_then(serde_json::Value::as_str)
                        .map(str::to_string),
                    created_at: row.get(5)?,
                    updated_at: row.get(6)?,
                })
            })
            .map_err(|e| format!("No se pudo leer personajes: {e}"))?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|e| format!("No se pudo materializar personajes: {e}"))
    }

    pub fn upsert_character(&mut self, character: &CharacterRecord) -> Result<(), String> {
        let card_json = serde_json::json!({
            "personality": character.personality,
            "greeting": character.greeting,
            "scenario": character.scenario,
            "firstMessage": character.first_message,
            "exampleMessages": character.example_messages,
            "systemPrompt": character.system_prompt,
            "creatorNotes": character.creator_notes,
            "tags": character.tags,
            "alternateGreetings": character.alternate_greetings,
            "lore": character.lore,
            "voiceId": character.voice_id,
        })
        .to_string();
        self.connection
            .execute(
                "INSERT INTO characters (id, name, description, avatar_path, card_json)
                 VALUES (?1, ?2, ?3, ?4, ?5)
                 ON CONFLICT(id) DO UPDATE SET
                    name=excluded.name, description=excluded.description,
                    avatar_path=excluded.avatar_path, card_json=excluded.card_json,
                    updated_at=CURRENT_TIMESTAMP",
                params![
                    character.id,
                    character.name,
                    character.description,
                    character.avatar_path,
                    card_json,
                ],
            )
            .map_err(|e| format!("No se pudo guardar el personaje: {e}"))?;
        Ok(())
    }

    pub fn delete_character(&mut self, id: &str) -> Result<(), String> {
        let transaction = self
            .connection
            .transaction()
            .map_err(|e| format!("No se pudo iniciar el borrado del personaje: {e}"))?;
        let conversation_ids = {
            let mut statement = transaction
                .prepare(
                    "SELECT id FROM conversations
                     WHERE COALESCE(NULLIF(lower(kind), ''), 'direct') = 'direct' AND character_id = ?1",
                )
                .map_err(|e| format!("No se pudieron localizar chats del personaje: {e}"))?;
            let ids = statement
                .query_map([id], |row| row.get::<_, String>(0))
                .map_err(|e| format!("No se pudieron leer chats del personaje: {e}"))?
                .collect::<Result<Vec<_>, _>>()
                .map_err(|e| format!("No se pudieron materializar chats del personaje: {e}"))?;
            ids
        };
        for conversation_id in conversation_ids {
            transaction
                .execute(
                    "DELETE FROM conversations WHERE id = ?1",
                    [&conversation_id],
                )
                .map_err(|e| format!("No se pudo eliminar un chat del personaje: {e}"))?;
        }
        transaction
            .execute(
                "DELETE FROM memories
                 WHERE character_id = ?1
                   AND NOT EXISTS (
                       SELECT 1 FROM conversations c
                       WHERE c.id = memories.conversation_id AND lower(c.kind) = 'group'
                   )",
                [id],
            )
            .map_err(|e| format!("No se pudieron limpiar memorias privadas: {e}"))?;
        for (table, column) in [
            ("character_lore", "character_id"),
            ("character_tags", "character_id"),
            ("relationships", "character_id"),
            ("pending_events", "character_id"),
            ("group_participants", "character_id"),
        ] {
            transaction
                .execute(&format!("DELETE FROM {table} WHERE {column} = ?1"), [id])
                .map_err(|e| format!("No se pudieron limpiar datos del personaje: {e}"))?;
        }
        transaction
            .execute("DELETE FROM characters WHERE id = ?1", [id])
            .map_err(|e| format!("No se pudo eliminar el personaje: {e}"))?;
        transaction
            .commit()
            .map_err(|e| format!("No se pudo confirmar el borrado del personaje: {e}"))?;
        Ok(())
    }

    /// Removes only direct conversations whose character reference is broken.
    /// Group history and unrelated conversations remain untouched.
    pub fn repair_orphan_direct_conversations(&mut self) -> Result<usize, String> {
        let transaction = self
            .connection
            .transaction()
            .map_err(|e| format!("No se pudo iniciar la reparación de chats: {e}"))?;
        let ids = {
            let mut statement = transaction
                .prepare(
                    "SELECT c.id FROM conversations c
                     WHERE COALESCE(NULLIF(lower(c.kind), ''), 'direct') = 'direct'
                       AND (c.character_id IS NULL OR NOT EXISTS (
                           SELECT 1 FROM characters ch WHERE ch.id = c.character_id
                       ))",
                )
                .map_err(|e| format!("No se pudieron detectar chats huérfanos: {e}"))?;
            let ids = statement
                .query_map([], |row| row.get::<_, String>(0))
                .map_err(|e| format!("No se pudieron leer chats huérfanos: {e}"))?
                .collect::<Result<Vec<_>, _>>()
                .map_err(|e| format!("No se pudieron materializar chats huérfanos: {e}"))?;
            ids
        };
        for id in &ids {
            transaction
                .execute("DELETE FROM conversations WHERE id = ?1", [id])
                .map_err(|e| format!("No se pudo reparar el chat huérfano: {e}"))?;
        }
        transaction
            .commit()
            .map_err(|e| format!("No se pudo confirmar la reparación de chats: {e}"))?;
        Ok(ids.len())
    }

    pub fn list_conversations(&mut self) -> Result<Vec<ConversationRecord>, String> {
        let mut statement = self
            .connection
            .prepare(
                "SELECT id, character_id, model_id, persona_id, title, pinned, archived, kind,
                        parent_conversation_id, branch_point_message_id, last_message_preview,
                        created_at, updated_at
                 FROM conversations
                 WHERE archived = 0
                   AND (COALESCE(NULLIF(lower(kind), ''), 'direct') <> 'direct' OR (character_id IS NOT NULL AND EXISTS (
                       SELECT 1 FROM characters ch WHERE ch.id = conversations.character_id
                   )))
                 ORDER BY pinned DESC, updated_at DESC",
            )
            .map_err(|e| format!("No se pudo consultar chats: {e}"))?;
        let rows = statement
            .query_map([], |row| {
                Ok(ConversationRecord {
                    id: row.get(0)?,
                    character_id: row.get(1)?,
                    model_id: row.get(2)?,
                    persona_id: row.get(3)?,
                    title: row.get(4)?,
                    pinned: row.get::<_, i64>(5)? != 0,
                    archived: row.get::<_, i64>(6)? != 0,
                    kind: row.get(7)?,
                    parent_conversation_id: row.get(8)?,
                    branch_point_message_id: row.get(9)?,
                    last_message_preview: row.get(10)?,
                    created_at: row.get(11)?,
                    updated_at: row.get(12)?,
                })
            })
            .map_err(|e| format!("No se pudo leer chats: {e}"))?;
        let mut conversations = rows
            .collect::<Result<Vec<_>, _>>()
            .map_err(|e| format!("No se pudo materializar chats: {e}"))?;
        drop(statement);
        for conversation in &mut conversations {
            let messages = self.list_messages(&conversation.id)?;
            conversation.last_message_preview = messages
                .iter()
                .rev()
                .find(|message| is_real_chat_message(message))
                .map(|message| message.content.chars().take(180).collect())
                .unwrap_or_default();
        }
        Ok(conversations)
    }

    pub fn upsert_conversation(&mut self, conversation: &ConversationRecord) -> Result<(), String> {
        self.connection
            .execute(
                "INSERT INTO conversations
                 (id, character_id, model_id, persona_id, title, pinned, archived, kind,
                  parent_conversation_id, branch_point_message_id, last_message_preview)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)
                 ON CONFLICT(id) DO UPDATE SET
                  character_id=excluded.character_id, model_id=excluded.model_id,
                  persona_id=excluded.persona_id, title=excluded.title,
                  pinned=excluded.pinned, archived=excluded.archived, kind=excluded.kind,
                  parent_conversation_id=excluded.parent_conversation_id,
                  branch_point_message_id=excluded.branch_point_message_id,
                  last_message_preview=excluded.last_message_preview,
                  updated_at=CURRENT_TIMESTAMP",
                params![
                    conversation.id,
                    conversation.character_id,
                    conversation.model_id,
                    conversation.persona_id,
                    conversation.title,
                    if conversation.pinned { 1_i64 } else { 0_i64 },
                    if conversation.archived { 1_i64 } else { 0_i64 },
                    conversation.kind,
                    conversation.parent_conversation_id,
                    conversation.branch_point_message_id,
                    conversation.last_message_preview,
                ],
            )
            .map_err(|e| format!("No se pudo guardar el chat: {e}"))?;
        Ok(())
    }

    pub fn delete_conversation(&mut self, id: &str) -> Result<(), String> {
        self.connection
            .execute("DELETE FROM conversations WHERE id = ?1", [id])
            .map_err(|e| format!("No se pudo eliminar el chat: {e}"))?;
        Ok(())
    }

    pub fn list_messages(
        &mut self,
        conversation_id: &str,
    ) -> Result<Vec<ChatMessageRecord>, String> {
        let mut statement = self
            .connection
            .prepare(
                "SELECT id, conversation_id, role, content, reply_to_id, pinned,
                        metadata_json, created_at, edited_at
                 FROM messages WHERE conversation_id = ?1 ORDER BY created_at ASC, id ASC LIMIT 5000",
            )
            .map_err(|e| format!("No se pudieron consultar mensajes: {e}"))?;
        let rows = statement
            .query_map([conversation_id], |row| {
                Ok(ChatMessageRecord {
                    id: row.get(0)?,
                    conversation_id: row.get(1)?,
                    role: row.get(2)?,
                    content: row.get(3)?,
                    reply_to_id: row.get(4)?,
                    pinned: row.get::<_, i64>(5)? != 0,
                    metadata_json: row.get(6)?,
                    created_at: row.get(7)?,
                    edited_at: row.get(8)?,
                })
            })
            .map_err(|e| format!("No se pudieron leer mensajes: {e}"))?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|e| format!("No se pudieron materializar mensajes: {e}"))
    }

    pub fn upsert_message(&mut self, message: &ChatMessageRecord) -> Result<(), String> {
        self.connection
            .execute(
                "INSERT INTO messages
                 (id, conversation_id, role, content, reply_to_id, pinned, metadata_json, created_at, edited_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, COALESCE(NULLIF(?8, ''), CURRENT_TIMESTAMP), ?9)
                 ON CONFLICT(id) DO UPDATE SET
                  role=excluded.role, content=excluded.content, reply_to_id=excluded.reply_to_id,
                  pinned=excluded.pinned, metadata_json=excluded.metadata_json, edited_at=excluded.edited_at",
                params![
                    message.id,
                    message.conversation_id,
                    message.role,
                    message.content,
                    message.reply_to_id,
                    if message.pinned { 1_i64 } else { 0_i64 },
                    message.metadata_json,
                    message.created_at,
                    message.edited_at,
                ],
            )
            .map_err(|e| format!("No se pudo guardar el mensaje: {e}"))?;
        if message.edited_at.is_some() {
            self.delete_conversation_summary(&message.conversation_id)?;
        }
        if is_real_chat_message(message) {
            self.connection
                .execute(
                    "UPDATE conversations SET last_message_preview = ?2, updated_at = CURRENT_TIMESTAMP WHERE id = ?1",
                    params![message.conversation_id, message.content.chars().take(180).collect::<String>()],
                )
                .map_err(|e| format!("No se pudo actualizar el resumen del chat: {e}"))?;
        }
        Ok(())
    }

    pub fn get_conversation_summary(
        &mut self,
        conversation_id: &str,
    ) -> Result<Option<ConversationSummaryRecord>, String> {
        self.connection
            .query_row(
                "SELECT conversation_id, summary, summary_until_message_id, message_count,
                        token_count, updated_at
                 FROM conversation_summaries WHERE conversation_id = ?1",
                [conversation_id],
                |row| {
                    Ok(ConversationSummaryRecord {
                        conversation_id: row.get(0)?,
                        summary: row.get(1)?,
                        summary_until_message_id: row.get(2)?,
                        message_count: row.get(3)?,
                        token_count: row.get(4)?,
                        updated_at: row.get(5)?,
                    })
                },
            )
            .optional()
            .map_err(|error| format!("No se pudo leer el resumen del chat: {error}"))
    }

    pub fn upsert_conversation_summary(
        &mut self,
        summary: &ConversationSummaryRecord,
    ) -> Result<(), String> {
        self.connection
            .execute(
                "INSERT INTO conversation_summaries
                 (conversation_id, summary, summary_until_message_id, message_count, token_count)
                 VALUES (?1, ?2, ?3, ?4, ?5)
                 ON CONFLICT(conversation_id) DO UPDATE SET
                  summary=excluded.summary,
                  summary_until_message_id=excluded.summary_until_message_id,
                  message_count=excluded.message_count,
                  token_count=excluded.token_count,
                  updated_at=CURRENT_TIMESTAMP",
                params![
                    summary.conversation_id,
                    summary.summary,
                    summary.summary_until_message_id,
                    summary.message_count,
                    summary.token_count,
                ],
            )
            .map_err(|error| format!("No se pudo guardar el resumen del chat: {error}"))?;
        Ok(())
    }

    pub fn delete_conversation_summary(&mut self, conversation_id: &str) -> Result<(), String> {
        self.connection
            .execute(
                "DELETE FROM conversation_summaries WHERE conversation_id = ?1",
                [conversation_id],
            )
            .map_err(|error| format!("No se pudo invalidar el resumen del chat: {error}"))?;
        Ok(())
    }

    pub fn delete_message(&mut self, id: &str) -> Result<(), String> {
        let conversation_id: Option<String> = self
            .connection
            .query_row(
                "SELECT conversation_id FROM messages WHERE id = ?1",
                [id],
                |row| row.get(0),
            )
            .optional()
            .map_err(|e| format!("No se pudo encontrar el mensaje: {e}"))?;
        self.connection
            .execute("DELETE FROM messages WHERE id = ?1", [id])
            .map_err(|e| format!("No se pudo eliminar el mensaje: {e}"))?;
        if let Some(conversation_id) = conversation_id {
            self.delete_conversation_summary(&conversation_id)?;
            self.refresh_conversation_preview(&conversation_id)?;
        }
        Ok(())
    }

    fn refresh_conversation_preview(&mut self, conversation_id: &str) -> Result<(), String> {
        let preview = self
            .list_messages(conversation_id)?
            .iter()
            .rev()
            .find(|message| is_real_chat_message(message))
            .map(|message| message.content.chars().take(180).collect::<String>())
            .unwrap_or_default();
        self.connection
            .execute(
                "UPDATE conversations SET last_message_preview = ?2 WHERE id = ?1",
                params![conversation_id, preview],
            )
            .map_err(|e| format!("No se pudo reparar el resumen del chat: {e}"))?;
        Ok(())
    }

    pub fn list_suspicious_messages(
        &mut self,
    ) -> Result<Vec<crate::models::SuspiciousMessageRecord>, String> {
        let mut statement = self
            .connection
            .prepare("SELECT id FROM conversations")
            .map_err(|e| format!("No se pudieron consultar chats: {e}"))?;
        let conversation_ids = statement
            .query_map([], |row| row.get::<_, String>(0))
            .map_err(|e| format!("No se pudieron leer chats: {e}"))?
            .collect::<Result<Vec<String>, _>>()
            .map_err(|e| format!("No se pudieron materializar chats: {e}"))?;
        drop(statement);
        let mut result = Vec::new();
        for conversation_id in conversation_ids {
            for message in self.list_messages(&conversation_id)? {
                if let Some(reason) = suspicious_message_reason(&message) {
                    result.push(crate::models::SuspiciousMessageRecord { message, reason });
                }
            }
        }
        Ok(result)
    }

    pub fn delete_suspicious_messages(&mut self, ids: &[String]) -> Result<usize, String> {
        let mut deleted = 0;
        for id in ids {
            let message = self
                .connection
                .query_row(
                    "SELECT id, conversation_id, role, content, reply_to_id, pinned, metadata_json, created_at, edited_at FROM messages WHERE id = ?1",
                    [id],
                    |row| {
                        Ok(ChatMessageRecord {
                            id: row.get(0)?, conversation_id: row.get(1)?, role: row.get(2)?, content: row.get(3)?,
                            reply_to_id: row.get(4)?, pinned: row.get::<_, i64>(5)? != 0, metadata_json: row.get(6)?,
                            created_at: row.get(7)?, edited_at: row.get(8)?,
                        })
                    },
                )
                .optional()
                .map_err(|e| format!("No se pudo revisar el mensaje: {e}"))?;
            if let Some(message) = message {
                if suspicious_message_reason(&message).is_some() {
                    self.delete_message(id)?;
                    deleted += 1;
                }
            }
        }
        Ok(deleted)
    }

    pub fn delete_messages_after(
        &mut self,
        conversation_id: &str,
        message_id: &str,
    ) -> Result<(), String> {
        let messages = self.list_messages(conversation_id)?;
        let Some(index) = messages.iter().position(|message| message.id == message_id) else {
            return Err("No se encontró el mensaje indicado".into());
        };
        for message in messages.into_iter().skip(index + 1) {
            self.delete_message(&message.id)?;
        }
        Ok(())
    }

    pub fn list_groups(&mut self) -> Result<Vec<GroupRecord>, String> {
        let mut statement = self
            .connection
            .prepare("SELECT id, name, description, avatar_path, system_prompt, created_at, updated_at FROM groups ORDER BY updated_at DESC")
            .map_err(|e| format!("No se pudieron consultar grupos: {e}"))?;
        let rows = statement
            .query_map([], |row| {
                let id: String = row.get(0)?;
                let mut members = self
                    .connection
                    .prepare("SELECT character_id FROM group_participants WHERE group_id = ?1 ORDER BY sort_order")
                    .map_err(|error| rusqlite::Error::ToSqlConversionFailure(Box::new(error)))?;
                let participant_ids = members
                    .query_map([&id], |member| member.get(0))?
                    .collect::<Result<Vec<String>, _>>()?;
                Ok(GroupRecord {
                    id,
                    name: row.get(1)?,
                    description: row.get(2)?,
                    avatar_path: row.get(3)?,
                    system_prompt: row.get(4)?,
                    participant_ids,
                    created_at: row.get(5)?,
                    updated_at: row.get(6)?,
                })
            })
            .map_err(|e| format!("No se pudieron leer grupos: {e}"))?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|e| format!("No se pudieron materializar grupos: {e}"))
    }

    pub fn upsert_group(&mut self, group: &GroupRecord) -> Result<(), String> {
        let transaction = self
            .connection
            .transaction()
            .map_err(|e| format!("No se pudo iniciar grupo: {e}"))?;
        transaction.execute(
            "INSERT INTO groups (id, name, description, avatar_path, system_prompt) VALUES (?1, ?2, ?3, ?4, ?5)
             ON CONFLICT(id) DO UPDATE SET name=excluded.name, description=excluded.description, avatar_path=excluded.avatar_path, system_prompt=excluded.system_prompt, updated_at=CURRENT_TIMESTAMP",
            params![group.id, group.name, group.description, group.avatar_path, group.system_prompt],
        ).map_err(|e| format!("No se pudo guardar grupo: {e}"))?;
        transaction
            .execute(
                "DELETE FROM group_participants WHERE group_id = ?1",
                [&group.id],
            )
            .map_err(|e| format!("No se pudieron actualizar participantes: {e}"))?;
        for (index, participant_id) in group.participant_ids.iter().enumerate() {
            transaction.execute("INSERT OR IGNORE INTO group_participants (group_id, character_id, sort_order) VALUES (?1, ?2, ?3)", params![group.id, participant_id, index as i64]).map_err(|e| format!("No se pudo añadir participante: {e}"))?;
        }
        transaction
            .commit()
            .map_err(|e| format!("No se pudo confirmar grupo: {e}"))?;
        Ok(())
    }

    pub fn delete_group(&mut self, id: &str) -> Result<(), String> {
        self.connection
            .execute("DELETE FROM groups WHERE id = ?1", [id])
            .map_err(|e| format!("No se pudo eliminar grupo: {e}"))?;
        Ok(())
    }

    pub fn list_providers(&mut self) -> Result<Vec<ProviderRecord>, String> {
        let mut statement = self.connection.prepare("SELECT id, kind, name, endpoint, api_key, model_name, available_models_json, enabled, created_at, updated_at FROM providers ORDER BY updated_at DESC").map_err(|e| format!("No se pudieron consultar APIs: {e}"))?;
        let rows = statement
            .query_map([], |row| {
                Ok(ProviderRecord {
                    id: row.get(0)?,
                    kind: row.get(1)?,
                    name: row.get(2)?,
                    endpoint: row.get(3)?,
                    api_key: row.get(4)?,
                    model_name: row.get(5)?,
                    available_models: row
                        .get::<_, Option<String>>(6)?
                        .and_then(|json| serde_json::from_str(&json).ok())
                        .unwrap_or_default(),
                    enabled: row.get::<_, i64>(7)? != 0,
                    created_at: row.get(8)?,
                    updated_at: row.get(9)?,
                })
            })
            .map_err(|e| format!("No se pudieron leer APIs: {e}"))?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|e| format!("No se pudieron materializar APIs: {e}"))
    }

    pub fn upsert_provider(&mut self, provider: &ProviderRecord) -> Result<(), String> {
        self.connection.execute(
            "INSERT INTO providers (id, kind, name, endpoint, api_key, model_name, available_models_json, enabled) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
             ON CONFLICT(id) DO UPDATE SET kind=excluded.kind, name=excluded.name, endpoint=excluded.endpoint, api_key=excluded.api_key, model_name=excluded.model_name, available_models_json=excluded.available_models_json, enabled=excluded.enabled, updated_at=CURRENT_TIMESTAMP",
            params![provider.id, provider.kind, provider.name, provider.endpoint, provider.api_key, provider.model_name, serde_json::to_string(&provider.available_models).unwrap_or_else(|_| "[]".to_string()), if provider.enabled {1_i64} else {0_i64}],
        ).map_err(|e| format!("No se pudo guardar API: {e}"))?;
        Ok(())
    }

    pub fn delete_provider(&mut self, id: &str) -> Result<(), String> {
        self.connection
            .execute("DELETE FROM providers WHERE id = ?1", [id])
            .map_err(|e| format!("No se pudo eliminar API: {e}"))?;
        Ok(())
    }

    pub fn list_voice_repositories(&mut self) -> Result<Vec<VoiceRepositoryRecord>, String> {
        let mut statement = self
            .connection
            .prepare(
                "SELECT id, name, endpoint, language, enabled, updated_at
                 FROM voice_repositories ORDER BY updated_at DESC",
            )
            .map_err(|error| format!("No se pudieron consultar repositorios TTS: {error}"))?;
        let rows = statement
            .query_map([], |row| {
                Ok(VoiceRepositoryRecord {
                    id: row.get(0)?,
                    name: row.get(1)?,
                    endpoint: row.get(2)?,
                    language: row.get(3)?,
                    enabled: row.get::<_, i64>(4)? != 0,
                    updated_at: row.get(5)?,
                })
            })
            .map_err(|error| format!("No se pudieron leer repositorios TTS: {error}"))?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|error| format!("No se pudieron materializar repositorios TTS: {error}"))
    }

    pub fn list_voice_models(&mut self) -> Result<Vec<VoiceModelRecord>, String> {
        let mut statement = self
            .connection
            .prepare(
                "SELECT m.id, m.repository_id, m.name, m.language, m.path, m.metadata_json
                 FROM voice_models m
                 LEFT JOIN voice_repositories r ON r.id = m.repository_id
                 WHERE m.repository_id IS NULL OR r.enabled = 1
                 ORDER BY m.name COLLATE NOCASE ASC",
            )
            .map_err(|error| format!("No se pudieron consultar voces TTS: {error}"))?;
        let rows = statement
            .query_map([], |row| {
                Ok(VoiceModelRecord {
                    id: row.get(0)?,
                    repository_id: row.get(1)?,
                    name: row.get(2)?,
                    language: row.get(3)?,
                    path: row.get(4)?,
                    metadata_json: row.get(5)?,
                })
            })
            .map_err(|error| format!("No se pudieron leer voces TTS: {error}"))?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|error| format!("No se pudieron materializar voces TTS: {error}"))
    }

    pub fn voice_repository_by_endpoint(
        &mut self,
        endpoint: &str,
    ) -> Result<Option<VoiceRepositoryRecord>, String> {
        self.connection
            .query_row(
                "SELECT id, name, endpoint, language, enabled, updated_at
                 FROM voice_repositories WHERE endpoint = ?1",
                [endpoint],
                |row| {
                    Ok(VoiceRepositoryRecord {
                        id: row.get(0)?,
                        name: row.get(1)?,
                        endpoint: row.get(2)?,
                        language: row.get(3)?,
                        enabled: row.get::<_, i64>(4)? != 0,
                        updated_at: row.get(5)?,
                    })
                },
            )
            .optional()
            .map_err(|error| format!("No se pudo buscar el repositorio TTS: {error}"))
    }

    pub fn replace_voice_repository(
        &mut self,
        repository: &VoiceRepositoryRecord,
        voices: &[VoiceModelRecord],
    ) -> Result<(), String> {
        let transaction = self
            .connection
            .transaction()
            .map_err(|error| format!("No se pudo iniciar sincronización TTS: {error}"))?;
        transaction
            .execute(
                "INSERT INTO voice_repositories (id, name, endpoint, language, enabled)
                 VALUES (?1, ?2, ?3, ?4, ?5)
                 ON CONFLICT(id) DO UPDATE SET name=excluded.name, endpoint=excluded.endpoint,
                 language=excluded.language, enabled=excluded.enabled, updated_at=CURRENT_TIMESTAMP",
                params![
                    repository.id,
                    repository.name,
                    repository.endpoint,
                    repository.language,
                    if repository.enabled { 1_i64 } else { 0_i64 }
                ],
            )
            .map_err(|error| format!("No se pudo guardar el repositorio TTS: {error}"))?;
        transaction
            .execute(
                "DELETE FROM voice_models WHERE repository_id = ?1",
                [&repository.id],
            )
            .map_err(|error| format!("No se pudieron actualizar las voces TTS: {error}"))?;
        for voice in voices {
            transaction
                .execute(
                    "INSERT INTO voice_models (id, repository_id, name, language, path, metadata_json)
                     VALUES (?1, ?2, ?3, ?4, ?5, ?6)
                     ON CONFLICT(id) DO UPDATE SET repository_id=excluded.repository_id,
                     name=excluded.name, language=excluded.language, path=excluded.path,
                     metadata_json=excluded.metadata_json",
                    params![
                        voice.id,
                        repository.id,
                        voice.name,
                        voice.language,
                        voice.path,
                        voice.metadata_json
                    ],
                )
                .map_err(|error| format!("No se pudo guardar una voz TTS: {error}"))?;
        }
        transaction
            .commit()
            .map_err(|error| format!("No se pudo confirmar sincronización TTS: {error}"))?;
        Ok(())
    }

    pub fn delete_voice_repository(&mut self, id: &str) -> Result<(), String> {
        self.connection
            .execute("DELETE FROM voice_models WHERE repository_id = ?1", [id])
            .map_err(|error| format!("No se pudieron eliminar las voces TTS: {error}"))?;
        self.connection
            .execute("DELETE FROM voice_repositories WHERE id = ?1", [id])
            .map_err(|error| format!("No se pudo eliminar el repositorio TTS: {error}"))?;
        Ok(())
    }

    pub fn list_repositories(&mut self) -> Result<Vec<RepositorySourceRecord>, String> {
        let mut statement = self.connection.prepare(
            "SELECT id, provider_id, name, url, enabled, status, status_message, last_sync_at, updated_at
             FROM character_repositories ORDER BY updated_at DESC",
        ).map_err(|error| format!("No se pudieron consultar fuentes de personajes: {error}"))?;
        let rows = statement
            .query_map([], |row| {
                Ok(RepositorySourceRecord {
                    id: row.get(0)?,
                    provider_id: row.get(1)?,
                    name: row.get(2)?,
                    url: row.get(3)?,
                    enabled: row.get::<_, i64>(4)? != 0,
                    status: row.get(5)?,
                    status_message: row.get(6)?,
                    last_sync_at: row.get(7)?,
                    updated_at: row.get(8)?,
                })
            })
            .map_err(|error| format!("No se pudieron leer fuentes de personajes: {error}"))?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|error| format!("No se pudieron materializar fuentes: {error}"))
    }

    pub fn upsert_repository(&mut self, repository: &RepositorySourceRecord) -> Result<(), String> {
        self.connection.execute(
            "INSERT INTO character_repositories (id, provider_id, name, url, enabled, status, status_message, last_sync_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
             ON CONFLICT(id) DO UPDATE SET provider_id=excluded.provider_id, name=excluded.name,
             url=excluded.url, enabled=excluded.enabled, status=excluded.status,
             status_message=excluded.status_message, last_sync_at=excluded.last_sync_at,
             updated_at=CURRENT_TIMESTAMP",
            params![repository.id, repository.provider_id, repository.name, repository.url,
                if repository.enabled { 1_i64 } else { 0_i64 }, repository.status,
                repository.status_message, repository.last_sync_at],
        ).map_err(|error| format!("No se pudo guardar la fuente: {error}"))?;
        Ok(())
    }

    pub fn repository_by_url(
        &mut self,
        url: &str,
    ) -> Result<Option<RepositorySourceRecord>, String> {
        self.connection.query_row(
            "SELECT id, provider_id, name, url, enabled, status, status_message, last_sync_at, updated_at FROM character_repositories WHERE url = ?1",
            [url],
            |row| Ok(RepositorySourceRecord {
                id: row.get(0)?, provider_id: row.get(1)?, name: row.get(2)?, url: row.get(3)?,
                enabled: row.get::<_, i64>(4)? != 0, status: row.get(5)?, status_message: row.get(6)?,
                last_sync_at: row.get(7)?, updated_at: row.get(8)?,
            }),
        ).optional().map_err(|error| format!("No se pudo buscar la fuente: {error}"))
    }

    pub fn delete_repository(&mut self, id: &str) -> Result<(), String> {
        self.connection
            .execute("DELETE FROM character_repositories WHERE id = ?1", [id])
            .map_err(|error| format!("No se pudo eliminar la fuente: {error}"))?;
        Ok(())
    }

    pub fn set_repository_enabled(&mut self, id: &str, enabled: bool) -> Result<(), String> {
        self.connection.execute(
            "UPDATE character_repositories SET enabled = ?2, updated_at = CURRENT_TIMESTAMP WHERE id = ?1",
            params![id, if enabled { 1_i64 } else { 0_i64 }],
        ).map_err(|error| format!("No se pudo actualizar el estado de la fuente: {error}"))?;
        Ok(())
    }

    pub fn replace_remote_characters(
        &mut self,
        source_id: &str,
        items: &[RemoteCharacterRecord],
    ) -> Result<(), String> {
        let transaction = self
            .connection
            .transaction()
            .map_err(|error| format!("No se pudo iniciar sincronización: {error}"))?;
        transaction
            .execute(
                "DELETE FROM remote_characters WHERE source_id = ?1",
                [source_id],
            )
            .map_err(|error| format!("No se pudo actualizar el catálogo anterior: {error}"))?;
        for item in items {
            transaction.execute(
                "INSERT INTO remote_characters
                 (provider_id, remote_id, source_id, name, description, avatar_url, author, tags_json, categories_json,
                  language, is_nsfw, download_count, remote_updated_at, source_url, card_url, raw_json, installed_character_id, cached_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, CURRENT_TIMESTAMP)
                 ON CONFLICT(provider_id, remote_id) DO UPDATE SET source_id=excluded.source_id,
                 name=excluded.name, description=excluded.description, avatar_url=excluded.avatar_url,
                 author=excluded.author, tags_json=excluded.tags_json, categories_json=excluded.categories_json, language=excluded.language,
                 is_nsfw=excluded.is_nsfw, download_count=excluded.download_count,
                 remote_updated_at=excluded.remote_updated_at, source_url=excluded.source_url,
                 card_url=excluded.card_url, raw_json=excluded.raw_json,
                 installed_character_id=COALESCE(excluded.installed_character_id, remote_characters.installed_character_id),
                 cached_at=CURRENT_TIMESTAMP",
                params![item.provider_id, item.remote_id, item.source_id, item.name, item.description,
                    item.avatar_url, item.author, serde_json::to_string(&item.tags).unwrap_or_else(|_| "[]".into()),
                    serde_json::to_string(&item.categories).unwrap_or_else(|_| "[]".into()), item.language,
                    if item.is_nsfw { 1_i64 } else { 0_i64 }, item.download_count,
                    item.updated_at, item.source_url, item.card_url, item.raw_json, item.installed_character_id],
            ).map_err(|error| format!("No se pudo guardar un personaje remoto: {error}"))?;
        }
        transaction
            .commit()
            .map_err(|error| format!("No se pudo confirmar sincronización: {error}"))?;
        Ok(())
    }

    pub fn list_remote_characters(&mut self) -> Result<Vec<RemoteCharacterRecord>, String> {
        let mut statement = self.connection.prepare(
            "SELECT provider_id, remote_id, source_id, name, description, avatar_url, author, tags_json, categories_json,
                    language, is_nsfw, download_count, remote_updated_at, source_url, card_url, raw_json,
                    installed_character_id, cached_at FROM remote_characters
             WHERE EXISTS (SELECT 1 FROM character_repositories r WHERE r.id = remote_characters.source_id AND r.enabled = 1)
             ORDER BY cached_at DESC, name COLLATE NOCASE ASC",
        ).map_err(|error| format!("No se pudo consultar catálogo remoto: {error}"))?;
        let rows = statement
            .query_map([], |row| {
                Ok(RemoteCharacterRecord {
                    provider_id: row.get(0)?,
                    remote_id: row.get(1)?,
                    source_id: row.get(2)?,
                    name: row.get(3)?,
                    description: row.get(4)?,
                    avatar_url: row.get(5)?,
                    author: row.get(6)?,
                    tags: row
                        .get::<_, Option<String>>(7)?
                        .and_then(|value| serde_json::from_str(&value).ok())
                        .unwrap_or_default(),
                    categories: row
                        .get::<_, Option<String>>(8)?
                        .and_then(|value| serde_json::from_str(&value).ok())
                        .unwrap_or_default(),
                    language: row.get(9)?,
                    is_nsfw: row.get::<_, i64>(10)? != 0,
                    download_count: row.get(11)?,
                    updated_at: row.get(12)?,
                    source_url: row.get(13)?,
                    card_url: row.get(14)?,
                    raw_json: row.get(15)?,
                    installed_character_id: row.get(16)?,
                    cached_at: row.get(17)?,
                })
            })
            .map_err(|error| format!("No se pudo leer catálogo remoto: {error}"))?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|error| format!("No se pudo materializar catálogo remoto: {error}"))
    }

    pub fn explore_filter_catalog(&mut self) -> Result<ExploreFilterCatalog, String> {
        let characters = self.list_remote_characters()?;
        let repositories = self.list_repositories()?;
        let mut sources = BTreeMap::<String, ExploreFilterOption>::new();
        let mut languages = BTreeMap::<String, ExploreFilterOption>::new();
        let mut tags = BTreeMap::<String, ExploreFilterOption>::new();
        let mut categories = BTreeMap::<String, ExploreFilterOption>::new();

        for repository in repositories.into_iter().filter(|item| item.enabled) {
            sources
                .entry(repository.id.clone())
                .or_insert_with(|| ExploreFilterOption {
                    id: repository.id,
                    label: repository.name,
                    count: 0,
                });
        }
        for character in characters {
            if let Some(source) = sources.get_mut(&character.source_id) {
                source.count += 1;
            }
            if let Some(language) = character.language.filter(|value| !value.trim().is_empty()) {
                let entry =
                    languages
                        .entry(language.clone())
                        .or_insert_with(|| ExploreFilterOption {
                            id: language.clone(),
                            label: language.to_uppercase(),
                            count: 0,
                        });
                entry.count += 1;
            }
            for tag in character.tags {
                if tag.trim().is_empty() {
                    continue;
                }
                let key = tag.to_ascii_lowercase();
                let entry = tags
                    .entry(key.clone())
                    .or_insert_with(|| ExploreFilterOption {
                        id: key,
                        label: tag.clone(),
                        count: 0,
                    });
                entry.count += 1;
            }
            for category in character.categories {
                if category.trim().is_empty() {
                    continue;
                }
                let key = category.to_ascii_lowercase();
                let entry = categories
                    .entry(key.clone())
                    .or_insert_with(|| ExploreFilterOption {
                        id: key,
                        label: category.clone(),
                        count: 0,
                    });
                entry.count += 1;
            }
        }
        Ok(ExploreFilterCatalog {
            sources: sources
                .into_values()
                .filter(|item| item.count > 0)
                .collect(),
            languages: languages
                .into_values()
                .filter(|item| item.count > 0)
                .collect(),
            tags: tags.into_values().filter(|item| item.count > 0).collect(),
            categories: categories
                .into_values()
                .filter(|item| item.count > 0)
                .collect(),
        })
    }

    pub fn get_remote_character(
        &mut self,
        provider_id: &str,
        remote_id: &str,
    ) -> Result<Option<RemoteCharacterRecord>, String> {
        self.connection.query_row(
            "SELECT provider_id, remote_id, source_id, name, description, avatar_url, author, tags_json, categories_json,
                    language, is_nsfw, download_count, remote_updated_at, source_url, card_url, raw_json,
                    installed_character_id, cached_at FROM remote_characters WHERE provider_id = ?1 AND remote_id = ?2",
            params![provider_id, remote_id], |row| Ok(RemoteCharacterRecord {
                provider_id: row.get(0)?, remote_id: row.get(1)?, source_id: row.get(2)?, name: row.get(3)?,
                description: row.get(4)?, avatar_url: row.get(5)?, author: row.get(6)?,
                tags: row.get::<_, Option<String>>(7)?.and_then(|value| serde_json::from_str(&value).ok()).unwrap_or_default(),
                categories: row.get::<_, Option<String>>(8)?.and_then(|value| serde_json::from_str(&value).ok()).unwrap_or_default(),
                language: row.get(9)?, is_nsfw: row.get::<_, i64>(10)? != 0, download_count: row.get(11)?,
                updated_at: row.get(12)?, source_url: row.get(13)?, card_url: row.get(14)?, raw_json: row.get(15)?,
                installed_character_id: row.get(16)?, cached_at: row.get(17)?,
            })).optional().map_err(|error| format!("No se pudo buscar personaje remoto: {error}"))
    }

    pub fn set_remote_installed(
        &mut self,
        provider_id: &str,
        remote_id: &str,
        character_id: &str,
    ) -> Result<(), String> {
        self.connection.execute("UPDATE remote_characters SET installed_character_id = ?3 WHERE provider_id = ?1 AND remote_id = ?2", params![provider_id, remote_id, character_id])
            .map_err(|error| format!("No se pudo marcar el personaje instalado: {error}"))?;
        Ok(())
    }

    pub fn insert_benchmark(
        &mut self,
        model_id: &str,
        context_length: i64,
        gpu_layers: i64,
        generated_tokens: i64,
        generation_tokens_per_second: f64,
        time_to_first_token_ms: f64,
    ) -> Result<(), String> {
        self.connection
            .execute(
                "INSERT INTO benchmarks
                 (id, model_id, backend, gpu_layers, context_length, generated_tokens, generation_tokens_per_second, time_to_first_token_ms)
                 VALUES (?1, ?2, 'llama-cli', ?3, ?4, ?5, ?6, ?7)",
                params![
                    Uuid::new_v4().to_string(),
                    model_id,
                    gpu_layers,
                    context_length,
                    generated_tokens,
                    generation_tokens_per_second,
                    time_to_first_token_ms,
                ],
            )
            .map_err(|e| format!("No se pudo guardar el benchmark: {e}"))?;
        Ok(())
    }
}

fn apply_migrations(connection: &mut Connection) -> Result<(), String> {
    let current: i64 = connection
        .query_row(
            "SELECT COALESCE(MAX(version), 0) FROM schema_migrations",
            [],
            |row| row.get(0),
        )
        .map_err(|e| format!("No se pudo leer schema_migrations: {e}"))?;

    if current < 1 {
        connection
            .execute("INSERT INTO schema_migrations(version) VALUES (1)", [])
            .map_err(|e| format!("No se pudo registrar la migración 1: {e}"))?;
    }

    let current: i64 = connection
        .query_row(
            "SELECT COALESCE(MAX(version), 0) FROM schema_migrations",
            [],
            |row| row.get(0),
        )
        .map_err(|e| format!("No se pudo leer schema_migrations: {e}"))?;
    if current < 2 {
        connection
            .execute_batch(
                "BEGIN;
                 CREATE TABLE IF NOT EXISTS settings (
                     key TEXT PRIMARY KEY,
                     value TEXT NOT NULL,
                     updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                 );
                 CREATE TABLE IF NOT EXISTS user_personas (
                     id TEXT PRIMARY KEY,
                     name TEXT NOT NULL,
                     description TEXT NOT NULL DEFAULT '',
                     prompt TEXT NOT NULL DEFAULT '',
                     avatar_path TEXT,
                     is_default INTEGER NOT NULL DEFAULT 0,
                     created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                 );
                 CREATE TABLE IF NOT EXISTS character_tags (
                     character_id TEXT NOT NULL,
                     tag TEXT NOT NULL,
                     PRIMARY KEY(character_id, tag),
                     FOREIGN KEY(character_id) REFERENCES characters(id) ON DELETE CASCADE
                 );
                 CREATE TABLE IF NOT EXISTS character_lore (
                     id TEXT PRIMARY KEY,
                     character_id TEXT NOT NULL,
                     keyword TEXT NOT NULL,
                     content TEXT NOT NULL,
                     priority INTEGER NOT NULL DEFAULT 0,
                     enabled INTEGER NOT NULL DEFAULT 1,
                     created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     FOREIGN KEY(character_id) REFERENCES characters(id) ON DELETE CASCADE
                 );
                 CREATE TABLE IF NOT EXISTS conversation_summaries (
                     conversation_id TEXT PRIMARY KEY,
                     summary TEXT NOT NULL,
                     message_count INTEGER NOT NULL DEFAULT 0,
                     token_count INTEGER NOT NULL DEFAULT 0,
                     updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
                 );
                 CREATE TABLE IF NOT EXISTS relationships (
                     id TEXT PRIMARY KEY,
                     character_id TEXT NOT NULL,
                     conversation_id TEXT,
                     label TEXT NOT NULL,
                     value TEXT NOT NULL,
                     confidence REAL NOT NULL DEFAULT 1.0,
                     created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     FOREIGN KEY(character_id) REFERENCES characters(id) ON DELETE CASCADE
                 );
                 CREATE TABLE IF NOT EXISTS pending_events (
                     id TEXT PRIMARY KEY,
                     character_id TEXT,
                     conversation_id TEXT,
                     title TEXT NOT NULL,
                     details TEXT NOT NULL DEFAULT '',
                     due_at TEXT,
                     completed INTEGER NOT NULL DEFAULT 0,
                     created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                 );
                 CREATE TABLE IF NOT EXISTS groups (
                     id TEXT PRIMARY KEY,
                     name TEXT NOT NULL,
                     description TEXT NOT NULL DEFAULT '',
                     avatar_path TEXT,
                     system_prompt TEXT NOT NULL DEFAULT '',
                     created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                 );
                 CREATE TABLE IF NOT EXISTS group_participants (
                     group_id TEXT NOT NULL,
                     character_id TEXT NOT NULL,
                     sort_order INTEGER NOT NULL DEFAULT 0,
                     PRIMARY KEY(group_id, character_id),
                     FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE,
                     FOREIGN KEY(character_id) REFERENCES characters(id) ON DELETE CASCADE
                 );
                 CREATE TABLE IF NOT EXISTS group_context (
                     group_id TEXT PRIMARY KEY,
                     context_json TEXT NOT NULL DEFAULT '{}',
                     updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE
                 );
                 CREATE TABLE IF NOT EXISTS providers (
                     id TEXT PRIMARY KEY,
                     kind TEXT NOT NULL,
                     name TEXT NOT NULL,
                     endpoint TEXT,
                     api_key TEXT,
                     enabled INTEGER NOT NULL DEFAULT 1,
                     created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                 );
                 CREATE TABLE IF NOT EXISTS provider_models_cache (
                     provider_id TEXT NOT NULL,
                     model_id TEXT NOT NULL,
                     name TEXT NOT NULL,
                     metadata_json TEXT NOT NULL DEFAULT '{}',
                     updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     PRIMARY KEY(provider_id, model_id),
                     FOREIGN KEY(provider_id) REFERENCES providers(id) ON DELETE CASCADE
                 );
                 CREATE TABLE IF NOT EXISTS voice_repositories (
                     id TEXT PRIMARY KEY,
                     name TEXT NOT NULL,
                     endpoint TEXT NOT NULL,
                     language TEXT,
                     enabled INTEGER NOT NULL DEFAULT 1,
                     updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                 );
                 CREATE TABLE IF NOT EXISTS voice_models (
                     id TEXT PRIMARY KEY,
                     repository_id TEXT,
                     name TEXT NOT NULL,
                     language TEXT,
                     path TEXT,
                     metadata_json TEXT NOT NULL DEFAULT '{}',
                     FOREIGN KEY(repository_id) REFERENCES voice_repositories(id) ON DELETE SET NULL
                 );
                 CREATE TABLE IF NOT EXISTS usage_records (
                     id TEXT PRIMARY KEY,
                     conversation_id TEXT,
                     model_id TEXT,
                     provider_id TEXT,
                     prompt_tokens INTEGER NOT NULL DEFAULT 0,
                     completion_tokens INTEGER NOT NULL DEFAULT 0,
                     latency_ms REAL,
                     created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                 );
                 CREATE TABLE IF NOT EXISTS message_feedback (
                     message_id TEXT PRIMARY KEY,
                     rating INTEGER,
                     note TEXT NOT NULL DEFAULT '',
                     created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     FOREIGN KEY(message_id) REFERENCES messages(id) ON DELETE CASCADE
                 );
                 CREATE TABLE IF NOT EXISTS branch_metadata (
                     conversation_id TEXT PRIMARY KEY,
                     parent_conversation_id TEXT,
                     branch_point_message_id TEXT,
                     label TEXT NOT NULL DEFAULT '',
                     created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                 );
                 COMMIT;",
            )
            .map_err(|e| format!("No se pudo aplicar la migración 2: {e}"))?;

        add_column_if_missing(
            connection,
            "characters",
            "scenario",
            "TEXT NOT NULL DEFAULT ''",
        )?;
        add_column_if_missing(
            connection,
            "characters",
            "card_format",
            "TEXT NOT NULL DEFAULT 'v2' ",
        )?;
        add_column_if_missing(
            connection,
            "characters",
            "creator",
            "TEXT NOT NULL DEFAULT ''",
        )?;
        add_column_if_missing(
            connection,
            "characters",
            "version",
            "TEXT NOT NULL DEFAULT '1.0'",
        )?;
        add_column_if_missing(
            connection,
            "characters",
            "tags_json",
            "TEXT NOT NULL DEFAULT '[]'",
        )?;
        add_column_if_missing(
            connection,
            "characters",
            "lore_json",
            "TEXT NOT NULL DEFAULT '[]'",
        )?;
        add_column_if_missing(
            connection,
            "characters",
            "alternate_greetings_json",
            "TEXT NOT NULL DEFAULT '[]'",
        )?;
        add_column_if_missing(
            connection,
            "characters",
            "example_messages_json",
            "TEXT NOT NULL DEFAULT '[]'",
        )?;
        add_column_if_missing(
            connection,
            "characters",
            "system_prompt",
            "TEXT NOT NULL DEFAULT ''",
        )?;
        add_column_if_missing(
            connection,
            "characters",
            "creator_notes",
            "TEXT NOT NULL DEFAULT ''",
        )?;
        add_column_if_missing(
            connection,
            "groups",
            "system_prompt",
            "TEXT NOT NULL DEFAULT ''",
        )?;
        add_column_if_missing(connection, "conversations", "model_id", "TEXT")?;
        add_column_if_missing(connection, "conversations", "persona_id", "TEXT")?;
        add_column_if_missing(
            connection,
            "conversations",
            "pinned",
            "INTEGER NOT NULL DEFAULT 0",
        )?;
        add_column_if_missing(
            connection,
            "conversations",
            "archived",
            "INTEGER NOT NULL DEFAULT 0",
        )?;
        add_column_if_missing(
            connection,
            "conversations",
            "kind",
            "TEXT NOT NULL DEFAULT 'direct'",
        )?;
        add_column_if_missing(
            connection,
            "conversations",
            "parent_conversation_id",
            "TEXT",
        )?;
        add_column_if_missing(
            connection,
            "conversations",
            "branch_point_message_id",
            "TEXT",
        )?;
        add_column_if_missing(
            connection,
            "conversations",
            "last_message_preview",
            "TEXT NOT NULL DEFAULT ''",
        )?;
        add_column_if_missing(connection, "messages", "reply_to_id", "TEXT")?;
        add_column_if_missing(
            connection,
            "messages",
            "pinned",
            "INTEGER NOT NULL DEFAULT 0",
        )?;
        add_column_if_missing(connection, "messages", "edited_at", "TEXT")?;
        add_column_if_missing(
            connection,
            "messages",
            "metadata_json",
            "TEXT NOT NULL DEFAULT '{}'",
        )?;
        add_column_if_missing(
            connection,
            "benchmarks",
            "generated_tokens",
            "INTEGER NOT NULL DEFAULT 0",
        )?;
        connection.execute_batch(
            "CREATE INDEX IF NOT EXISTS idx_messages_conversation_created ON messages(conversation_id, created_at, id);
             CREATE INDEX IF NOT EXISTS idx_conversations_updated ON conversations(updated_at DESC);
             CREATE INDEX IF NOT EXISTS idx_memories_character_updated ON memories(character_id, updated_at DESC);
             CREATE INDEX IF NOT EXISTS idx_lore_character_priority ON character_lore(character_id, priority DESC);",
        ).map_err(|e| format!("No se pudieron crear índices: {e}"))?;
        connection
            .execute("INSERT INTO schema_migrations(version) VALUES (2)", [])
            .map_err(|e| format!("No se pudo registrar la migración 2: {e}"))?;
    }
    let current: i64 = connection
        .query_row(
            "SELECT COALESCE(MAX(version), 0) FROM schema_migrations",
            [],
            |row| row.get(0),
        )
        .map_err(|e| format!("No se pudo leer schema_migrations: {e}"))?;
    if current < 3 {
        add_column_if_missing(connection, "providers", "model_name", "TEXT")?;
        connection
            .execute("INSERT INTO schema_migrations(version) VALUES (3)", [])
            .map_err(|e| format!("No se pudo registrar la migración 3: {e}"))?;
    }
    let current: i64 = connection
        .query_row(
            "SELECT COALESCE(MAX(version), 0) FROM schema_migrations",
            [],
            |row| row.get(0),
        )
        .map_err(|e| format!("No se pudo leer schema_migrations: {e}"))?;
    if current < 4 {
        add_column_if_missing(
            connection,
            "providers",
            "available_models_json",
            "TEXT NOT NULL DEFAULT '[]'",
        )?;
        connection
            .execute("INSERT INTO schema_migrations(version) VALUES (4)", [])
            .map_err(|e| format!("No se pudo registrar la migraciÃ³n 4: {e}"))?;
    }
    let current: i64 = connection
        .query_row(
            "SELECT COALESCE(MAX(version), 0) FROM schema_migrations",
            [],
            |row| row.get(0),
        )
        .map_err(|e| format!("No se pudo leer schema_migrations: {e}"))?;
    if current < 5 {
        connection
            .execute_batch(
                "BEGIN;
                 CREATE TABLE IF NOT EXISTS character_repositories (
                     id TEXT PRIMARY KEY,
                     provider_id TEXT NOT NULL,
                     name TEXT NOT NULL,
                     url TEXT NOT NULL UNIQUE,
                     enabled INTEGER NOT NULL DEFAULT 1,
                     status TEXT NOT NULL DEFAULT 'SYNCING',
                     status_message TEXT NOT NULL DEFAULT '',
                     last_sync_at TEXT,
                     updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                 );
                 CREATE TABLE IF NOT EXISTS remote_characters (
                     provider_id TEXT NOT NULL,
                     remote_id TEXT NOT NULL,
                     source_id TEXT NOT NULL,
                     name TEXT NOT NULL,
                     description TEXT NOT NULL DEFAULT '',
                     avatar_url TEXT,
                     author TEXT,
                     tags_json TEXT NOT NULL DEFAULT '[]',
                     language TEXT,
                     is_nsfw INTEGER NOT NULL DEFAULT 0,
                     download_count INTEGER,
                     remote_updated_at TEXT,
                     source_url TEXT NOT NULL,
                     card_url TEXT,
                     raw_json TEXT NOT NULL DEFAULT '{}',
                     installed_character_id TEXT,
                     cached_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     PRIMARY KEY(provider_id, remote_id),
                     FOREIGN KEY(source_id) REFERENCES character_repositories(id) ON DELETE CASCADE
                 );
                 CREATE INDEX IF NOT EXISTS idx_remote_characters_source ON remote_characters(source_id);
                 CREATE INDEX IF NOT EXISTS idx_remote_characters_language ON remote_characters(language);
                 CREATE INDEX IF NOT EXISTS idx_remote_characters_cached ON remote_characters(cached_at DESC);
                 COMMIT;",
            )
            .map_err(|e| format!("No se pudo aplicar la migración 5: {e}"))?;
        connection
            .execute("INSERT INTO schema_migrations(version) VALUES (5)", [])
            .map_err(|e| format!("No se pudo registrar la migración 5: {e}"))?;
    }
    let current: i64 = connection
        .query_row(
            "SELECT COALESCE(MAX(version), 0) FROM schema_migrations",
            [],
            |row| row.get(0),
        )
        .map_err(|e| format!("No se pudo leer schema_migrations: {e}"))?;
    if current < 6 {
        add_column_if_missing(
            connection,
            "remote_characters",
            "categories_json",
            "TEXT NOT NULL DEFAULT '[]'",
        )?;
        connection
            .execute("INSERT INTO schema_migrations(version) VALUES (6)", [])
            .map_err(|e| format!("No se pudo registrar la migraciÃ³n 6: {e}"))?;
    }
    let current: i64 = connection
        .query_row(
            "SELECT COALESCE(MAX(version), 0) FROM schema_migrations",
            [],
            |row| row.get(0),
        )
        .map_err(|e| format!("No se pudo leer schema_migrations: {e}"))?;
    if current < 7 {
        add_column_if_missing(
            connection,
            "conversation_summaries",
            "summary_until_message_id",
            "TEXT",
        )?;
        connection
            .execute("INSERT INTO schema_migrations(version) VALUES (7)", [])
            .map_err(|e| format!("No se pudo registrar la migraciÃ³n 7: {e}"))?;
    }
    Ok(())
}

fn add_column_if_missing(
    connection: &Connection,
    table: &str,
    column: &str,
    definition: &str,
) -> Result<(), String> {
    let mut statement = connection
        .prepare(&format!("PRAGMA table_info({table})"))
        .map_err(|e| format!("No se pudo inspeccionar {table}: {e}"))?;
    let columns = statement
        .query_map([], |row| row.get::<_, String>(1))
        .map_err(|e| format!("No se pudo leer columnas de {table}: {e}"))?;
    if columns.flatten().any(|existing| existing == column) {
        return Ok(());
    }
    connection
        .execute(
            &format!("ALTER TABLE {table} ADD COLUMN {column} {definition}"),
            [],
        )
        .map_err(|e| format!("No se pudo añadir {table}.{column}: {e}"))?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::{is_real_chat_message, suspicious_message_reason};
    use crate::models::ChatMessageRecord;

    fn message(role: &str, content: &str, metadata_json: &str) -> ChatMessageRecord {
        ChatMessageRecord {
            id: "m".into(),
            conversation_id: "c".into(),
            role: role.into(),
            content: content.into(),
            reply_to_id: None,
            pinned: false,
            metadata_json: metadata_json.into(),
            created_at: String::new(),
            edited_at: None,
        }
    }

    #[test]
    fn runtime_output_is_not_a_preview_candidate() {
        let log = message("assistant", "Loading model...", "{}");
        assert!(!is_real_chat_message(&log));
        assert!(suspicious_message_reason(&log).is_some());
    }

    #[test]
    fn typed_roleplay_turns_are_kept_even_without_keyword_heuristics() {
        let turn = message(
            "assistant",
            "Assistant: este es un diálogo legítimo",
            r#"{"source":"character"}"#,
        );
        assert!(is_real_chat_message(&turn));
        assert!(suspicious_message_reason(&turn).is_none());
    }
}
