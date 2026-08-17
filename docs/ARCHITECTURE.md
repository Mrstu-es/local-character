# Arquitectura

## Principios

Local Character es local-first: personajes, historial, memorias, lore, relación, configuración y métricas permanecen en el dispositivo. El usuario puede ejecutar GGUF sin conexión o elegir explícitamente una API BYOK; en ese caso solo se envía al proveedor el prompt y contexto seleccionado para ese turno. Compose depende de ViewModels y modelos de dominio; Room, DataStore, red, proveedores LLM y JNI quedan detrás de repositorios e interfaces. Las inferencias y operaciones de red nunca se ejecutan en el hilo principal.

## Flujo de chat

1. `ChatViewModel` guarda inmediatamente el mensaje del usuario.
2. `MemoryOrchestrator` atiende intenciones manuales deterministas: “recuerda que” crea una memoria fijada y “olvida…” desactiva coincidencias.
3. `MemoryRetriever` puntúa únicamente memorias del personaje, persona y alcance actuales.
4. `PromptBuilder` combina personaje, relación, memorias recuperadas, lore, resumen, historial reciente y mensaje actual dentro del presupuesto.
5. `AiProviderManager` resuelve Conversation → Character → Global, aplica `Solo modelos locales` y entrega un `LlmRequest` neutral al `LlmProvider` elegido.
6. Local usa `LlmTaskQueue`/`LlamaCppEngine`; online usa el adapter HTTP/SSE específico. Ambos producen los mismos `LlmStreamEvent`.
7. `StreamingTextBuffer` agrupa actualizaciones visuales a un máximo aproximado de una cada 32 ms y mantiene la respuesta parcial fuera de Room.
8. La respuesta completa se escribe una sola vez y `ApiUsageTracker` conserva usage/latencia/costo disponible. Después, una coroutine local ejecuta extracción, consolidación, relación y, solo si toca, resumen. Cualquier fallo de este trabajo no afecta el chat.

## Memory Architecture

### Persistencia

Room v6 contiene `characters`, `character_sources`, `conversations`, `messages`, `memories`, `conversation_summaries`, `character_relationships`, `pending_events`, `lore_entries`, `models`, `user_personas`, `ai_usage`, `voice_repositories`, `voices` y `character_preferences`.

`MemoryEntity` admite `FACT`, `EVENT`, `PREFERENCE`, `RELATIONSHIP`, `EMOTIONAL`, `GOAL`, `PROMISE`, `CHARACTER_RELATIONSHIP` y `SHARED_EVENT`. Conserva contenido normalizado, importancia/confianza 0–1, procedencia, fechas de creación/actualización/acceso/evento/caducidad, contador de accesos, mensaje fuente, fijado, activo e historial de sustitución.

El alcance lógico es `characterId + userPersonaId + conversationId`. Si “Compartir memoria entre chats” está activo, `conversationId` es nulo y la memoria se comparte solo para el mismo personaje y persona. Está desactivado por defecto.

### `MemoryExtractionService`

Usa el mismo GGUF local después de la respuesta principal. Solicita JSON estricto, fecha absoluta para expresiones relativas y procedencia (`USER_STATED_FACT`, `CHARACTER_STATED`, `CHARACTER_INFERENCE`, `SYSTEM_INFERENCE`). `MemoryParser` elimina fences, localiza el primer objeto balanceado, valida tipos/campos/rangos, descarta texto trivial y devuelve una lista vacía ante JSON inválido.

Los niveles Mínimo/Normal/Detallado aplican umbrales 0.65/0.45/0.30. Los eventos futuros sin caducidad explícita caducan dos días después de su fecha; su `PendingEvent` mantiene por separado el seguimiento.

### `MemoryDeduplicator` y `MemoryConflictResolver`

El deduplicador combina tokens canónicos, Jaccard, contención y tipo para reconocer paráfrasis sin embeddings. El resolver:

- fusiona equivalentes elevando importancia/confianza y preservando fijado;
- sustituye hechos estables cuando hay señales de cambio, conservando la fila anterior inactiva mediante `supersededById`;
- permite coexistir eventos, emociones, promesas y experiencias compartidas.

La arquitectura de búsqueda depende de `MemorySearchEngine`; hoy se inyecta `KeywordMemorySearchEngine` y una futura implementación por embeddings no requiere cambiar `MemoryRetriever`.

### `MemoryRetriever`

Recibe el mensaje actual, conversación reciente y candidatos ya aislados por alcance. El score combina coincidencia textual, contexto reciente, nombres propios, tipo de relación, importancia, recencia, accesos y fijado. Solo entrega los mejores 4/8/12 según el nivel. Las memorias incluidas incrementan `accessCount`; las caducadas o inactivas nunca entran.

### `PendingEventManager`

Convierte memorias `EVENT`, `GOAL` o `PROMISE` con fecha en eventos pendientes. Después de la fecha quedan disponibles como seguimiento opcional. `PromptBuilder` dice expresamente que solo pregunte si resulta natural. Si la respuesta pregunta por el evento se registra `followUpAskedAt` y un cooldown de tres días; si el usuario comunicó un resultado, se resuelve.

### `RelationshipManager`

Persiste por personaje, conversación y persona niveles internos de confianza, afecto, familiaridad y tensión. Los cambios por turno están limitados a incrementos pequeños. El prompt recibe una descripción cualitativa, nunca los números, y la UI tampoco expone métricas internas.

### `ConversationSummarizer`

A partir de 60 mensajes, cuando hay al menos 30 sin resumir, resume un bloque antiguo y conserva 24 mensajes recientes directos. El prompt de resumen exige eventos, relaciones, decisiones, emociones, cambios, promesas y conflictos. Cada ventana guarda IDs y fechas; ningún mensaje original se elimina o reemplaza.

### `LlmTaskQueue`

El único contexto llama.cpp se protege con una cola y mutex:

```text
CHAT_GENERATION > MEMORY > SUMMARY
```

Una tarea de chat llama `stopGeneration()` y cancela trabajo interno de menor prioridad antes de adquirir el motor. JNI conserva además su mutex nativo y bandera atómica de cancelación.

## Prompt y presupuesto

La salida conceptual es:

```text
SYSTEM + CHARACTER + USER PERSONA + CONTENT MODE + PERSONALITY + SCENARIO
RELATIONSHIP STATE
RELEVANT LONG-TERM MEMORIES
OPTIONAL UNRESOLVED EVENTS
RELEVANT LORE
EARLIER CONVERSATION SUMMARY
RECENT CONVERSATION
CURRENT USER MESSAGE / TEMPORARY GENERATION MODE
```

La identidad esencial, definición del personaje y mensaje actual nunca se truncan. El presupuesto restante prioriza historial reciente, memorias, lore y resumen. Las instrucciones prohíben mencionar una base de memoria, inventar recuerdos o forzarlos en temas no relacionados.

`TemplateVariableResolver` sustituye únicamente `{{user}}` y `{{char}}` completos. `GenerationMode.CHARACTER_CONTINUE` no fabrica un mensaje de usuario: agrega una instrucción efímera al system prompt y mantiene todos los mensajes reales como historial. `ContentPolicyResolver` aplica override de personaje antes del ajuste global; no intenta alterar las políticas de un proveedor remoto.

## Voz y postprocesado

```text
Respuesta completa → RoleplayTextParser → TtsTextSanitizer → TtsManager
                                                       ├─ SherpaOnnxTtsEngine
                                                       └─ AndroidSystemTtsEngine
```

`TtsManager` es process-wide y conserva una sola voz local cargada. Room sólo guarda una referencia `voiceId` por personaje, de modo que varias identidades comparten el mismo modelo. La reproducción no escribe Room por progreso. Detalles de formatos, seguridad y offline en `docs/VOICE_SYSTEM.md`.

## UI y configuración

Desde el chat se abre “Memoria local”, agrupada en Recuerdos, Relaciones, Eventos y Preferencias. Permite editar, borrar, fijar y desfijar texto entendible. El `CharacterChatTopBar` permanece fijo y abre el detalle al pulsar avatar/nombre. El composer mantiene su estado aislado de la lista. El historial observa inicialmente los 100 mensajes más recientes y carga bloques anteriores a petición.

Ajustes incluye memoria inteligente, seguimientos, resúmenes, nivel, alcance compartido, repositorios y **Proveedores de IA**. DataStore conserva selección global/por personaje/por conversación, caché de modelos, favoritos, fallback y presupuesto; las credenciales nunca entran en DataStore. **Modelos** unifica GGUF y catálogos online. El detalle de personaje expone su motor y el chat permite override inmediato sin crear otra conversación.

## Migración

`MIGRATION_1_2` crea una tabla de memorias ampliada, copia todas las filas v1 con valores conservadores, reemplaza la tabla y crea resúmenes, relaciones y eventos. Preserva conversaciones/mensajes, usa claves foráneas e índices y no existe `fallbackToDestructiveMigration`.

`MIGRATION_2_3` añade `character_sources` con identidad única por proveedor/ID remoto, hashes y rutas locales. No modifica personajes, chats, memorias ni modelos existentes.

`MIGRATION_3_4` conserva todos los modelos y añade tensores, parámetros, tokenizer, plantilla embebida y selección manual por modelo. No existe migración destructiva.

`MIGRATION_4_5` añade `ai_usage` e índices por proveedor, modelo, fecha, conversación y personaje sin modificar ninguna tabla previa. No existe migración destructiva.

`MIGRATION_5_6` añade la extensión opcional de voz recomendada, reconstruye `user_personas` conservando filas y asignando una predeterminada, y crea repositorios/voces/preferencias con índices y claves foráneas. No toca personajes, conversaciones, mensajes, memorias, lore, modelos ni métricas existentes.

## Proveedores LLM

`LlmProvider` define capabilities, conexión barata, listado dinámico, generación `Flow<LlmStreamEvent>` y cancelación. `LocalLlamaProvider` adapta el `LlmEngine` existente; Groq, OpenRouter y Mistral reutilizan la base OpenAI-compatible con overrides; Gemini, OpenAI Responses y Anthropic tienen mappers nativos; Custom permite otro servidor Chat Completions.

`PromptBuilder` es único y devuelve dos representaciones simultáneas: el prompt plano legado para llama.cpp y `systemPrompt + List<LlmMessage>` para cualquier adapter remoto. La identidad nunca conoce `providerId`.

El cliente OkHttp compartido desactiva reintentos de conexión para generación, deja abierto el read timeout del stream y delega deadlines de primer token/total a `ChatViewModel`. El parser SSE implementa eventos multilinea y `[DONE]`. Fallback automático está desactivado por defecto y solo se permite antes de texto ante red, timeout, 429, 404 de modelo o 5xx; credenciales/facturación nunca cambian de proveedor silenciosamente.

`ApiCredentialStore` cifra cada key con AES/GCM y una clave no exportable de Android Keystore. `AiUsageRepository` guarda exclusivamente metadata de uso; los logs debug contienen proveedor/estado/latencia, nunca keys, prompts o mensajes. Detalles en `docs/AI_PROVIDERS.md`.

## Catálogos públicos

`CharacterCatalogProvider` separa por completo DTO remotos y modelos de dominio. `CharacterCatalogManager` registra fuentes y aísla fallos. AI Character Cards es el primer proveedor remoto completo; Local usa el mismo contrato y las fuentes sin API fiable se anuncian como no compatibles. La descarga pasa por validación HTTPS/host/tamaño/firma y `CharacterInstaller` coordina archivos privados con una transacción Room. Detalles, decisiones y mecanismo de extensión están en `docs/CHARACTER_PROVIDERS.md`.

## LLM, JNI y GGUF

`LlmEngine` abstrae carga, descarga, generación por propósito, parada y estado. `LlamaCppEngine` usa llama.cpp fijado en `b10434` (`7e4c0a96880dae4fc4268ad441f8a6446bd5460a`). JNI mantiene un modelo/contexto, CPU, sampler chain, streaming y cancelación atómica; `ggml_abort_callback` permite cortar también un `llama_decode()` que sigue dentro del grafo CPU.

La carga usa mmap para los pesos originales y permite al backend crear buffers extra repacked; así obtiene kernels optimizados sin copiar todo el GGUF al heap. Conserva I/O directo con repacking como fallback. SAF abre primero el GGUF desde `/proc/self/fd/<n>`; si el proveedor no permite las operaciones que llama.cpp necesita, `ModelStorageManager` crea una copia privada verificada y estable. La copia se registra en Room y se elimina junto al modelo. Errores definitivos de arquitectura, tensores, archivo truncado o memoria insuficiente no disparan una copia inútil. Antes de activar el registro de Room, una inferencia breve verifica que el modelo realmente decodifica texto; el último activo vuelve a cargarse tras reiniciar el proceso.

`ModelLoadPolicy` trata `context_length` como capacidad máxima: usa el contexto configurado por el usuario, limitado a 2048 en móvil y por el propio modelo, en vez de reservar automáticamente 32K/128K. La carga usa hasta cuatro hilos y batch 512. `llama-common` aplica con Minja/Jinja la plantilla incorporada en cada GGUF en modo `AUTO`; por modelo puede elegirse ChatML, Llama 3, Qwen, Gemma, Raw o Custom. El parser reconoce dinámicamente cualquier `*.context_length`, calcula parámetros desde las dimensiones de tensores y no mantiene una whitelist de arquitecturas. El binario arm64 carga dinámicamente la mejor variante para el SoC (incluidos DOTPROD, FP16, KleidiAI y OpenMP), y el streaming espera fragmentos UTF-8 completos antes de enviarlos a Kotlin. JNI añade un deadline de prefill que comparte el callback de aborto con la cancelación manual.

## Rendimiento observable

La UI usa listas lazy con IDs y `contentType`, avatares decodificados a 256×256 con un único ImageLoader, transformaciones recordadas y autoscroll solo si el usuario sigue cerca del final. La inferencia bloqueante usa un executor dedicado y deja dos núcleos disponibles en dispositivos de seis o más núcleos. La instrumentación continua de frames/imágenes queda desconectada del APK normal para no afectar equipos reales; permanecen logs puntuales de Room y errores.
## Chats grupales

Los chats grupales viven en `GroupRepository` y no reutilizan la tabla `conversations` para evitar introducir una clave de personaje artificial en los chats directos. `GroupChatViewModel` reutiliza el motor de proveedores, `PromptBuilder`, memoria, TTS, compositor y políticas de rendimiento. `GroupSpeakerSelector` y `GroupTurnOrchestrator` mantienen la selección contextual y los límites de generación.
