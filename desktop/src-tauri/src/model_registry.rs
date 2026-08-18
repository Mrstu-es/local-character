use crate::models::{ModelMetadata, ModelRecord};
use std::fs::File;
use std::io::{self, Read};
use std::path::Path;

const MAX_METADATA_STRING: u64 = 2 * 1024 * 1024;

pub fn inspect_gguf(path: &Path) -> Result<ModelRecord, String> {
    if path
        .extension()
        .and_then(|extension| extension.to_str())
        .map(|value| value.to_ascii_lowercase())
        != Some("gguf".into())
    {
        return Err("El archivo seleccionado no tiene extensión .gguf".into());
    }
    let metadata =
        std::fs::metadata(path).map_err(|error| format!("No se pudo leer el archivo: {error}"))?;
    if !metadata.is_file() {
        return Err("La ruta seleccionada no es un archivo".into());
    }
    let parsed = parse_header(path)?;
    Ok(parsed.into_record(path, metadata.len() as i64))
}

fn parse_header(path: &Path) -> Result<ModelMetadata, String> {
    let mut file =
        File::open(path).map_err(|error| format!("No se pudo abrir el GGUF: {error}"))?;
    let mut magic = [0_u8; 4];
    file.read_exact(&mut magic)
        .map_err(|error| format!("GGUF incompleto: {error}"))?;
    if &magic != b"GGUF" {
        return Err("Cabecera GGUF inválida: no empieza por GGUF".into());
    }
    let version = read_u32(&mut file).map_err(io_error)?;
    if !(1..=3).contains(&version) {
        return Err(format!("Versión GGUF no soportada: {version}"));
    }
    let tensor_count = read_u64(&mut file).map_err(io_error)?;
    let metadata_count = read_u64(&mut file).map_err(io_error)?;
    let mut result = ModelMetadata {
        gguf_version: version,
        tensor_count,
        metadata_count,
        ..Default::default()
    };
    for _ in 0..metadata_count {
        let key = read_string(&mut file).map_err(io_error)?;
        let value_type = read_u32(&mut file).map_err(io_error)?;
        let value = read_value(&mut file, value_type).map_err(io_error)?;
        match key.as_str() {
            "general.name" => result.name = value.as_string(),
            "general.architecture" => result.architecture = value.as_string(),
            "general.quantization_version" => {
                result.quantization = value.as_i64().map(|number| format!("GGUF v{number}"))
            }
            "general.context_length" => result.context_length = value.as_i64(),
            key if key.ends_with(".context_length") => {
                result.context_length = value.as_i64().or(result.context_length)
            }
            "tokenizer.chat_template" => result.chat_template = value.as_string(),
            _ => {}
        }
    }
    Ok(result)
}

#[derive(Debug)]
enum GgufValue {
    String(String),
    Signed(i64),
    Unsigned(u64),
    Float,
    Bool,
    Array,
}

impl GgufValue {
    fn as_string(self) -> Option<String> {
        match self {
            Self::String(value) => Some(value),
            _ => None,
        }
    }

    fn as_i64(self) -> Option<i64> {
        match self {
            Self::Signed(value) => Some(value),
            Self::Unsigned(value) => i64::try_from(value).ok(),
            _ => None,
        }
    }
}

fn read_value<R: Read>(reader: &mut R, value_type: u32) -> io::Result<GgufValue> {
    Ok(match value_type {
        0 => GgufValue::Unsigned(read_u8(reader)? as u64),
        1 => GgufValue::Signed(read_u8(reader)? as i8 as i64),
        2 => GgufValue::Unsigned(read_u16(reader)? as u64),
        3 => GgufValue::Signed(read_u16(reader)? as i16 as i64),
        4 => GgufValue::Unsigned(read_u32(reader)? as u64),
        5 => GgufValue::Signed(read_u32(reader)? as i32 as i64),
        6 => {
            let _ = f32::from_le_bytes(read_array(reader)?);
            GgufValue::Float
        }
        7 => {
            let _ = read_u8(reader)?;
            GgufValue::Bool
        }
        8 => GgufValue::String(read_string(reader)?),
        9 => {
            let element_type = read_u32(reader)?;
            let length = read_u64(reader)?;
            if length > 1_000_000 {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidData,
                    "GGUF array demasiado grande",
                ));
            }
            for _ in 0..length {
                let _ = read_value(reader, element_type)?;
            }
            GgufValue::Array
        }
        10 => GgufValue::Unsigned(read_u64(reader)?),
        11 => GgufValue::Signed(read_u64(reader)? as i64),
        12 => {
            let _ = f64::from_le_bytes(read_array(reader)?);
            GgufValue::Float
        }
        _ => {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                format!("tipo GGUF desconocido: {value_type}"),
            ))
        }
    })
}

fn read_string<R: Read>(reader: &mut R) -> io::Result<String> {
    let length = read_u64(reader)?;
    if length > MAX_METADATA_STRING {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "cadena GGUF demasiado grande",
        ));
    }
    let mut bytes = vec![0_u8; length as usize];
    reader.read_exact(&mut bytes)?;
    String::from_utf8(bytes).map_err(|error| io::Error::new(io::ErrorKind::InvalidData, error))
}

fn read_array<R: Read, const N: usize>(reader: &mut R) -> io::Result<[u8; N]> {
    let mut bytes = [0_u8; N];
    reader.read_exact(&mut bytes)?;
    Ok(bytes)
}

fn read_u8<R: Read>(reader: &mut R) -> io::Result<u8> {
    Ok(read_array::<_, 1>(reader)?[0])
}

fn read_u16<R: Read>(reader: &mut R) -> io::Result<u16> {
    Ok(u16::from_le_bytes(read_array(reader)?))
}

fn read_u32<R: Read>(reader: &mut R) -> io::Result<u32> {
    Ok(u32::from_le_bytes(read_array(reader)?))
}

fn read_u64<R: Read>(reader: &mut R) -> io::Result<u64> {
    Ok(u64::from_le_bytes(read_array(reader)?))
}

fn io_error(error: io::Error) -> String {
    format!("GGUF metadata inválida: {error}")
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    #[test]
    fn reads_little_endian_numbers() {
        let mut cursor = Cursor::new(vec![1, 0, 0, 0, 4, 0, 0, 0]);
        assert_eq!(read_u32(&mut cursor).unwrap(), 1);
        assert_eq!(read_u32(&mut cursor).unwrap(), 4);
    }

    #[test]
    fn rejects_oversized_strings() {
        let mut bytes = Vec::new();
        bytes.extend_from_slice(&(MAX_METADATA_STRING + 1).to_le_bytes());
        assert!(read_string(&mut Cursor::new(bytes)).is_err());
    }
}
