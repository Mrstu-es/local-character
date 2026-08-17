# Proveedores de IA

Verificado el 2026-08-15 contra la documentación oficial disponible en esa fecha. Endpoints, modelos, cuotas, precios y free tiers pueden cambiar. La aplicación no mantiene una lista permanente de modelos supuestamente gratuitos.

## Arquitectura

El personaje y el proveedor están separados:

```text
Character Card + personalidad + lore + memoria + relación + historial
                              ↓
                        PromptBuilder
                              ↓
                  LlmRequest común y neutral
                              ↓
                AiProviderManager / LlmProvider
       ┌──────────┬──────────┬──────────┬───────────┐
  Local GGUF    Groq    OpenRouter    APIs nativas   Custom
       └──────────┴──────────┴──────────┴───────────┘
                              ↓
             LlmStreamEvent → ChatViewModel → UI
```

Cambiar el proveedor no crea otra conversación ni modifica Character Card, lore, memorias, relación o resumen. `PromptBuilder` produce un solo `systemPrompt` y una lista neutral de mensajes. Cada adapter convierte esa representación al contrato de su API.

La selección se resuelve en este orden:

1. modelo de la conversación;
2. modelo del personaje;
3. modelo global.

`Solo modelos locales` tiene prioridad sobre los tres niveles y nunca permite un fallback online.

## Proveedores incluidos

| Proveedor | Implementación | Catálogo | Generación streaming | Clasificación mostrada |
|---|---|---|---|---|
| Local GGUF | llama.cpp/JNI existente mediante `LocalLlamaProvider` | modelo activo en Room | `Flow` local | Local |
| Groq | API oficial OpenAI-compatible | `GET /openai/v1/models` | `POST /openai/v1/chat/completions` | Free tier/pago según cuenta |
| OpenRouter | API oficial | `GET /api/v1/models` | `POST /api/v1/chat/completions` | metadata dinámica por modelo |
| Google Gemini | Gemini API REST | `GET /v1beta/models` | `POST /v1beta/models/{model}:streamGenerateContent?alt=sse` | Free tier/pago según modelo, proyecto y región |
| OpenAI | Responses API | `GET /v1/models` | `POST /v1/responses` | Pago; precio exacto no inferido |
| Anthropic | Messages API | `GET /v1/models` | `POST /v1/messages` | Pago; precio exacto no inferido |
| Mistral | API oficial | `GET /v1/models` | `POST /v1/chat/completions` | Desconocido si la API no informa precio |
| OpenAI-compatible personalizado | Chat Completions compatible | `GET {baseUrl}/models` | `POST {baseUrl}/chat/completions` | Configurable/desconocido |

Documentación oficial consultada:

- Groq: <https://console.groq.com/docs/api-reference> y <https://console.groq.com/docs/rate-limits>
- OpenRouter: <https://openrouter.ai/docs/api/reference/overview> y <https://openrouter.ai/docs/api-reference/models/get-models>
- Gemini: <https://ai.google.dev/api> y <https://ai.google.dev/gemini-api/docs/pricing>
- OpenAI: <https://developers.openai.com/api/reference/resources/responses> y <https://developers.openai.com/api/reference/resources/models>
- Anthropic: <https://platform.claude.com/docs/en/api/overview> y <https://platform.claude.com/docs/en/api/messages>
- Mistral: <https://docs.mistral.ai/api>

No se codifican IDs de modelos actuales. La pantalla usa el catálogo que devuelve la cuenta del usuario y lo conserva 24 horas; `Actualizar modelos` fuerza una consulta nueva.

## Configuración y API Keys

Ruta: **Ajustes → Proveedores de IA**.

La aplicación funciona con BYOK: no incluye una clave maestra. Cada clave:

- se cifra con AES/GCM;
- usa una clave AES no exportable creada en Android Keystore;
- guarda únicamente IV + ciphertext en preferencias privadas;
- se excluye de backup y transferencia;
- se muestra solo enmascarada después de guardarla;
- nunca se añade a Room, DataStore, `BuildConfig`, logs o Git.

Los botones `Probar` usan el listado de modelos, que evita generar texto innecesario. Para crear credenciales, usa el panel oficial del proveedor: Groq Console, OpenRouter Keys, Google AI Studio, OpenAI Platform, Anthropic Console o Mistral La Plateforme.

## Modelos, filtros y precios

La pantalla **Modelos** conserva los GGUF locales y añade la sección Online con búsqueda, favoritos y filtros Todos/Gratis/Free tier/Pago.

OpenRouter es el caso con metadata de precio implementada: se leen `pricing.prompt` y `pricing.completion`, se convierten de precio por token a USD por un millón de tokens y se prioriza la metadata numérica. El sufijo oficial `:free` solo se usa como fallback cuando falta la metadata. Cero real en entrada y salida se clasifica `FREE`; valores informados distintos de cero se clasifican `PAID`.

Para proveedores cuyo catálogo no entrega precios, la app muestra `Precio no disponible`; no adivina precios desde el nombre del modelo. El texto `Información de precio sujeta a cambios` permanece visible.

## Streaming, errores y cancelación

Todos los providers entregan `TextDelta`, `Usage`, `Error` y `Completed`. El parser SSE soporta comentarios, campos `event`, IDs, retry, múltiples líneas `data`, final sin línea vacía, `[DONE]` y fragmentación arbitraria de red. Gemini, OpenAI Responses y Anthropic tienen adapters de eventos propios.

`Detener` cancela coroutine, request OkHttp/SSE o inferencia llama.cpp. No se escribe cada delta en Room: la respuesta parcial permanece en memoria y se guarda una sola vez al finalizar.

Se mapean 401, 402/errores de facturación, 403, 404, 408, 429, 5xx, timeout y red. `Retry-After` numérico se conserva. OkHttp no reintenta automáticamente una generación, para evitar duplicados o doble cobro.

## Fallback

Está desactivado por defecto. Si el usuario lo activa, puede ordenar una cadena de modelos, incluido Local GGUF.

Solo se cambia automáticamente antes de haber recibido texto y ante red, timeout, 429, modelo no disponible o 5xx. Nunca se hace fallback silencioso por 401/credencial inválida, acceso denegado, facturación o después de recibir texto. Si está desactivado, el chat muestra el error y el acceso `Cambiar IA`.

Cada intento reutiliza el mismo `PromptBuilder`, pero vuelve a ajustar el presupuesto al contexto del modelo de destino. Así, un fallback desde una API de contexto grande hacia un GGUF móvil no envía al motor local un prompt que exceda su ventana.

## Uso y presupuesto

Room v5 añade `ai_usage`. Por respuesta final se guardan, cuando existen:

- proveedor y modelo;
- tokens de entrada/salida informados por la API;
- costo estimado;
- conversación y personaje;
- tiempo hasta el primer token y duración total.

El costo solo se calcula si existen usage real y ambos precios. Los tokens desconocidos no se estiman. Local se registra con costo API cero.

En **Proveedores de IA → Uso y presupuesto** se puede fijar presupuesto mensual, aviso porcentual y bloqueo de modelos `PAID` al 100 %. Todo está desactivado por defecto. El bloqueo no afecta Local, Gratis o Free tier.

## Privacidad y servidores personalizados

Con Local GGUF, el contenido se procesa en el dispositivo. Con un proveedor online, el chat indica procesamiento online y envía a ese proveedor el mensaje, system prompt y contexto seleccionado: personalidad necesaria, mensajes recientes, lore y memorias relevantes; nunca envía toda la base de memoria.

Un proveedor personalizado acepta HTTPS. HTTP solo se admite para localhost, loopback o una IP LAN privada y la UI muestra `Conexión HTTP sin cifrar`. Android necesita permitir cleartext a nivel de aplicación porque el host LAN se configura en runtime y no puede enumerarse en XML; `ProviderBaseUrlValidator` rechaza HTTP público y todos los esquemas distintos de HTTP/HTTPS.

## Validación y límites

Los adapters se probaron con MockWebServer; las pruebas no hacen llamadas reales ni consumen créditos. También existen pruebas para SSE, errores, precios, costo, selección, fallback, URL LAN, tracking de uso y migración Room 4→5.

No se ejecutaron llamadas reales a Groq, OpenRouter, Gemini, OpenAI, Anthropic o Mistral porque no se proporcionaron API Keys. Vision, tools, structured output e imágenes están modelados como capabilities, pero el chat MVP envía texto solamente. La arquitectura almacena una credencial por proveedor por ahora. Algunos catálogos generales pueden incluir modelos que no acepten texto/chat; si la API los rechaza, se muestra el error sin cerrar la app.
