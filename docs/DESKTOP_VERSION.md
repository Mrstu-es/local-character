# Local Character Desktop

Local Character Desktop es la versión para Windows de Local Character: un cliente de roleplay con personajes de IA, diseñado para ejecutar modelos GGUF localmente y mantener las conversaciones en el equipo del usuario.

## Funciones incluidas

- Chat de roleplay con personajes, saludos iniciales y conversaciones persistentes.
- Biblioteca de personajes con creación y edición de nombre, descripción, personalidad, saludo y avatar.
- Importación de Character Cards y exploración de repositorios de personajes.
- Chats individuales y grupos con varios personajes.
- Memoria local, mensajes fijados y acciones de chat (`**`), continuar y regenerar.
- Modelos GGUF locales con lectura de metadata, selector de contexto y registro persistente.
- Proveedores de IA externos configurables con sus modelos disponibles.
- Motor llama.cpp real, streaming de tokens, cancelación y estado de carga visible.
- Aceleración GPU con Vulkan: selecciona automáticamente la GPU discreta cuando está disponible y permite usar GPU, CPU o modo híbrido.
- Ajustes de capas GPU, contexto, idioma, repositorios de personajes y repositorios de voces TTS.
- Asignación de una voz TTS individual a cada personaje cuando hay voces instaladas.
- Diseño responsive para ventanas pequeñas y grandes, con navegación independiente y compositor compacto.
- Datos locales en SQLite; no se suben conversaciones, personajes ni modelos a un servidor propio.

## Motor local y GPU

El instalador incluye el runtime de llama.cpp con backend Vulkan y fallback CPU. En una NVIDIA compatible el motor usa la GPU discreta (por ejemplo `Vulkan0`) y solicita todas las capas posibles (`-ngl -1`). Si la GPU no tiene memoria suficiente, el usuario puede cambiar a CPU o híbrido desde Ajustes. Los modelos GGUF no se incluyen en el repositorio ni en el instalador: se añaden desde **Modelos y APIs**.

## Instalación

1. Descarga el instalador `.exe` (recomendado) o `.msi` desde la sección **Releases**.
2. Instala Local Character Desktop en Windows 10/11 x64.
3. Abre **Modelos y APIs → Añadir GGUF** y selecciona un modelo instruct compatible.
4. Pulsa **Cargar** y después abre un personaje desde **Personajes** o **Explorar**.

## Notas de compatibilidad

- Se necesita Windows 10/11 de 64 bits y WebView2.
- Vulkan depende de los controladores instalados. Si no está disponible, el motor continúa con CPU.
- El rendimiento real depende del tamaño/cuanti­zación del GGUF, la RAM y la VRAM.
- No se distribuyen modelos GGUF por derechos de autor y por su tamaño.

## Capturas

- [Capturas de Android/Xiaomi](screenshots/android/)
- [Capturas de Windows Desktop](screenshots/desktop/)

