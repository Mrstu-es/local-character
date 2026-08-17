# Progreso

## Hotfix 0.6.1: chat Android y Next remoto

- Corregido el cierre al enviar un mensaje: Android ICU rechazaba las llaves finales sin escapar en la expresión regular de `TemplateVariableResolver`, aunque el motor regex de la JVM de escritorio aceptaba el patrón.
- `ChatViewModel` ahora contiene también los errores de preparación del prompt y los presenta en la interfaz en vez de finalizar el proceso.
- `Next` envía a proveedores online una instrucción efímera de continuación como último elemento de la solicitud. No crea un `ChatMessage` ni escribe un mensaje USER en Room.
- Validado en Xiaomi M2102K1C: envío normal y Next recibieron HTTP 200 con Groq; la app permaneció abierta y Next incrementó Room de 6 a 7 mensajes, solamente la respuesta del personaje.

Última actualización: 2026-08-15

## Base de la aplicación

Implementado: Android nativo, Kotlin, Compose/Material 3, navegación, temas, onboarding, personajes, Character Cards JSON/PNG, lore, conversaciones persistentes, modelos GGUF por SAF, llama.cpp/JNI CPU, streaming y cancelación. llama.cpp está fijado en `b10434`, commit `7e4c0a96880dae4fc4268ad441f8a6446bd5460a`, para `arm64-v8a` y `x86_64`.

La carga de modelos ahora combina mmap con buffers repacked del backend CPU optimizado, conserva I/O directo y copia privada como fallbacks, muestra el error nativo real, respeta el contexto configurado y aplica automáticamente el chat template Jinja completo del GGUF. Qwen3 y Llama 3.2 están incluidos entre las arquitecturas de la versión integrada.

## Rendimiento, chat y modelos 0.4.0

- la respuesta parcial permanece en memoria, se publica visualmente cada ~32 ms y se persiste una sola vez al terminar;
- Room observa 100 mensajes recientes y el usuario puede cargar bloques anteriores de 100 sin perder historial;
- el autoscroll solo sigue la generación cerca del final; al desplazarse arriba aparece “Ir al final” y no se fuerza la posición;
- `CharacterChatTopBar` fijo muestra avatar de 52 dp, nombre truncado, privacidad, memoria y regeneración; avatar/nombre abre el personaje;
- el composer mantiene estado propio y no recompone la conversación por cada tecla;
- avatares a 256×256 con un ImageLoader compartido, caché de memoria/disco y fallback por inicial;
- listas con claves estables, `contentType`, paginación de catálogos y transformaciones recordadas;
- logs debug `LocalPerformance` para frames lentos, recomposiciones, ítems visibles, imágenes y emisiones Room;
- selector SAF sin whitelist de nombres/arquitecturas, metadata dinámica y cálculo de parámetros desde tensores;
- plantillas por modelo: Auto/ChatML/Llama 3/Qwen/Gemma/Raw/Custom, descarga explícita de RAM e información detallada;
- Room v4 y migración no destructiva 3→4.

Corrección 0.4.1:

- la cancelación nativa se comprueba también durante el prefill y ya no se sobrescribe antes del muestreo;
- una tarea automática de memoria puede ser interrumpida inmediatamente por el chat siguiente;
- el worker JNI está separado de `Dispatchers.Default` y la carga reserva dos núcleos para UI/sistema en teléfonos de seis o más núcleos;
- extracción automática limitada a 160 tokens y diagnóstico continuo de frames/imágenes retirado del runtime normal;
- el mensaje temporal se limpia en `finally` ante éxito, error, cancelación o timeout de cinco minutos.

Corrección 0.4.2:

- `ggml_abort_callback` interrumpe el cálculo dentro de `llama_decode`, eliminando la espera prolongada en «Deteniendo inferencia»;
- la carga móvil queda limitada a 4 hilos y batch 128, también para configuraciones antiguas ya persistidas;
- un prompt de Character Card demasiado grande se recorta al contexto real antes de entrar a JNI;
- timeout de 75 segundos hasta el primer token y 3 minutos totales, con limpieza inmediata del indicador de escritura;
- el trabajo autónomo de memoria espera 3 segundos y sigue siendo cancelable/preemptible;
- Ajustes incluye idioma de contenido persistente con todos los idiomas publicados por AI Character Cards; Español es el valor predeterminado;
- el idioma se envía al proveedor remoto, filtra `repository.json`, aparece en Explorar y guía el idioma de respuesta local;
- los modelos muestran por separado el contexto máximo declarado y el contexto que se usará al recargar.

Corrección 0.4.3:

- `AUTO` usa el procesador Minja/Jinja completo de `llama-common`, necesario para plantillas embebidas modernas como Qwen3 y Llama 3.x;
- se eliminó una segunda aceptación accidental del mismo token en el sampler, que alteraba las penalizaciones y degradaba la generación;
- los fragmentos de tokens se acumulan hasta formar UTF-8 válido antes de cruzar JNI;
- Android arm64 compila los kernels KleidiAI y OpenMP de la integración oficial; la carga intenta primero buffers con repacking y conserva mmap como fallback de memoria;
- `n_ubatch` queda fijado al batch móvil y la caché KV se limpia sin reinicialización costosa;
- antes de mostrar un modelo como activo se ejecuta una prueba real y acotada de generación; un check ya significa que el GGUF produjo texto;
- al reiniciar la aplicación se vuelve a cargar y verificar el último modelo, o se elimina el estado activo engañoso si falla.

Corrección 0.4.6 validada en un Xiaomi M2102K1C con Android 14 y Snapdragon 888:

- el backend ARM se distribuye con variantes CPU y selecciona en ejecución `armv8.2_2`, con DOTPROD/FP16 y kernels KleidiAI para el SoC real;
- las bibliotecas nativas se extraen físicamente y `llama.cpp` carga los backends dinámicos desde `nativeLibraryDir` antes de inicializar el modelo;
- mmap mantiene los pesos originales fuera del heap y `use_extra_bufts` crea únicamente el buffer repacked optimizado; se evitó el swap de aproximadamente 1,2 GB que volvía lenta toda la interfaz;
- el contexto móvil queda limitado a 2048, el batch efectivo sube a 512 y el worker nativo usa prioridad normal;
- un deadline nativo adicional corta el prefill si el backend no devuelve control, además de los timeouts de Kotlin;
- el prefill de la prueba de 16 tokens bajó de 15.159 ms a 117–147 ms; un prompt real de personaje con 363 tokens produjo el primer token en 2.898 ms;
- después de calentar las cinco secciones, la navegación física registró 232 frames, 2,16 % de frames lentos, mediana de 6 ms y percentil 90 de 7 ms;
- se corrigió la carrera entre el mensaje streaming y el mensaje ya persistido: Room ya no puede crear dos claves iguales en `LazyColumn` al finalizar una respuesta;
- prueba física completa: Luna respondió, el mensaje quedó persistido y el proceso continuó abierto sin `AndroidRuntime` ni indicador de escritura infinito.

## Multiproveedor 0.5.0

IMPLEMENTADO:

- `LlmProvider`, `ProviderCapabilities`, `LlmRequest`, `LlmModelInfo`, pricing y eventos de streaming comunes;
- adaptación no destructiva del motor GGUF existente mediante `LocalLlamaProvider`;
- Groq, OpenRouter, Gemini REST, OpenAI Responses, Anthropic Messages, Mistral Chat Completions y Custom OpenAI-compatible;
- un único `PromptBuilder`: personalidad, lore, memoria, relación e historial son independientes del cerebro elegido;
- API Keys BYOK cifradas AES/GCM con una clave no exportable de Android Keystore, enmascaradas y excluidas de backup/Git;
- pantalla Ajustes → Proveedores de IA, prueba barata, caché de modelos de 24 h, refresh, estados y custom LAN;
- pantalla Modelos unificada con buscador online, Gratis/Free tier/Pago, favoritos, contexto, precio disponible y confirmación de pago;
- selección Global → Personaje → Conversación, selector rápido en chat y modo estricto Solo modelos locales;
- streaming/cancelación común, parser SSE robusto, mensajes de privacidad local/online y persistencia final única;
- error mapper 401/402/403/404/408/429/5xx/red, sin retry automático ni doble cobro silencioso;
- fallback configurable y OFF por defecto; solo antes de texto y nunca por autenticación/facturación;
- Room v5 con `ai_usage`, tokens reales, costo estimado, TTFT/duración, presupuesto mensual, aviso y bloqueo opcional;
- documentación técnica y de configuración en `docs/AI_PROVIDERS.md`.
- creación y edición de personajes con foto elegida mediante el selector visual de Android, vista previa y copia validada en almacenamiento privado; no requiere permiso general sobre la galería.

PROBADO:

- contratos HTTP, headers, cuerpos y SSE de los siete adapters online con MockWebServer, sin claves ni cargos;
- selección, local-only, fallback, pricing OpenRouter, costo, URL LAN, errores y SSE mediante tests unitarios;
- compilación de pruebas instrumentadas para tracking de uso y migración Room 4→5.

PARCIAL:

- capabilities de vision/tools/reasoning/structured output están modeladas, pero el chat 0.5.0 envía texto solamente;
- una credencial por proveedor; la estructura permite ampliación futura;
- costo exacto solo cuando el catálogo aporta ambos precios y la respuesta aporta usage;
- los catálogos generales pueden incluir un modelo no conversacional porque algunas APIs no publican capacidades por modelo.

NO SOPORTADO en este milestone:

- incluir keys dentro del APK, reintentos pagados automáticos, scraping de precios o listas estáticas de modelos “gratis”;
- llamadas reales a cuentas remotas sin que el usuario configure su propia API Key.

## Persona, Acción, Next, contenido y voces 0.6.0

IMPLEMENTADO:

- perfil principal en **Ajustes → Mi perfil**, con nombre, descripción limitada, avatar privado y tabla preparada para múltiples `UserPersona`;
- asignación persistente de `userPersonaId` a cada conversación y aislamiento existente de memorias/relación/eventos por persona;
- `TemplateVariableResolver` seguro para `{{user}}` y `{{char}}`, resuelto en cada prompt con una sección USER PERSONA de alta prioridad;
- composer moderno con `ComposerMode.NORMAL/ACTION/NARRATION`, botón `**`, estado visual y formato sin duplicar asteriscos;
- botón Next independiente y `GenerationMode.CHARACTER_CONTINUE`: conserva el borrador, bloquea generación simultánea y nunca escribe un mensaje USER artificial;
- las respuestas Next sí se guardan y pasan por memoria, relación, seguimientos y resumen;
- `ContentMode.STANDARD/ADULT_ENABLED` OFF/estándar por defecto, confirmación adulta, filtro forzado en Explorar y `CharacterContentOverride`;
- clasificación `ContentRating` separada del ajuste de habilitación y sin inferencias por nombre/avatar/tags;
- sherpa-onnx 1.13.5 oficial para Kokoro, Piper/VITS y VITS, con Android System TTS como fallback;
- `TtsManager` único, una voz pesada, síntesis/reproducción fuera de Main, cancelación, low-memory y `AudioTrack` reutilizable;
- parser pequeño de roleplay, lectura de sólo diálogo o diálogo+acciones, Auto TTS global/por personaje y audio reproducir/detener por mensaje;
- Room v6: `voice_repositories`, `voices`, `character_preferences`, UserPersona completa y voz recomendada namespaced para Character Card V2;
- repositorios de voces HTTPS con búsqueda/filtros, metadata de licencia/fuente/autor/consentimiento y eliminación que conserva voces instaladas;
- instalación offline atómica con rutas permitidas, límites, tamaño exacto, SHA-256, directorio temporal, rename y transacción Room;
- documentación en `docs/VOICE_SYSTEM.md` y `docs/USER_PERSONA.md`.

PROBADO:

- resolver de plantillas, formato Acción, instrucción Next, política de contenido, parser/sanitizer TTS y prompt de continuación;
- parser de repositorio, rechazo de traversal/ejecutables/voz real sin consentimiento, instalación/rollback y asignación de voz por ID;
- persistencia lógica de UserPersona y extensión opcional de voz recomendada en importación/exportación Character Card.

PARCIAL:

- `sampleUrl` se valida y conserva, pero la escucha remota antes de descargar aún no tiene reproductor; una voz instalada sí puede probarse;
- no se distribuye una voz pesada ni un repositorio público por defecto: el usuario añade una URL compatible;
- el tono se aplica con Android TTS; sherpa puede ignorarlo según el modelo;
- el selector de múltiples UserPersona queda preparado en Room, pero 0.6.0 expone sólo el perfil predeterminado.

## Memoria autónoma

Implementado:

- Room v2 y migración no destructiva 1→2;
- tipos de memoria estable, episódica, emocional, relacional, objetivos, promesas y experiencias compartidas;
- extracción JSON con el mismo LLM local después del turno y fallback seguro;
- filtro por nivel, normalización, deduplicación, sustitución e historial de conflictos;
- fechas absolutas, caducidad y eventos pendientes con seguimiento/cooldown;
- retrieval por keywords, nombres, recencia, importancia, accesos y tipo mediante `MemorySearchEngine`;
- separación por personaje, persona y conversación, con memoria global opcional desactivada por defecto;
- estado persistente de relación con evolución gradual;
- resúmenes incrementales sin borrar mensajes;
- prompt con memorias relevantes e instrucciones de uso natural;
- cola única `CHAT_GENERATION > MEMORY > SUMMARY` con preempción del trabajo interno;
- memoria manual fijada, intención natural de olvidar, pantalla CRUD y texto de privacidad;
- ajustes de memoria inteligente, seguimientos, resumen, nivel y alcance compartido;
- métricas únicamente en log debug.

## Catálogos públicos y avatares

Implementado desde 0.3.0 y ampliado en 0.4.0:

- proveedor real AI Character Cards con búsqueda, tendencias, paginación, detalle, introducción, PNG original y avatar;
- proveedor local directo y gestor neutral con fallos aislados;
- Chub, Pygmalion y Character Tavern visibles como no compatibles con motivo verificable, sin scraping;
- pantalla Ajustes → Repositorios con alta, edición, prueba, activación, sincronización y borrado de fuentes `repository.json` HTTPS;
- búsqueda con debounce, selector de fuente/Todos, categorías derivadas de tags reales, SFW/NSFW, orden, resultados progresivos y carga incremental;
- feature flags persistentes por proveedor en Ajustes;
- Coil para avatar remoto/local con fallback, usado en Inicio, detalle, listas y chat;
- instalación privada con directorio temporal, transacción Room, rename final, compensación, hashes y deduplicación;
- Room v3 y migración no destructiva 2→3 con `character_sources`;
- permiso de Internet usado por catálogos y, solo si el usuario lo selecciona, por proveedores de IA BYOK; chats y memoria continúan almacenados localmente;
- documentación técnica en `docs/CHARACTER_PROVIDERS.md`.

## Validación

- Hotfix 0.6.2: el botón `**` del chat ahora escribe marcas visibles en el campo. Con el campo vacío inserta `**`; con texto existente envuelve como `**texto**`; al desactivar acción retira el envoltorio completo.
- Hotfix 0.6.3 responsive: `CharacterChatTopBar`, burbujas y composer del chat ya no dependen de una captura/resolución concreta. La top bar calcula avatar, subtítulo, acciones visibles y menú desde el ancho disponible; el chat centra y limita ancho en pantallas grandes; el composer usa constraints e `imePadding()` para mantenerse visible con teclado.
- Fase 0.6.4 UX/rendimiento/topbars: se añadió `AppMotion`, `MainScreenTopBar`, `DetailTopBar` y `HomeTopBar`; se retiró el inset duplicado de `CenterAlignedTopAppBar` en las pantallas principales y secundarias. Medido por ADB en Xiaomi: Inicio 168 px; Explorar/Chats/Modelos/Ajustes 147 px, con contenido visible inmediatamente debajo.
- Chat 0.6.4: la generación/prompt se ejecuta en `Dispatchers.Default`, la TopBar ya no observa la lista completa de mensajes para regenerar, el autoscroll se sincroniza con frame y los tokens siguen agrupados antes de renderizar.
- Microinteracciones 0.6.4: cards con press-scale sutil, bottom navigation con escala controlada y action mode `**` con transición de color centralizada.
- `testDebugUnitTest testReleaseUnitTest`: `BUILD SUCCESSFUL`; 91 tests debug + 91 release, 182 ejecuciones, 0 fallos.
- Cubierto además: GGUF futuro/desconocido, metadata/template, repositorio válido/inválido, persistencia/enable, avatar, autoscroll y escenarios de 500 mensajes/500 personajes.
- `Migration1To2Test`, `Migration2To3Test`, `Migration3To4Test`, `Migration4To5Test` y `Migration5To6Test` cubren preservación/esquema; `compileDebugAndroidTestKotlin` finaliza correctamente.
- `lintDebug`: `BUILD SUCCESSFUL`, 0 errores.
- `assembleDebug` y `assembleRelease`: `BUILD SUCCESSFUL`; CMake/JNI validado para `arm64-v8a` y `x86_64`.
- APK debug 0.6.4 (versionCode 16): `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `EE91454069E0144DA9BB50134649A2D9221DBAF76687C1996DBDC6AA376C3CA1`; instalado por ADB en el Xiaomi conectado.
- APK release unsigned 0.6.4: `app/build/outputs/apk/release/app-release-unsigned.apk`, SHA-256 `A803DF6C2AF97A33132F42AF12235AEF1C4BDCFD7A082F25C4489E92E0BB0E12`.
- El APK solicita `android.permission.INTERNET` únicamente para explorar y descargar fichas públicas; no se añadió telemetría ni sincronización de conversaciones.
- El APK de pruebas instrumentadas se genera correctamente. MIUI canceló la instalación del APK auxiliar de test, pero el flujo real de chat de 0.6.1 se verificó por ADB en el Xiaomi conectado sin borrar los datos de la aplicación.

## Límites conocidos

- La calidad de extracción y resumen depende de que el GGUF siga correctamente instrucciones JSON; el parser falla de forma segura.
- Retrieval v1 no usa embeddings ni resolución semántica profunda; la interfaz ya permite añadir otro motor.
- Las contradicciones se resuelven con slots y señales lingüísticas conservadoras; casos ambiguos coexisten para evitar borrar hechos válidos.
- Las fechas relativas dependen primero del LLM local; no hay un parser lingüístico completo para todos los idiomas.
- CPU solamente; Vulkan no está habilitado.
- No se distribuye ningún GGUF; la prueba end-to-end se realizó en el dispositivo físico conectado con Qwen3 0.6B Q8_0.
- Algunos GGUF antiguos no incluyen una plantilla de chat utilizable; en esos casos `AUTO` conserva RAW y el usuario puede elegir una plantilla manual.
- «Cualquier modelo» significa un GGUF monolítico de generación de texto cuya arquitectura admita la versión integrada de llama.cpp; embeddings, difusión, proyectores multimodales y modelos partidos requieren motores o archivos adicionales.
- La carga, prefill, streaming, persistencia y navegación se verificaron mediante ADB en el Xiaomi físico conectado, conservando sus datos.
- Las voces sherpa requieren un repositorio compatible que publique directamente todos los archivos ONNX/tokens/datos y hashes; los archivos comprimidos de terceros no se ejecutan ni extraen automáticamente.
- No se validó síntesis sherpa de una voz real en el Xiaomi durante 0.6.0 porque no había dispositivo ADB ni voz compatible instalada en esta sesión.

## Próxima mejora recomendada

Añadir embeddings locales pequeños y un evaluador de contradicciones por lotes, manteniendo el motor por keywords como fallback; después validar con conversaciones largas y distintos GGUF en dispositivo físico.
## 0.7.0 — Chats grupales

- Room 6→7 con entidades aisladas para grupos, participantes, mensajes y memoria compartida.
- Botón `+` en Chats, creación con personajes locales, listado y detalles del grupo.
- `SMART`/`ROUND_ROBIN`/`MANUAL` y selector contextual con menciones, cooldown y límite de cadena.
- Chat grupal con avatares/nombres por mensaje, `**`, `Next`, sugerencias `@` y TTS secuencial.
- Tests unitarios para mención, solicitudes colectivas, bot-a-bot y round-robin.
