# Integracion de llama.cpp

## Release

La fuente se fija en `b10218` / `de699957b` en `native/llama.cpp.version`.

## Build Windows

```powershell
.\scripts\build-llama-windows.ps1 -Backend cpu
.\scripts\build-llama-windows.ps1 -Backend cuda
.\scripts\build-llama-windows.ps1 -Backend vulkan
.\scripts\check-native.ps1
```

El build prepara tanto `llama-cli.exe` como `llama-server.exe` y copia sus DLL al mismo directorio. El chat usa el segundo; `llama-cli.exe` queda para herramientas auxiliares como benchmark.

## Contrato del motor

`LocalLlmEngine` define carga, descarga, generacion, stop y estado. `EngineRuntime` inicia un unico servidor oculto por modelo y mantiene `server_port` y `runtime_state` (`STARTING`, `LOADING_MODEL`, `READY`, `GENERATING`, `STOPPING`, `ERROR`).

Cada turno envia historial estructurado a `POST /v1/chat/completions` y lee SSE. El parser deserializa JSON y unicamente acepta `choices[0].delta.content`; nunca copia stdout/stderr al transcript. Los logs se drenan a la seccion Diagnostico. `stop_generation` cancela el stream sin descargar el GGUF.

Los modelos con razonamiento reciben `chat_template_kwargs.enable_thinking=false`, de modo que el texto visible llega por `delta.content` y no se presenta `reasoning_content` en la burbuja.

## Validacion real

Para una prueba completa hay que validar `llama-server --version`, `GET /health` y una peticion SSE con un GGUF real. Una GPU detectada o un backend compilado no demuestra por si solo que un modelo pueda cargarse: hay que comprobar la respuesta y las metricas de la ejecucion.
