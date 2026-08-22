# Local Character 0.3.0 — continuidad y memoria persistente

Esta entrega publica **Android 0.9.0** y **Desktop 0.3.0** desde el mismo repositorio. El objetivo principal es que los personajes mantengan una escena coherente, recuerden lo ocurrido y conserven opiniones, preferencias, relaciones y promesas propias sin mostrar razonamiento interno del modelo.

## Contexto de conversación reforzado

- La ficha completa del personaje y la persona del usuario siguen siendo la identidad base del chat.
- Los turnos recientes se conservan literalmente y en orden cronológico dentro del presupuesto real del modelo.
- Los chats largos usan un resumen acumulativo solo para la parte antigua; nunca sustituye los últimos turnos.
- La escena actual, las preguntas sin resolver, los objetos, lugares, correcciones y compromisos se vuelven anclas explícitas del siguiente turno.
- El mismo contexto se utiliza con GGUF local y con proveedores externos.
- Las respuestas que intentan hablar como sistema, describir su razonamiento o ignorar una referencia inmediata se filtran antes de volver a entrar en el historial.

## Opiniones y memoria propias del personaje

- Detección conservadora de hechos, preferencias, opiniones, relaciones y compromisos expresados de forma explícita.
- Las opiniones del personaje se guardan con propietario claro: no se confunden con gustos del usuario ni con instrucciones del sistema.
- Un cambio explícito posterior actualiza la memoria anterior en lugar de duplicarla.
- La recuperación prioriza el chat actual, la relevancia para el mensaje y los recuerdos fijados.
- Las memorias permanecen asociadas al personaje y a la persona de usuario correctos; no se mezclan entre personajes.
- Android permite desactivar el uso entre chats desde Ajustes. Desktop conserva la procedencia del chat y prioriza la conversación activa.
- Las memorias pueden revisarse y eliminarse desde la interfaz.

## Compatibilidad con el modelo Local Character RP

Se añadió una prueba reproducible con `Llama-LocalCharacter-RP-3B-v0.1-Q4_K_M.gguf`. El escenario comprueba que el modelo interprete una pregunta elíptica usando los turnos anteriores, conserve el objeto de la escena y no exponga metacontenido. La prueba está en `desktop/scripts/model-continuity-smoke.ps1`.

El motor local mantiene el chat template del GGUF, desactiva el razonamiento visible cuando la arquitectura lo permite y aplica limpieza adicional para modelos que ignoren esa opción. Desktop usa un muestreo conservador (`temperature 0.35`, `top_p 0.90`, `top_k 40`, `min_p 0.05`) para priorizar continuidad en modelos pequeños. Android migra únicamente los antiguos valores predeterminados a un perfil estable y respeta cualquier ajuste personalizado.

## Validación de esta entrega

- Android: 110 pruebas unitarias en 38 suites, compilación APK 0.9.0 correcta.
- Desktop: pruebas de continuidad y memoria semántica, 17 pruebas Rust y compilación Tauri 0.3.0 correctas.
- Runtime Desktop: `llama.cpp b10434` (`7e4c0a968`).
- Prueba real del GGUF Q4: conserva la búsqueda del pijama, resuelve la referencia a la ropa sucia y no expone razonamiento interno.

## Descargas y Releases automáticas

Cada etiqueta `v*` inicia un flujo reproducible que:

1. prueba, firma y verifica el APK release de Android con una clave estable protegida en GitHub Secrets;
2. prueba y compila los instaladores EXE y MSI de Windows;
3. publica los tres archivos juntos en GitHub Releases;
4. añade un archivo `SHA256SUMS.txt` para verificar su integridad.

Los nombres son estables, por lo que los enlaces de descarga de la página principal siempre apuntan a la versión más reciente:

- `Local-Character-Android.apk`
- `Local-Character-Windows-Setup.exe`
- `Local-Character-Windows.msi`

> **Cambio único de firma en Android:** las compilaciones alfa anteriores usaban una firma de depuración. Android no permite actualizarlas directamente con la nueva firma estable. Quien tenga una versión anterior deberá exportar lo que quiera conservar, desinstalarla una sola vez e instalar el APK 0.9.0. Desde esta versión, las actualizaciones posteriores conservarán la misma clave de publicación.

## Privacidad

La memoria, los personajes, los resúmenes y el historial se guardan localmente. Cuando se usa un GGUF, el contenido no sale del dispositivo. Si se elige una API externa, solo se envía al proveedor el contexto necesario para responder y la interfaz indica qué motor está activo.

## Nota sobre modelos locales

Ninguna aplicación puede hacer que un modelo pequeño sea infalible. Esta versión reduce los fallos mediante contexto estructurado, memoria, resumen, filtros y pruebas reales; cuando el contexto del GGUF se llena, la aplicación conserva los turnos recientes y comprime únicamente la parte antigua para evitar perder la escena inmediata.
