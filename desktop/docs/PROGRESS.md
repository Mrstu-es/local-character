# Progreso de Local Character Desktop

> Actualización verificable: consulta [STATUS_2026-08-17.md](STATUS_2026-08-17.md). Las secciones históricas inferiores conservan el contexto del primer prototipo.

## ESTADO DE ENTREGA (2026-08-16)

- Toolchain Windows MSVC, Rust, CMake y Git instalado y verificado.
- llama.cpp b10218 compilado para CPU x64; `llama-cli --version` y `--list-devices` responden.
- `cargo fmt -- --check`, `cargo check` y `npm.cmd run build` completados correctamente.
- El ejecutable Tauri abre correctamente; instaladores NSIS y MSI x64 generados en `src-tauri/target/release/bundle/`.
- Los modelos GGUF no se incluyen en el repositorio ni en esta entrega por su tamaÃ±o y licencia; se seleccionan desde la aplicaciÃ³n.
- Layout responsive validado a 1366x768 y 1024x680: la barra lateral queda fija, el contenido usa scroll independiente y el estado del motor se mantiene visible sin bajar hasta el final.
- Biblioteca de personajes funcional en escritorio: alta, edición, eliminación, saludo, personalidad y avatar desde el selector de archivos.
- Ajustes locales funcionales para idioma del repositorio, tema, contexto y capas GPU.

## IMPLEMENTADO EN LA BASE

- estructura independiente `desktop/` sin tocar la aplicación Android;
- Tauri 2 + React + TypeScript + Vite configurados;
- ventanas Windows, tamaño mínimo, tema oscuro y bundle NSIS/MSI;
- dashboard, sidebar colapsable, navegación, modelos, chat, benchmark y ajustes;
- detector Rust de CPU/RAM/núcleos, NVIDIA y Vulkan cuando las herramientas están disponibles;
- SQLite con migración inicial para modelos, personajes, conversaciones, mensajes, memorias y benchmarks;
- selector nativo de GGUF y registro de rutas absolutas;
- parser de cabecera GGUF con límites de seguridad y metadata básica;
- contrato `LocalLlmEngine` aislado de la UI;
- adaptador de proceso para un `llama-cli.exe` real, eventos de streaming y stop;
- scripts reproducibles para fijar y compilar llama.cpp.

## PROBADO

- revisión oficial de Tauri/WebView2, Vite/React/TypeScript y build/backends de llama.cpp;
- lectura del proyecto Android y de los contratos Character Card, PromptBuilder, memoria y grupos;
- `npm.cmd run build` completado: TypeScript y Vite producen `desktop/dist`;
- navegación manual del frontend en `127.0.0.1:1420` sin respuestas de IA simuladas.
- ninguna generación mock se presenta como resultado final.

## PARCIAL

- el backend Rust necesita compilarse en Windows con Rust/CMake/Visual Studio;
- el adaptador inicial usa `llama-cli.exe` por proceso; FFI directo o servidor persistente se decidirá con benchmarks reales;
- metadata avanzada de tensors, cuantización exacta y parámetros se ampliará con la API de llama.cpp;
- benchmark, personajes y memoria tienen UI/SQLite base, pero todavía no están expuestos de extremo a extremo.

## PENDIENTE

- completar el toolchain Windows: el Rust MSVC ya está instalado, pero `cargo check` requiere `link.exe` de Visual Studio Build Tools/C++;
- obtener/compilar `llama.cpp` b10218 para CPU y probar un GGUF real;
- probar CUDA y Vulkan en hardware disponible;
- completar generación persistente, estadísticas reales y descarga segura del modelo;
- implementar Character Cards, PromptBuilder compatible, historial, Next, Action Mode y memoria fijada;
- generar y probar el instalador final `LocalCharacterSetup.exe` o equivalente.
