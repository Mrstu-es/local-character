use serde::{Deserialize, Serialize};
use std::path::Path;
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ModelRecord {
    pub id: String,
    pub path: String,
    pub name: String,
    pub size_bytes: i64,
    pub exists: bool,
    pub architecture: Option<String>,
    pub quantization: Option<String>,
    pub parameter_count: Option<i64>,
    pub context_length: Option<i64>,
    pub chat_template: Option<String>,
    pub backend: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CharacterRecord {
    pub id: String,
    pub name: String,
    pub description: String,
    pub personality: String,
    pub greeting: String,
    #[serde(default)]
    pub scenario: String,
    #[serde(default)]
    pub first_message: String,
    #[serde(default)]
    pub example_messages: String,
    #[serde(default)]
    pub system_prompt: String,
    #[serde(default)]
    pub creator_notes: String,
    #[serde(default)]
    pub tags: Vec<String>,
    #[serde(default)]
    pub alternate_greetings: Vec<String>,
    #[serde(default)]
    pub lore: Vec<serde_json::Value>,
    pub avatar_path: Option<String>,
    #[serde(default)]
    pub voice_id: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ConversationRecord {
    pub id: String,
    pub character_id: Option<String>,
    pub model_id: Option<String>,
    pub persona_id: Option<String>,
    pub title: String,
    pub pinned: bool,
    pub archived: bool,
    pub kind: String,
    pub parent_conversation_id: Option<String>,
    pub branch_point_message_id: Option<String>,
    pub last_message_preview: String,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChatMessageRecord {
    pub id: String,
    pub conversation_id: String,
    pub role: String,
    pub content: String,
    pub reply_to_id: Option<String>,
    pub pinned: bool,
    pub metadata_json: String,
    pub created_at: String,
    pub edited_at: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ConversationSummaryRecord {
    pub conversation_id: String,
    pub summary: String,
    pub summary_until_message_id: Option<String>,
    pub message_count: i64,
    pub token_count: i64,
    pub updated_at: String,
}

/// Explicit long-term roleplay memory extracted locally from the visible
/// conversation. It never stores hidden reasoning or model diagnostics.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SemanticMemoryRecord {
    pub id: String,
    pub character_id: String,
    pub conversation_id: String,
    pub kind: String,
    pub subject: String,
    pub memory_key: String,
    pub content: String,
    pub confidence: f64,
    pub source_message_id: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SuspiciousMessageRecord {
    #[serde(flatten)]
    pub message: ChatMessageRecord,
    pub reason: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GroupRecord {
    pub id: String,
    pub name: String,
    pub description: String,
    pub avatar_path: Option<String>,
    #[serde(default)]
    pub system_prompt: Option<String>,
    pub participant_ids: Vec<String>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProviderRecord {
    pub id: String,
    pub kind: String,
    pub name: String,
    pub endpoint: Option<String>,
    pub api_key: Option<String>,
    pub model_name: Option<String>,
    #[serde(default)]
    pub available_models: Vec<String>,
    pub enabled: bool,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RepositorySourceRecord {
    pub id: String,
    pub provider_id: String,
    pub name: String,
    pub url: String,
    pub enabled: bool,
    pub status: String,
    pub status_message: String,
    pub last_sync_at: Option<String>,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RemoteCharacterRecord {
    pub provider_id: String,
    pub remote_id: String,
    pub source_id: String,
    pub name: String,
    pub description: String,
    pub avatar_url: Option<String>,
    pub author: Option<String>,
    pub tags: Vec<String>,
    #[serde(default)]
    pub categories: Vec<String>,
    pub language: Option<String>,
    pub is_nsfw: bool,
    pub download_count: Option<i64>,
    pub updated_at: Option<String>,
    pub source_url: String,
    pub card_url: Option<String>,
    pub raw_json: String,
    pub installed_character_id: Option<String>,
    pub cached_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RepositoryProbe {
    pub url: String,
    pub normalized_url: String,
    pub provider_id: String,
    pub provider_name: String,
    pub status: String,
    pub message: String,
    pub supports_search: bool,
    pub supports_install: bool,
    pub is_direct_character: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RepositorySyncResult {
    pub source: RepositorySourceRecord,
    pub probe: RepositoryProbe,
    pub items: Vec<RemoteCharacterRecord>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VoiceRepositoryRecord {
    pub id: String,
    pub name: String,
    pub endpoint: String,
    pub language: Option<String>,
    pub enabled: bool,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VoiceModelRecord {
    pub id: String,
    pub repository_id: Option<String>,
    pub name: String,
    pub language: Option<String>,
    pub path: Option<String>,
    pub metadata_json: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VoiceRepositorySyncResult {
    pub repository: VoiceRepositoryRecord,
    pub voices: Vec<VoiceModelRecord>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct ExploreFilterOption {
    pub id: String,
    pub label: String,
    pub count: usize,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct ExploreFilterCatalog {
    pub sources: Vec<ExploreFilterOption>,
    pub languages: Vec<ExploreFilterOption>,
    pub tags: Vec<ExploreFilterOption>,
    pub categories: Vec<ExploreFilterOption>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct ModelMetadata {
    pub name: Option<String>,
    pub architecture: Option<String>,
    pub quantization: Option<String>,
    pub parameter_count: Option<i64>,
    pub context_length: Option<i64>,
    pub chat_template: Option<String>,
    pub gguf_version: u32,
    pub tensor_count: u64,
    pub metadata_count: u64,
}

impl ModelMetadata {
    pub fn into_record(self, path: &Path, size_bytes: i64) -> ModelRecord {
        let fallback_name = path
            .file_stem()
            .and_then(|value| value.to_str())
            .unwrap_or("Modelo GGUF")
            .to_string();
        ModelRecord {
            id: Uuid::new_v4().to_string(),
            path: path.to_string_lossy().to_string(),
            name: self.name.unwrap_or(fallback_name),
            size_bytes,
            exists: true,
            architecture: self.architecture,
            quantization: self.quantization,
            parameter_count: self.parameter_count,
            context_length: self.context_length,
            chat_template: self.chat_template,
            backend: None,
            created_at: String::new(),
            updated_at: String::new(),
        }
    }
}
