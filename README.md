# Local Character

Local Character es una aplicación Android gratuita para conversar con personajes de inteligencia artificial. La idea es que cualquier persona pueda crear y tener su propio personaje, conservar sus conversaciones en privado y elegir entre modelos locales GGUF o proveedores online configurados por el usuario.

## Apoya el proyecto

Si Local Character te resulta útil, puedes apoyar su desarrollo con una propina en Ko-fi: [Apoyar en Ko-fi](https://ko-fi.com/thepassionatestudent).

## Próximamente

Estamos preparando una aplicación para PC que permitirá usar modelos de IA locales más potentes, junto con nuevas funciones para personalizar personajes, conversaciones y memoria. El proyecto sigue creciendo y toda ayuda, comentario o contribución es bienvenida.

Aplicación Android nativa para conversar con personajes usando modelos GGUF en el dispositivo mediante `llama.cpp` o proveedores online opcionales con las credenciales del usuario. El modo local no requiere cuenta, backend ni conexión.

## Estado

El repositorio contiene un MVP Android con:

- interfaz Jetpack Compose y Material 3, onboarding y temas claro/oscuro/sistema;
- personajes locales, creación, importación/exportación Character Card V2 JSON e importación PNG con metadata `chara`;
- foto de perfil propia desde el selector visual de Android al crear o editar un personaje, guardada de forma privada;
- perfil real del usuario con avatar, nombre, descripción y variables seguras `{{user}}`/`{{char}}`;
- composer con modo Acción `**` y Next, cuya instrucción temporal nunca crea un mensaje falso del usuario;
- contenido estándar por defecto, confirmación adulta, filtrado de catálogos y override por personaje;
- TTS local sherpa-onnx (Kokoro/Piper/VITS), fallback Android, voces por personaje, auto reproducción y botón de audio;
- repositorios de voces HTTPS con licencia/consentimiento, descarga temporal, tamaño/hash y uso offline;
- múltiples conversaciones persistentes, streaming cancelable en memoria y una sola escritura final por respuesta;
- Room para personajes, conversaciones, mensajes, memorias, lorebooks, modelos y personas de usuario;
- selección GGUF por Storage Access Framework, con carga directa y copia privada automática solo cuando el proveedor no admite mmap/reapertura;
- lectura segura de metadata GGUF dinámica, parámetros/tokenizer/chat template y recomendación orientativa según RAM disponible;
- catálogos integrados y pantalla **Ajustes → Repositorios** para fuentes `repository.json` HTTPS configurables;
- idioma de contenido persistente: filtra automáticamente catálogos/repositorios y guía el idioma de respuesta del modelo;
- `PromptBuilder` con presupuesto de tokens, historia reciente, lore relevante y memoria;
- memoria autónoma local con extracción por el GGUF, deduplicación, conflictos, retrieval, eventos pendientes, relación y resumen incremental;
- pantalla CRUD de memoria y ajustes de nivel, seguimientos, resúmenes y alcance entre chats;
- Android NDK/JNI y `llama.cpp` CPU integrado directamente, sin servidor externo;
- arquitectura `LlmProvider` intercambiable con Local GGUF, Groq, OpenRouter, Gemini, OpenAI Responses, Anthropic, Mistral y OpenAI-compatible personalizado;
- API Keys BYOK cifradas mediante Android Keystore, selector global/por personaje/por conversación, streaming común, fallback conservador y tracking local de usage/costo;
- tests unitarios para prompt, presupuesto, lore, memoria y Character Card, más una prueba Room instrumentada.

Consulta [docs/PROGRESS.md](docs/PROGRESS.md) para conocer las limitaciones verificadas de esta compilación.

## Requisitos

- Android Studio con JDK 17
- Android SDK Platform 34 y Build Tools 34.0.0
- Android NDK `26.1.10909125`
- CMake `3.22.1`
- dispositivo Android 8.0 (API 26) o posterior, preferiblemente `arm64-v8a`
- un modelo `.gguf` compatible con la versión fijada de `llama.cpp`

## llama.cpp reproducible

La versión fijada está en `third_party/llama.version`:

- tag: `b10434`
- commit: `7e4c0a96880dae4fc4268ad441f8a6446bd5460a`
- backend inicial: CPU

La carpeta `third_party/llama.cpp` se omite del control de versiones para no duplicar el repositorio upstream. Para obtener exactamente la versión esperada desde PowerShell:

```powershell
.\scripts\fetch-llama.ps1
```

El proyecto CMake falla de forma explícita si falta esta dependencia.

## Compilar

En Windows PowerShell:

```powershell
$env:JAVA_HOME = 'ruta\a\jdk-17'
.\gradlew.bat clean testDebugUnitTest assembleDebug
```

El APK resultante queda en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

ABIs configuradas: `arm64-v8a` y `x86_64`. Vulkan no es obligatorio y no está habilitado en este milestone.

## Usar un modelo GGUF

1. Abre **Modelos** o completa el onboarding.
2. Pulsa **Añadir modelo GGUF** y selecciona un `.gguf` con el selector de Android.
3. La app valida la cabecera, guarda permiso persistente sobre el URI y muestra metadata disponible.
4. Pulsa **Cargar**. La app usa mmap desde `/proc/self/fd/<fd>` junto con el repacking del backend CPU optimizado y conserva una ruta de I/O directo como fallback. Si el proveedor de documentos no permite esa carga, prepara automáticamente una copia privada estable y la elimina al borrar el modelo.
5. El contexto usado es el valor elegido en Ajustes, limitado por el máximo declarado por el modelo; un GGUF de 32K/128K no reserva automáticamente todo ese contexto en el teléfono.
6. En móvil la app limita la inferencia a 4 hilos y batch 128 para conservar capacidad para Compose y Android.
6. En **Información del modelo** puedes dejar `AUTO` o elegir ChatML, Llama 3, Qwen, Gemma, Raw o una plantilla personalizada.

La carga termina con una inferencia breve: la app solo muestra el modelo como activo si realmente consigue generar texto. Durante la generación, `AUTO` procesa la plantilla Jinja completa incluida en el GGUF, incluidas las variantes modernas de Qwen y Llama. Se admiten GGUF monolíticos de generación de texto en arquitecturas y cuantizaciones soportadas por la versión integrada de llama.cpp; embeddings, difusión, proyectores multimodales, modelos partidos y formatos como SafeTensors u ONNX requieren soporte o archivos adicionales. Después, abre un personaje y pulsa **Chatear**.

Los límites reales dependen de RAM, arquitectura y cuantización. Para móviles, empieza con un modelo instruct pequeño (aprox. 1B–3B) cuantizado a Q4. La aplicación avisa cuando el tamaño del archivo se acerca demasiado a la RAM disponible, pero no bloquea arbitrariamente la carga.

## Character Cards

- Importación: Character Card V1/V2 en JSON y PNG con chunks `tEXt`, `zTXt` o `iTXt` cuyo campo sea `chara`.
- Exportación: JSON Character Card V2.
- Se validan límites de archivo, metadata, strings, listas y JSON. Las tarjetas se tratan exclusivamente como datos; nunca se ejecuta contenido importado.

## Arquitectura resumida

```text
Compose UI → ViewModels → repositorios → Room / DataStore
                     ↓
              PromptBuilder → LlmRequest
                     ↓
              LlmProvider (Flow)
          ┌──────────┴──────────┐
   LocalLlamaProvider       Proveedores online
          ↓                      ↓
   JNI / llama.cpp / GGUF   HTTPS / SSE
```

Detalles: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Privacidad

La app no incluye analítica ni telemetría. En modo Local GGUF, las conversaciones no salen del dispositivo. Si el usuario elige una API online, la interfaz lo indica y envía al proveedor seleccionado el mensaje y el contexto necesario. Memorias, historial completo, personajes y métricas siguen almacenados localmente. Las API Keys se cifran con Android Keystore y se excluyen del backup.

## Limitaciones actuales

- CPU solamente; Vulkan queda para una fase posterior.
- La edición visual completa de lorebooks y personas de usuario todavía no está expuesta en todas las pantallas; las memorias sí tienen pantalla CRUD desde el chat.
- Exportación PNG Character Card está preparada conceptualmente, pero el MVP exporta JSON.
- Los embeddings y las respuestas alternativas son mejoras futuras; el retrieval actual usa scoring local por keywords.

## Documentación

- [Arquitectura](docs/ARCHITECTURE.md)
- [Proveedores de IA](docs/AI_PROVIDERS.md)
- [Sistema de voces](docs/VOICE_SYSTEM.md)
- [Persona del usuario](docs/USER_PERSONA.md)
- [Progreso y problemas conocidos](docs/PROGRESS.md)
