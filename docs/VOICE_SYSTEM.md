# Sistema de voces

## Motores

La aplicación usa una interfaz neutral `TtsEngine`. La implementación local principal es `SherpaOnnxTtsEngine`, integrada con el AAR oficial sherpa-onnx 1.13.5. Acepta modelos ONNX preparados para sherpa-onnx de tipo Kokoro, Piper/VITS y VITS. `AndroidSystemTtsEngine` es un fallback opcional y usa las voces que tenga instaladas el proveedor TTS del sistema.

El artefacto versionado es `app/libs/sherpa-onnx-1.13.5.aar`, SHA-256 `6419cd8bc983e0c4fab06067f0fe0313fdc0f7103818ac1e7a08d50787b7a82b`, publicado por k2-fsa bajo Apache-2.0.

Una voz sherpa descargada no necesita Internet para sintetizar. El modelo GGUF genera primero el texto; después `TtsTextSanitizer` selecciona diálogo/acciones y `TtsManager` sintetiza y reproduce. TTS nunca modifica `PromptBuilder` ni la personalidad.

`TtsManager` vive una sola vez en `AppContainer`, mantiene como máximo una voz pesada, reutiliza un `AudioTrack`, cambia la voz al cambiar de personaje, cancela el audio anterior y libera el modelo ante presión de memoria. Carga, síntesis, conversión PCM, descarga y reproducción bloqueante se ejecutan fuera de Main Thread.

## Configuración

- **Ajustes → Voces**: auto reproducción global, fallback Android, descarga de RAM cuando queda inactiva, prueba del sistema y voces instaladas.
- **Personaje → Voz y contenido**: voz asignada, override Global/Siempre/Nunca, velocidad, tono, volumen y qué leer.
- Cada respuesta completa del personaje muestra reproducir/detener. Los mensajes del usuario y respuestas todavía en streaming no muestran audio.
- Si una voz asignada fue borrada o falta, se usa el TTS del sistema sólo si el fallback está activo; en caso contrario no se reproduce nada y nunca se cierra la app.

El tono depende del motor. Android TTS lo admite; los modelos sherpa actuales pueden ignorarlo. Velocidad y volumen se aplican siempre dentro de los límites de la UI.

## Repositorios abiertos

Un repositorio publica `voice-repository.json`:

```json
{
  "schema": "localcharacter.voice.repository",
  "version": 1,
  "name": "Spanish Voices",
  "description": "Voces compatibles",
  "voicesIndex": "voices.json"
}
```

El índice usa archivos explícitos para soportar modelos que necesitan más de dos recursos:

```json
{
  "version": 1,
  "voices": [{
    "id": "voice-es-001",
    "name": "Voz Española 01",
    "language": "es",
    "engine": "piper",
    "sizeBytes": 123456,
    "license": "CC-BY-4.0",
    "author": "Nombre del autor",
    "creator": "Nombre del creador",
    "source": "https://example.org/project",
    "version": "1.0",
    "files": [
      {
        "role": "model",
        "url": "voice/model.onnx",
        "relativePath": "model.onnx",
        "sizeBytes": 120000,
        "sha256": "64-caracteres-hexadecimales"
      },
      {
        "role": "tokens",
        "url": "voice/tokens.txt",
        "relativePath": "tokens.txt",
        "sizeBytes": 3456,
        "sha256": "64-caracteres-hexadecimales"
      }
    ]
  }]
}
```

Roles admitidos: `model`, `tokens`, `voices`, `config`, `lexicon`, `data`, `rule_fst` y `rule_far`. Kokoro requiere `model`, `tokens` y `voices`; Piper/VITS requieren `model` y `tokens`. Los archivos `data` permiten publicar `espeak-ng-data` como archivos individuales bajo una ruta relativa común.

## Seguridad e instalación

La URL se añade en **Ajustes → Voces → Repositorios de voces**. Manifiesto, índice, archivos y muestras deben usar HTTPS, puerto 443, sin credenciales y el mismo host declarado. No se siguen redirecciones durante descargas de modelos.

Cada voz exige tamaño total coherente, SHA-256 por archivo, rutas relativas sin traversal y extensiones permitidas por rol. Se rechazan APK, DEX, JAR, clases, bibliotecas nativas, scripts y ejecutables. Una voz declarada como imitación de una persona real debe incluir consentimiento confirmado y evidencia no vacía; la app no clona voces.

La instalación es:

```text
directorio temporal
→ descarga acotada
→ tamaño exacto
→ SHA-256
→ metadata/licencia
→ rename del directorio
→ transacción Room
```

Si cualquier paso falla se eliminan el temporal y el destino compensatorio. `VoiceEntity` guarda rutas privadas, hash conjunto, licencia, fuente, autor, versión y archivos. Eliminar un repositorio no elimina voces ya instaladas. Eliminar una voz pone en `NULL` las asignaciones mediante la clave foránea y limpia únicamente su directorio privado verificado.

## Character Card

Character Card V2 permanece estándar. La recomendación opcional se guarda en `extensions.localcharacter.recommendedVoiceId`. La recomendación nunca es obligatoria y no duplica el modelo por personaje.

## Límites actuales

- No se incluye una voz pesada dentro del APK; las voces locales se obtienen desde repositorios añadidos por el usuario.
- El MVP sintetiza después de terminar la respuesta, no durante el streaming de tokens.
- No hay caché de audio persistente ni clonación de voz.
- `sampleUrl` se conserva y valida, pero la escucha remota previa a la descarga todavía no tiene reproductor propio; una voz instalada sí puede probarse.
