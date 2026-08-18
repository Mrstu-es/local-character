# Decisiones de Local Character Desktop

## Tauri 2

La aplicacion usa Tauri 2 + React + TypeScript + Vite + Rust. La interfaz corre dentro de WebView2 y el backend Rust puede iniciar procesos nativos sin abrir una consola.

## llama.cpp fijado

La fuente se fija en `b10218` / `de699957b` mediante `native/llama.cpp.version` y los scripts de `desktop/scripts`.

## Transporte local

El chat local usa `llama-server.exe` como sidecar oculto, no `llama-cli.exe` por mensaje. Al cargar un GGUF se reserva un puerto efimero en `127.0.0.1`, se inicia el servidor y se espera `GET /health`. El servidor permanece cargado entre turnos y solo se detiene al descargar el modelo o cerrar la aplicacion.

El endpoint `POST /v1/chat/completions` se consume como SSE. Solo el campo estructurado `choices[0].delta.content` se emite como `llm://delta`; stdout, stderr, banners, razonamiento y metadatos quedan en Diagnostico. Los eventos `llm://complete`, `llm://usage`, `llm://runtime-state` y `llm://error` estan separados del contenido del chat.

## Modelos y privacidad

Los GGUF conservan la ruta absoluta elegida por el usuario y se marcan si desaparecen. SQLite, personajes y conversaciones permanecen locales. Las APIs externas se muestran separadas como procesamiento online.
