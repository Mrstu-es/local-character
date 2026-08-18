use base64::{engine::general_purpose::STANDARD, Engine as _};
use flate2::read::ZlibDecoder;
use serde_json::Value;
use std::io::Read;
use std::path::Path;
use uuid::Uuid;

const MAX_FILE_BYTES: usize = 20 * 1024 * 1024;
const MAX_METADATA_BYTES: usize = 2 * 1024 * 1024;

pub fn import(path: &Path) -> Result<crate::models::CharacterRecord, String> {
    let bytes = std::fs::read(path).map_err(|e| format!("No se pudo leer la tarjeta: {e}"))?;
    import_bytes(&bytes)
}

pub fn import_bytes(bytes: &[u8]) -> Result<crate::models::CharacterRecord, String> {
    if bytes.len() > MAX_FILE_BYTES {
        return Err("La tarjeta supera el límite permitido de 20 MB".into());
    }
    let json_bytes = if bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        extract_png_payload(&bytes)?
    } else {
        bytes.to_vec()
    };
    if json_bytes.len() > MAX_METADATA_BYTES {
        return Err("La metadata de la tarjeta es demasiado grande".into());
    }
    let root: Value = serde_json::from_slice(&json_bytes)
        .map_err(|_| "El JSON de la tarjeta está dañado o no es válido".to_string())?;
    parse_value(root)
}

pub fn import_repository(path: &Path) -> Result<Vec<crate::models::CharacterRecord>, String> {
    let bytes = std::fs::read(path).map_err(|e| format!("No se pudo leer el repositorio: {e}"))?;
    import_repository_bytes(&bytes)
}

pub fn import_repository_bytes(
    bytes: &[u8],
) -> Result<Vec<crate::models::CharacterRecord>, String> {
    if bytes.len() > 50 * 1024 * 1024 {
        return Err("El repositorio supera 50 MB".into());
    }
    let root: Value = serde_json::from_slice(&bytes)
        .map_err(|_| "El repositorio debe ser un JSON válido".to_string())?;
    let items = root
        .as_array()
        .or_else(|| root.get("characters").and_then(Value::as_array))
        .or_else(|| root.get("cards").and_then(Value::as_array))
        .ok_or_else(|| "El repositorio necesita un array 'characters' o 'cards'".to_string())?;
    let mut characters = Vec::new();
    for item in items.iter().take(1000) {
        if let Ok(character) = parse_value(item.clone()) {
            characters.push(character);
        }
    }
    if characters.is_empty() {
        return Err("No se encontraron Character Cards válidas".into());
    }
    Ok(characters)
}

fn parse_value(root: Value) -> Result<crate::models::CharacterRecord, String> {
    let data = root.get("data").unwrap_or(&root);
    let name = string(data, &["name"]).trim().to_string();
    if name.is_empty() {
        return Err("La tarjeta no contiene un nombre de personaje".into());
    }
    let card = |names: &[&str]| string(data, names);
    let lore = data
        .get("character_book")
        .and_then(|book| book.get("entries"))
        .and_then(Value::as_array)
        .map(|entries| entries.iter().take(500).cloned().collect())
        .unwrap_or_default();
    Ok(crate::models::CharacterRecord {
        id: Uuid::new_v4().to_string(),
        name: name.chars().take(120).collect(),
        description: capped(&card(&["description"]), 20_000),
        personality: capped(&card(&["personality"]), 20_000),
        greeting: capped(&card(&["first_mes", "firstMessage"]), 20_000),
        scenario: capped(&card(&["scenario"]), 20_000),
        first_message: capped(&card(&["first_mes", "firstMessage"]), 20_000),
        example_messages: capped(&card(&["mes_example", "exampleMessages"]), 40_000),
        system_prompt: capped(&card(&["system_prompt", "systemPrompt"]), 20_000),
        creator_notes: capped(&card(&["creator_notes", "creatorNotes"]), 20_000),
        tags: string_list(data, &["tags"])
            .into_iter()
            .take(64)
            .map(|s| capped(&s, 80))
            .collect(),
        alternate_greetings: string_list(data, &["alternate_greetings", "alternateGreetings"])
            .into_iter()
            .take(32)
            .map(|s| capped(&s, 20_000))
            .collect(),
        lore,
        avatar_path: None,
        voice_id: data
            .get("voiceId")
            .or_else(|| data.get("voice_id"))
            .and_then(Value::as_str)
            .map(str::to_string),
        created_at: String::new(),
        updated_at: String::new(),
    })
}

fn string(value: &Value, names: &[&str]) -> String {
    names
        .iter()
        .find_map(|name| value.get(*name).and_then(Value::as_str))
        .unwrap_or_default()
        .to_string()
}

fn string_list(value: &Value, names: &[&str]) -> Vec<String> {
    names
        .iter()
        .find_map(|name| {
            let item = value.get(*name)?;
            if let Some(array) = item.as_array() {
                return Some(
                    array
                        .iter()
                        .filter_map(Value::as_str)
                        .map(str::to_string)
                        .filter(|s| !s.trim().is_empty())
                        .collect(),
                );
            }
            item.as_str().map(|s| {
                s.split(',')
                    .map(str::trim)
                    .filter(|s| !s.is_empty())
                    .map(str::to_string)
                    .collect()
            })
        })
        .unwrap_or_default()
}

fn capped(value: &str, max: usize) -> String {
    value.chars().take(max).collect()
}

fn extract_png_payload(bytes: &[u8]) -> Result<Vec<u8>, String> {
    let mut offset = 8_usize;
    while offset + 12 <= bytes.len() {
        let length = u32::from_be_bytes(bytes[offset..offset + 4].try_into().unwrap()) as usize;
        if length > MAX_METADATA_BYTES || offset + 12 + length > bytes.len() {
            return Err("La estructura PNG de la tarjeta está dañada".into());
        }
        let kind = &bytes[offset + 4..offset + 8];
        let payload = &bytes[offset + 8..offset + 8 + length];
        let encoded = match kind {
            b"tEXt" => text_chunk(payload),
            b"zTXt" => compressed_text_chunk(payload)?,
            b"iTXt" => international_text_chunk(payload)?,
            _ => None,
        };
        if let Some(encoded) = encoded {
            return STANDARD
                .decode(encoded.trim())
                .map_err(|_| "La metadata 'chara' no contiene Base64 válido".into());
        }
        offset += 12 + length;
        if kind == b"IEND" {
            break;
        }
    }
    Err("Este PNG no contiene metadata compatible con Character Card".into())
}

fn text_chunk(payload: &[u8]) -> Option<String> {
    let separator = payload.iter().position(|value| *value == 0)?;
    if &payload[..separator] != b"chara" {
        return None;
    }
    String::from_utf8(payload[separator + 1..].to_vec()).ok()
}

fn compressed_text_chunk(payload: &[u8]) -> Result<Option<String>, String> {
    let Some(separator) = payload.iter().position(|value| *value == 0) else {
        return Ok(None);
    };
    if &payload[..separator] != b"chara" || payload.get(separator + 1) != Some(&0) {
        return Ok(None);
    }
    let mut decoder = ZlibDecoder::new(&payload[separator + 2..]);
    let mut decoded = Vec::new();
    decoder
        .read_to_end(&mut decoded)
        .map_err(|_| "No se pudo descomprimir metadata PNG".to_string())?;
    if decoded.len() > MAX_METADATA_BYTES {
        return Err("Metadata PNG demasiado grande".into());
    }
    Ok(String::from_utf8(decoded).ok())
}

fn international_text_chunk(payload: &[u8]) -> Result<Option<String>, String> {
    let Some(keyword_end) = payload.iter().position(|value| *value == 0) else {
        return Ok(None);
    };
    if &payload[..keyword_end] != b"chara" || keyword_end + 3 >= payload.len() {
        return Ok(None);
    }
    let compressed = payload[keyword_end + 1] == 1;
    let mut cursor = keyword_end + 3;
    for _ in 0..2 {
        let Some(end) = payload[cursor..].iter().position(|value| *value == 0) else {
            return Ok(None);
        };
        cursor += end + 1;
    }
    let text = if compressed {
        let mut decoder = ZlibDecoder::new(&payload[cursor..]);
        let mut decoded = Vec::new();
        decoder
            .read_to_end(&mut decoded)
            .map_err(|_| "No se pudo descomprimir metadata PNG".to_string())?;
        decoded
    } else {
        payload[cursor..].to_vec()
    };
    if text.len() > MAX_METADATA_BYTES {
        return Err("Metadata PNG demasiado grande".into());
    }
    Ok(String::from_utf8(text).ok())
}

#[cfg(test)]
mod tests {
    use super::import;
    use std::fs;

    #[test]
    fn imports_v2_json_and_limits_fields() {
        let path = std::env::temp_dir().join(format!(
            "local-character-card-{}.json",
            uuid::Uuid::new_v4()
        ));
        fs::write(&path, br#"{"spec":"chara_card_v2","data":{"name":"Sophie","description":"desc","first_mes":"Hola","tags":["es"],"alternate_greetings":["Hey"],"character_book":{"entries":[{"keys":["casa"],"content":"lore"}]}}}"#).expect("write card");
        let result = import(&path).expect("import card");
        let _ = fs::remove_file(path);
        assert_eq!(result.name, "Sophie");
        assert_eq!(result.greeting, "Hola");
        assert_eq!(result.tags, vec!["es"]);
        assert_eq!(result.lore.len(), 1);
    }
}
