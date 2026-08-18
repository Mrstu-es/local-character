# Roadmap Desktop

## Milestone 1 — laboratorio local (actual)

- abrir Tauri y mostrar UI inmediatamente;
- detectar Windows, CPU, núcleos, RAM, GPU NVIDIA y Vulkan cuando las herramientas estén instaladas;
- registrar cualquier GGUF externo y leer cabecera/metadata segura;
- seleccionar backend y configuración inicial sin ocultar las decisiones;
- cargar/verificar un `llama-cli.exe` real;
- chat de prueba con streaming, detener, descargar y volver a cargar;
- medir TTFT y tokens/s;
- guardar benchmarks en SQLite;
- generar instalador Windows.

## Milestone 2 — personajes compatibles

- Character Card V1/V2 JSON y PNG;
- avatar, personalidad, escenario, saludo, ejemplos, system prompt y lore;
- UserPersona, `{{user}}` y `{{char}}`;
- historial persistente y PromptBuilder compartido conceptualmente con Android;
- Next, Action Mode `**`, pulsación larga y acciones de mensaje;
- memoria fijada, ramas y rebobinado.

## Milestone 3 — continuidad avanzada

- extracción automática y retrieval de memoria;
- resúmenes y relaciones;
- grupos, contexto de grupo y speaker selector;
- TTS y repositorios de voces;
- importación/exportación de biblioteca Android.

## Milestone 4 — laboratorio ampliado

- comparación de modelos y presets reproducibles;
- ajuste avanzado de GPU layers, contexto, batch, ubatch, Flash Attention, mmap, mlock y KV cache;
- CUDA/Vulkan tuning y monitorización de VRAM cuando exista API fiable;
- servidor LAN opcional y conexión Android;
- proveedores online explícitos.

Nunca se debe avanzar a funciones sofisticadas si el primer milestone no puede cargar un GGUF real, generar tokens, detenerse y liberar memoria.
