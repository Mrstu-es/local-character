# Local Character Desktop

Aplicación de escritorio local para Windows 10/11 x64, separada de la aplicación Android. Su objetivo es probar modelos GGUF grandes con CPU, CUDA o Vulkan, conversar con streaming real y medir rendimiento sin enviar conversaciones a la nube.

## Estado actual

Esta primera base ya incluye:

- shell Tauri 2 con React, TypeScript y Vite;
- dashboard oscuro responsive para escritorio;
- detector de CPU, núcleos, RAM, NVIDIA/nvidia-smi y Vulkan/vulkaninfo;
- registro SQLite de rutas de modelos, personajes, conversaciones, mensajes, memorias y benchmarks;
- selector nativo de archivos `.gguf` sin copiar modelos grandes;
- lectura segura inicial de cabecera y metadata GGUF;
- contrato `LocalLlmEngine` separado de la UI;
- puente de generación que ejecuta un `llama-cli.exe` real y emite deltas por eventos Tauri;
- carga, descarga, cancelación y estado del motor;
- layout responsive para escritorios y ventanas compactas, con barra lateral fija y scroll independiente;
- biblioteca de personajes con nombre, descripción, personalidad, saludo, avatar de galería y persistencia SQLite;
- ajustes locales de idioma del repositorio, contexto máximo y capas GPU;
- pantalla de chat sin respuestas mock: si llama.cpp no está instalado, se muestra un error explícito;
- scripts para fijar y compilar llama.cpp en CPU, CUDA, Vulkan o CUDA+Vulkan;
- documentación de arquitectura, formatos portables, decisiones y roadmap.

La integración nativa real requiere completar los prerrequisitos de Windows y colocar el binario compilado en `desktop/native/bin/llama-cli.exe`. No se guardan modelos GGUF dentro de Git.

## Estructura

```text
desktop/
├─ src/                 # React + TypeScript
├─ src-tauri/           # Rust + Tauri + SQLite
├─ native/
│  ├─ llama.cpp.version # tag/commit fijado
│  └─ bin/              # binarios locales, ignorados por Git
├─ scripts/             # fetch/build/check de llama.cpp
├─ docs/                # decisiones, integración, rendimiento y roadmap
├─ tests/
├─ benchmarks/
└─ models/              # reservado; ignorado por Git
```

## Desarrollo

## Entrega Windows

La compilaciÃ³n validada de esta versiÃ³n produce instaladores x64 en
`src-tauri/target/release/bundle/`: un instalador NSIS `.exe` y un instalador
`.msi`. El paquete incluye el runtime Vulkan de llama.cpp (con fallback CPU) y sus DLL; los modelos
GGUF se mantienen fuera del repositorio y se aÃ±aden desde la pantalla Modelos.

Instala Node.js, Rust, Visual Studio 2022 con Desktop development with C++, CMake y WebView2. Tauri utiliza WebView2 en Windows 10/11.

```powershell
cd desktop
npm.cmd install
npm.cmd run tauri:dev
```

Para preparar el motor local:

```powershell
cd desktop
.\scripts\fetch-llama.ps1
.\scripts\build-llama-windows.ps1 -Backend cpu
# Opcional, según el hardware y SDK instalados:
.\scripts\build-llama-windows.ps1 -Backend cuda
.\scripts\build-llama-windows.ps1 -Backend vulkan
.\scripts\check-native.ps1
```

El build final genera instaladores NSIS/MSI mediante:

```powershell
npm.cmd run tauri:build
```

## Decisiones y límites

La decisión de Tauri frente a Electron, la estrategia del binario nativo y el orden CPU → CUDA/Vulkan están en [docs/DECISIONS.md](docs/DECISIONS.md). El primer hito prioriza abrir el EXE, detectar hardware, cargar un GGUF real, generar, detener, medir y descargar. Character Cards, memoria avanzada, grupos, TTS y proveedores online se portarán después sobre formatos compatibles con Android.

Consulta el estado verificable en [docs/STATUS_2026-08-17.md](docs/STATUS_2026-08-17.md) y la procedencia del icono en [docs/BRANDING.md](docs/BRANDING.md).
