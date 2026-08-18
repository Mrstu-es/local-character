//! Character repository providers shared by the desktop Explore flow.
//!
//! Android currently has two real catalog concepts: the public AI Character
//! Cards API and a validated repository.json format. Desktop keeps those
//! providers isolated and never scrapes arbitrary web pages as JSON.

use crate::models::{RemoteCharacterRecord, RepositoryProbe, VoiceModelRecord};
use reqwest::{Client, StatusCode, Url};
use serde_json::Value;
use std::time::Duration;

const MAX_JSON_BYTES: usize = 20 * 1024 * 1024;
const MAX_CARD_BYTES: usize = 50 * 1024 * 1024;
const AICC_API_ORIGIN: &str = "https://api.aicharactercards.com";
const AICC_API_BASE: &str = "https://api.aicharactercards.com/api";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ProviderKind {
    AiCharacterCards,
    ChubUnsupported,
    CharacterTavernUnsupported,
    GenericJson,
    Unknown,
}

impl ProviderKind {
    pub fn id(self) -> &'static str {
        match self {
            Self::AiCharacterCards => "ai_character_cards",
            Self::ChubUnsupported => "chub",
            Self::CharacterTavernUnsupported => "character_tavern",
            Self::GenericJson => "generic_json",
            Self::Unknown => "unknown",
        }
    }

    pub fn name(self) -> &'static str {
        match self {
            Self::AiCharacterCards => "AI Character Cards",
            Self::ChubUnsupported => "Chub",
            Self::CharacterTavernUnsupported => "Character Tavern",
            Self::GenericJson => "Repositorio JSON de Local Character",
            Self::Unknown => "Fuente desconocida",
        }
    }
}

pub fn normalize_url(raw: &str) -> Result<Url, String> {
    let value = raw.trim();
    let mut url = Url::parse(value).map_err(|_| "La URL de la fuente no es válida".to_string())?;
    if !matches!(url.scheme(), "https" | "http") {
        return Err("La fuente debe usar una URL http:// o https://".into());
    }
    if url.host_str().is_none() {
        return Err("La URL de la fuente no tiene un dominio válido".into());
    }
    if !url.username().is_empty() || url.password().is_some() {
        return Err("Las credenciales incrustadas en una URL no están permitidas".into());
    }
    url.set_fragment(None);
    if url.path().is_empty() {
        url.set_path("/");
    }
    Ok(url)
}

fn host_without_www(url: &Url) -> String {
    url.host_str()
        .unwrap_or_default()
        .trim_start_matches("www.")
        .to_ascii_lowercase()
}

pub fn provider_for_url(url: &Url) -> ProviderKind {
    let host = host_without_www(url);
    if host == "aicharactercards.com" || host.ends_with(".aicharactercards.com") {
        return ProviderKind::AiCharacterCards;
    }
    if host == "chub.ai" || host.ends_with(".chub.ai") {
        return ProviderKind::ChubUnsupported;
    }
    if host == "character-tavern.com" || host.ends_with(".character-tavern.com") {
        return ProviderKind::CharacterTavernUnsupported;
    }
    if url.path().to_ascii_lowercase().ends_with(".json")
        || url
            .path_segments()
            .map(|mut segments| {
                segments.any(|segment| segment.eq_ignore_ascii_case("repository.json"))
            })
            .unwrap_or(false)
    {
        ProviderKind::GenericJson
    } else {
        ProviderKind::Unknown
    }
}

pub fn provider_for_content(url: &Url, content_type: Option<&str>, bytes: &[u8]) -> ProviderKind {
    let known = provider_for_url(url);
    if known != ProviderKind::Unknown {
        return known;
    }
    let json_content_type = content_type
        .unwrap_or_default()
        .to_ascii_lowercase()
        .contains("json");
    if json_content_type || serde_json::from_slice::<Value>(bytes).is_ok() {
        ProviderKind::GenericJson
    } else {
        ProviderKind::Unknown
    }
}

pub fn probe_without_network(raw: &str) -> Result<RepositoryProbe, String> {
    let url = normalize_url(raw)?;
    let kind = provider_for_url(&url);
    let (status, message, search, install) = match kind {
        ProviderKind::AiCharacterCards => (
            "READY",
            "AI Character Cards detectado. Se consultará su catálogo público.",
            true,
            true,
        ),
        ProviderKind::ChubUnsupported => (
            "UNSUPPORTED",
            "Chub está reconocido, pero Android no contiene un adapter público para consultar o descargar sus tarjetas. No se inventó ningún endpoint.",
            false,
            false,
        ),
        ProviderKind::CharacterTavernUnsupported => (
            "UNSUPPORTED",
            "Character Tavern está reconocido, pero Android no contiene un adapter público para esta fuente. No se hará scraping frágil.",
            false,
            false,
        ),
        ProviderKind::GenericJson => (
            "CHECK_REQUIRED",
            "URL con apariencia de repository.json. Se validará el schema antes de aceptarla.",
            true,
            true,
        ),
        ProviderKind::Unknown => (
            "UNKNOWN",
            "Esta página no pertenece a una fuente compatible conocida. Puedes intentar validarla como repository.json.",
            false,
            false,
        ),
    };
    let is_direct = url
        .path_segments()
        .map(|segments| {
            let items: Vec<_> = segments.collect();
            items.iter().any(|segment| {
                segment.eq_ignore_ascii_case("character") || segment.eq_ignore_ascii_case("cards")
            }) && items.iter().any(|segment| {
                !segment.is_empty()
                    && !segment.eq_ignore_ascii_case("cards")
                    && !segment.eq_ignore_ascii_case("character")
            })
        })
        .unwrap_or(false);
    Ok(RepositoryProbe {
        url: raw.trim().to_string(),
        normalized_url: url.to_string(),
        provider_id: kind.id().to_string(),
        provider_name: kind.name().to_string(),
        status: status.to_string(),
        message: message.to_string(),
        supports_search: search,
        supports_install: install,
        is_direct_character: is_direct,
    })
}

pub async fn probe(raw: &str) -> Result<RepositoryProbe, String> {
    let url = normalize_url(raw)?;
    let initial = probe_without_network(raw)?;
    if matches!(
        provider_for_url(&url),
        ProviderKind::ChubUnsupported
            | ProviderKind::CharacterTavernUnsupported
            | ProviderKind::AiCharacterCards
    ) {
        return Ok(initial);
    }
    let response = client()?
        .get(url.clone())
        .send()
        .await
        .map_err(|error| format!("No se pudo conectar con la fuente: {error}"))?;
    let status_code = response.status();
    if status_code == StatusCode::TOO_MANY_REQUESTS {
        return Err("Demasiadas solicitudes. Inténtalo nuevamente.".into());
    }
    if !status_code.is_success() {
        return Err(format!("La fuente respondió con HTTP {status_code}"));
    }
    let content_type = response
        .headers()
        .get(reqwest::header::CONTENT_TYPE)
        .and_then(|value| value.to_str().ok())
        .map(str::to_string);
    let bytes = read_limited(response, MAX_JSON_BYTES).await?;
    let kind = provider_for_content(&url, content_type.as_deref(), &bytes);
    if kind != ProviderKind::GenericJson {
        return Ok(initial);
    }
    validate_generic_manifest(&bytes)?;
    Ok(RepositoryProbe {
        provider_id: kind.id().into(),
        provider_name: kind.name().into(),
        status: "READY".into(),
        message: "Repositorio JSON de Local Character compatible.".into(),
        supports_search: true,
        supports_install: true,
        ..initial
    })
}

pub async fn fetch_catalog(
    raw: &str,
    query: &str,
    language: Option<&str>,
    tags: &[String],
    safe_only: bool,
) -> Result<(RepositoryProbe, Vec<RemoteCharacterRecord>), String> {
    let url = normalize_url(raw)?;
    let initial = probe_without_network(raw)?;
    let kind = provider_for_url(&url);
    match kind {
        ProviderKind::ChubUnsupported | ProviderKind::CharacterTavernUnsupported => {
            Err(initial.message)
        }
        ProviderKind::AiCharacterCards => {
            let page_limit = 100usize;
            let mut skip = 0usize;
            let mut records = Vec::new();
            loop {
                let mut endpoint = Url::parse(&format!("{AICC_API_BASE}/cards"))
                    .map_err(|error| error.to_string())?;
                {
                    let mut pairs = endpoint.query_pairs_mut();
                    pairs.append_pair("skip", &skip.to_string());
                    pairs.append_pair("limit", &page_limit.to_string());
                    pairs.append_pair("isNsfw", if safe_only { "false" } else { "true" });
                    if !query.trim().is_empty() {
                        pairs.append_pair("search", query.trim());
                    }
                    if let Some(language) = language.filter(|value| !value.is_empty()) {
                        pairs.append_pair("language", language);
                    }
                    if !tags.is_empty() {
                        pairs.append_pair("tags", &tags.join(","));
                    }
                }
                let response =
                    client()?.get(endpoint).send().await.map_err(|error| {
                        format!("No se pudo consultar AI Character Cards: {error}")
                    })?;
                let status = response.status();
                if status == StatusCode::TOO_MANY_REQUESTS {
                    return Err("Demasiadas solicitudes. Inténtalo nuevamente.".into());
                }
                if !status.is_success() {
                    return Err(format!("AI Character Cards respondió con HTTP {status}"));
                }
                let bytes = read_limited(response, MAX_JSON_BYTES).await?;
                let root: Value = serde_json::from_slice(&bytes).map_err(|error| {
                    format!("AI Character Cards devolvió JSON no válido: {error}")
                })?;
                let page_count = root
                    .get("data")
                    .and_then(Value::as_array)
                    .map(Vec::len)
                    .unwrap_or_default();
                records.extend(parse_aicc(&bytes, raw)?);
                let total = root
                    .get("pagination")
                    .and_then(|value| value.get("total"))
                    .and_then(Value::as_u64)
                    .map(|value| value as usize);
                if page_count == 0
                    || page_count < page_limit
                    || total
                        .map(|value| skip + page_count >= value)
                        .unwrap_or(false)
                    || records.len() >= 1000
                {
                    break;
                }
                skip += page_count;
            }
            let mut probe = initial;
            probe.status = "READY".into();
            probe.message = format!(
                "AI Character Cards: {} personajes disponibles.",
                records.len()
            );
            Ok((probe, records))
        }
        _ => {
            let response = client()?
                .get(url.clone())
                .send()
                .await
                .map_err(|error| format!("No se pudo descargar el catálogo: {error}"))?;
            let status = response.status();
            if status == StatusCode::TOO_MANY_REQUESTS {
                return Err("Demasiadas solicitudes. Inténtalo nuevamente.".into());
            }
            if !status.is_success() {
                return Err(format!("La fuente respondió con HTTP {status}"));
            }
            let content_type = response
                .headers()
                .get(reqwest::header::CONTENT_TYPE)
                .and_then(|value| value.to_str().ok())
                .map(str::to_string);
            let bytes = read_limited(response, MAX_JSON_BYTES).await?;
            if provider_for_content(&url, content_type.as_deref(), &bytes)
                != ProviderKind::GenericJson
            {
                return Err("Esta página no pertenece a una fuente compatible conocida.".into());
            }
            let records = parse_generic(&bytes, raw)?;
            let mut probe = initial;
            probe.provider_id = ProviderKind::GenericJson.id().into();
            probe.provider_name = ProviderKind::GenericJson.name().into();
            probe.status = "READY".into();
            probe.message = format!(
                "Repositorio JSON: {} personajes disponibles.",
                records.len()
            );
            Ok((probe, records))
        }
    }
}

/// Reads a small, provider-agnostic voice manifest. Supported roots are an
/// array or an object containing `voices`, `models`, or `data`. Voice files
/// remain metadata references; playback uses the browser/OS TTS voice chosen
/// for the character when it is available on the machine.
pub async fn fetch_voice_catalog(raw: &str) -> Result<Vec<VoiceModelRecord>, String> {
    let endpoint = normalize_url(raw)?;
    let response = client()?
        .get(endpoint.clone())
        .send()
        .await
        .map_err(|error| format!("No se pudo consultar el repositorio TTS: {error}"))?;
    if !response.status().is_success() {
        return Err(format!(
            "El repositorio TTS respondió con HTTP {}",
            response.status()
        ));
    }
    let bytes = read_limited(response, 16 * 1024 * 1024).await?;
    let root: Value = serde_json::from_slice(&bytes)
        .map_err(|error| format!("El repositorio TTS devolvió JSON no válido: {error}"))?;
    let items = root
        .as_array()
        .or_else(|| root.get("voices").and_then(Value::as_array))
        .or_else(|| root.get("models").and_then(Value::as_array))
        .or_else(|| root.get("data").and_then(Value::as_array))
        .or_else(|| {
            root.get("data")
                .and_then(|data| data.get("voices").or_else(|| data.get("models")))
                .and_then(Value::as_array)
        })
        .ok_or_else(|| {
            "El repositorio TTS no contiene una lista de voces compatible".to_string()
        })?;
    let voices = items
        .iter()
        .filter_map(|item| {
            let (raw_id, name, language, path) = if let Some(value) = item.as_str() {
                (value.to_string(), value.to_string(), None, None)
            } else {
                let data = item.get("data").unwrap_or(item);
                let name = value_string(
                    data,
                    &["name", "title", "voiceName", "voice_name", "label", "id"],
                )?;
                let raw_id = value_string(data, &["id", "voiceId", "voice_id", "name"])
                    .unwrap_or_else(|| name.clone());
                let language = value_string(data, &["language", "lang"])
                    .map(|value| normalize_language(&value));
                let path = value_string(
                    data,
                    &[
                        "url",
                        "path",
                        "file",
                        "fileUrl",
                        "file_url",
                        "modelUrl",
                        "model_url",
                        "src",
                    ],
                );
                (raw_id, name, language, path)
            };
            let id = format!("{}#{}", endpoint, raw_id);
            let path = path.and_then(|value| {
                if value.starts_with("http://") || value.starts_with("https://") {
                    normalize_url(&value).ok().map(|url| url.to_string())
                } else {
                    endpoint.join(&value).ok().map(|url| url.to_string())
                }
            });
            Some(VoiceModelRecord {
                id,
                repository_id: None,
                name,
                language,
                path,
                metadata_json: item.to_string(),
            })
        })
        .take(512)
        .collect::<Vec<_>>();
    if voices.is_empty() {
        return Err("El repositorio TTS no contiene voces con nombre".into());
    }
    Ok(voices)
}

pub async fn download_card(record: &RemoteCharacterRecord) -> Result<Vec<u8>, String> {
    let url = if record.provider_id == ProviderKind::AiCharacterCards.id() {
        let detail_url = format!(
            "{AICC_API_BASE}/cards/{}",
            urlencoding_safe(&record.remote_id)
        );
        let response = client()?
            .get(detail_url)
            .send()
            .await
            .map_err(|error| format!("No se pudo consultar el personaje remoto: {error}"))?;
        if !response.status().is_success() {
            return Err(format!(
                "El proveedor respondió con HTTP {}",
                response.status()
            ));
        }
        let value: Value = response
            .json()
            .await
            .map_err(|error| format!("La ficha remota no es JSON válido: {error}"))?;
        let versions = value
            .get("versions")
            .and_then(Value::as_array)
            .or_else(|| {
                value
                    .get("data")
                    .and_then(|data| data.get("versions"))
                    .and_then(Value::as_array)
            })
            .ok_or_else(|| "El proveedor no publicó versiones descargables".to_string())?;
        let current = versions
            .iter()
            .find(|version| {
                version
                    .get("isCurrent")
                    .and_then(Value::as_bool)
                    .unwrap_or(false)
            })
            .or_else(|| versions.last())
            .ok_or_else(|| "El proveedor no publicó una tarjeta descargable".to_string())?;
        let file_url = current
            .get("fileUrl")
            .or_else(|| current.get("file_url"))
            .and_then(Value::as_str)
            .ok_or_else(|| "La versión actual no tiene URL de descarga".to_string())?;
        resolve_aicc_url(file_url)?
    } else {
        let card_url = record
            .card_url
            .clone()
            .ok_or_else(|| "Este resultado no tiene una tarjeta descargable".to_string())?;
        if card_url.starts_with("http://") || card_url.starts_with("https://") {
            card_url
        } else {
            normalize_url(&record.source_url)?
                .join(&card_url)
                .map_err(|error| format!("La URL de la tarjeta no es válida: {error}"))?
                .to_string()
        }
    };
    let url = normalize_url(&url)?;
    let response = client()?
        .get(url)
        .send()
        .await
        .map_err(|error| format!("No se pudo descargar la Character Card: {error}"))?;
    if !response.status().is_success() {
        return Err(format!(
            "La descarga respondió con HTTP {}",
            response.status()
        ));
    }
    read_limited(response, MAX_CARD_BYTES).await
}

pub async fn download_asset(url: &str) -> Result<Vec<u8>, String> {
    let parsed = normalize_url(url)?;
    let response = client()?
        .get(parsed)
        .send()
        .await
        .map_err(|error| format!("No se pudo descargar el avatar: {error}"))?;
    if !response.status().is_success() {
        return Err(format!(
            "El avatar respondió con HTTP {}",
            response.status()
        ));
    }
    read_limited(response, 8 * 1024 * 1024).await
}

fn client() -> Result<Client, String> {
    Client::builder()
        .timeout(Duration::from_secs(25))
        .user_agent("LocalCharacterDesktop/0.1")
        .redirect(reqwest::redirect::Policy::limited(5))
        .build()
        .map_err(|error| format!("No se pudo preparar la conexión: {error}"))
}

async fn read_limited(response: reqwest::Response, limit: usize) -> Result<Vec<u8>, String> {
    if response.content_length().unwrap_or(0) > limit as u64 {
        return Err(format!(
            "La respuesta supera el límite permitido de {} MB",
            limit / 1024 / 1024
        ));
    }
    let bytes = response
        .bytes()
        .await
        .map_err(|error| format!("No se pudo leer la respuesta: {error}"))?;
    if bytes.len() > limit {
        return Err(format!(
            "La respuesta supera el límite permitido de {} MB",
            limit / 1024 / 1024
        ));
    }
    Ok(bytes.to_vec())
}

fn parse_aicc(bytes: &[u8], source_url: &str) -> Result<Vec<RemoteCharacterRecord>, String> {
    let root: Value = serde_json::from_slice(bytes)
        .map_err(|error| format!("AI Character Cards devolvió JSON no válido: {error}"))?;
    let data = root
        .get("data")
        .and_then(Value::as_array)
        .ok_or_else(|| "AI Character Cards no devolvió un catálogo compatible".to_string())?;
    Ok(data
        .iter()
        .take(1000)
        .filter_map(|item| map_remote_item(item, ProviderKind::AiCharacterCards, source_url))
        .collect())
}

fn parse_generic(bytes: &[u8], source_url: &str) -> Result<Vec<RemoteCharacterRecord>, String> {
    validate_generic_manifest(bytes)?;
    let root: Value = serde_json::from_slice(bytes)
        .map_err(|_| "El repositorio JSON no es válido".to_string())?;
    let items = root
        .as_array()
        .or_else(|| root.get("characters").and_then(Value::as_array))
        .or_else(|| root.get("cards").and_then(Value::as_array))
        .ok_or_else(|| "El JSON no es un repositorio de personajes compatible".to_string())?;
    Ok(items
        .iter()
        .take(1000)
        .filter_map(|item| map_remote_item(item, ProviderKind::GenericJson, source_url))
        .collect())
}

fn validate_generic_manifest(bytes: &[u8]) -> Result<(), String> {
    let root: Value = serde_json::from_slice(bytes)
        .map_err(|_| "El JSON no es un repositorio de personajes compatible".to_string())?;
    let items = root
        .as_array()
        .or_else(|| root.get("characters").and_then(Value::as_array))
        .or_else(|| root.get("cards").and_then(Value::as_array))
        .ok_or_else(|| "El JSON no es un repositorio de personajes compatible".to_string())?;
    if items.is_empty() {
        return Err("El repositorio JSON no contiene personajes".into());
    }
    if !items.iter().any(|item| {
        item.get("name").is_some()
            || item.get("title").is_some()
            || item.get("data").and_then(|data| data.get("name")).is_some()
    }) {
        return Err("El JSON no es un repositorio de personajes compatible".into());
    }
    Ok(())
}

fn map_remote_item(
    item: &Value,
    provider: ProviderKind,
    source_url: &str,
) -> Option<RemoteCharacterRecord> {
    let data = item.get("data").unwrap_or(item);
    let id = value_string(data, &["id", "remoteId", "remote_id"])
        .or_else(|| value_string(item, &["id", "remoteId", "remote_id"]))?;
    let name = value_string(data, &["title", "name"])
        .or_else(|| value_string(item, &["title", "name"]))?;
    if name.trim().is_empty() {
        return None;
    }
    let description =
        value_string(data, &["excerpt", "description", "shortDescription"]).unwrap_or_default();
    let avatar_url = value_string(
        data,
        &[
            "imageUrl",
            "avatarUrl",
            "avatar_url",
            "image",
            "thumbnailUrl",
        ],
    );
    let card_url = value_string(
        data,
        &[
            "cardUrl",
            "card_url",
            "downloadUrl",
            "download_url",
            "fileUrl",
            "file_url",
        ],
    );
    let tags = value_strings(data, &["tags"]);
    let categories = value_strings(data, &["categories", "category"]);
    let language = value_string(data, &["language", "lang"]);
    let is_nsfw = data
        .get("isNsfw")
        .and_then(Value::as_bool)
        .or_else(|| data.get("nsfw").and_then(Value::as_bool))
        .unwrap_or(false)
        || tags.iter().any(|tag| tag.eq_ignore_ascii_case("nsfw"));
    let source = value_string(data, &["sourceUrl", "source_url", "url"])
        .unwrap_or_else(|| source_url.to_string());
    Some(RemoteCharacterRecord {
        provider_id: provider.id().into(),
        remote_id: id,
        source_id: source_url.to_string(),
        name: name.chars().take(120).collect(),
        description: strip_html(&description).chars().take(2000).collect(),
        avatar_url: avatar_url
            .and_then(|url| resolve_provider_asset(provider, &url, Some(source_url)).ok()),
        author: value_string(data, &["author", "creator"])
            .map(|value| value.chars().take(120).collect()),
        tags: tags
            .into_iter()
            .map(|tag| normalize_tag(&tag))
            .filter(|tag| !tag.is_empty())
            .take(64)
            .collect(),
        categories: categories
            .into_iter()
            .map(|category| normalize_tag(&category))
            .filter(|category| !category.is_empty())
            .take(32)
            .collect(),
        language: language.map(|value| normalize_language(&value)),
        is_nsfw,
        download_count: data.get("downloadCount").and_then(Value::as_i64),
        updated_at: value_string(
            data,
            &["updatedAt", "updated_at", "createdAt", "created_at"],
        ),
        source_url: source,
        card_url: card_url
            .and_then(|url| resolve_provider_asset(provider, &url, Some(source_url)).ok()),
        raw_json: item.to_string(),
        installed_character_id: None,
        cached_at: String::new(),
    })
}

fn value_string(value: &Value, keys: &[&str]) -> Option<String> {
    keys.iter()
        .find_map(|key| {
            value.get(*key).and_then(|item| {
                item.as_str()
                    .map(str::to_string)
                    .or_else(|| item.as_i64().map(|number| number.to_string()))
            })
        })
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
}

fn value_strings(value: &Value, keys: &[&str]) -> Vec<String> {
    for key in keys {
        if let Some(item) = value.get(*key) {
            if let Some(values) = item.as_array() {
                return values
                    .iter()
                    .filter_map(|value| {
                        value.as_str().map(str::to_string).or_else(|| {
                            value
                                .get("name")
                                .and_then(Value::as_str)
                                .map(str::to_string)
                        })
                    })
                    .collect();
            }
            if let Some(value) = item.as_str() {
                return value
                    .split(',')
                    .map(str::trim)
                    .map(str::to_string)
                    .filter(|value| !value.is_empty())
                    .collect();
            }
        }
    }
    Vec::new()
}

fn resolve_provider_asset(
    provider: ProviderKind,
    value: &str,
    base_url: Option<&str>,
) -> Result<String, String> {
    let value = value.trim();
    let resolved = if value.starts_with("http://") || value.starts_with("https://") {
        value.to_string()
    } else if provider == ProviderKind::AiCharacterCards || base_url.is_some() {
        let base = if provider == ProviderKind::AiCharacterCards {
            Url::parse(AICC_API_ORIGIN).map_err(|error| error.to_string())?
        } else {
            normalize_url(base_url.unwrap_or_default())?
        };
        base.join(value)
            .map_err(|error| error.to_string())?
            .to_string()
    } else {
        return Err("Ruta de asset remota no válida".into());
    };
    let parsed = normalize_url(&resolved)?;
    if provider == ProviderKind::AiCharacterCards
        && !host_without_www(&parsed).ends_with("aicharactercards.com")
    {
        return Err("Asset fuera del dominio permitido".into());
    }
    Ok(parsed.to_string())
}

fn resolve_aicc_url(value: &str) -> Result<String, String> {
    resolve_provider_asset(ProviderKind::AiCharacterCards, value, None)
}

fn urlencoding_safe(value: &str) -> String {
    value
        .chars()
        .map(|character| {
            if character.is_ascii_alphanumeric() || matches!(character, '-' | '_' | '.' | '~') {
                character.to_string()
            } else {
                format!("%{:02X}", character as u32)
            }
        })
        .collect()
}

fn strip_html(value: &str) -> String {
    let mut result = String::with_capacity(value.len());
    let mut in_tag = false;
    for character in value.chars() {
        match character {
            '<' => in_tag = true,
            '>' => in_tag = false,
            _ if !in_tag => result.push(character),
            _ => {}
        }
    }
    result
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .trim()
        .to_string()
}

pub fn normalize_language(value: &str) -> String {
    let value = value.trim().to_ascii_lowercase().replace('_', "-");
    let token = value
        .split(|character: char| {
            character == '-' || character == '(' || character == ',' || character.is_whitespace()
        })
        .next()
        .unwrap_or("");
    match token {
        "es" | "spa" | "spanish" | "español" | "espanol" => "es",
        "en" | "eng" | "english" | "inglés" | "ingles" => "en",
        "pt" | "por" | "portuguese" | "português" | "portugues" => "pt",
        "fr" | "fra" | "fre" | "french" | "français" | "francais" => "fr",
        "de" | "deu" | "ger" | "german" | "deutsch" => "de",
        "ja" | "jpn" | "japanese" => "ja",
        other => other,
    }
    .to_string()
}

pub fn normalize_tag(value: &str) -> String {
    let value = value
        .trim()
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
        .to_lowercase();
    let mut chars = value.chars();
    chars
        .next()
        .map(|first| {
            let mut result = String::new();
            result.push(first.to_ascii_uppercase());
            result.push_str(chars.as_str());
            result
        })
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalizes_provider_hosts_without_exact_string_matching() {
        let url = normalize_url("https://www.aicharactercards.com/").unwrap();
        assert_eq!(provider_for_url(&url), ProviderKind::AiCharacterCards);
        assert_eq!(
            provider_for_url(&normalize_url("https://aicharactercards.com/cards?x=1").unwrap()),
            ProviderKind::AiCharacterCards
        );
    }

    #[test]
    fn does_not_treat_unknown_html_as_json() {
        let url = normalize_url("https://example.com/").unwrap();
        assert_eq!(
            provider_for_content(&url, Some("text/html"), b"<html></html>"),
            ProviderKind::Unknown
        );
    }

    #[test]
    fn normalizes_facets() {
        assert_eq!(normalize_language("Español (es-ES)"), "es");
        assert_eq!(normalize_tag("  anime   "), "Anime");
    }
}
