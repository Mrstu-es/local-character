# Chats grupales

La versión inicial de chats grupales se integra sobre la aplicación existente sin modificar las tablas de conversaciones directas.

## Datos y migración

Room pasa de la versión 6 a la 7 mediante `MIGRATION_6_7`. Se crean `group_conversations`, `group_participants`, `group_messages` y `group_memories`. Los participantes referencian `CharacterEntity`; quitar un participante no borra su personaje, chats privados ni memoria privada. Borrar un grupo elimina únicamente sus filas por las claves foráneas `CASCADE`.

## Flujo

En **Chats**, el botón `+` abre la elección entre chat individual y grupo. **Nuevo grupo** permite nombre, búsqueda y selección de dos o más personajes instalados localmente. El grupo aparece mezclado con los chats individuales y abre `GroupChatScreen`. Sus detalles permiten cambiar nombre, modo de turnos, límites de cadena, memoria compartida y consultar participantes.

## Turnos

`GroupSpeakerSelector` usa reglas rápidas y deterministas: mención directa, solicitud colectiva, contexto reciente, cooldown del último hablante y balance de participación. `GroupTurnOrchestrator` impone un solo generador a la vez y limita `maxAutoResponses`/`maxBotChain`. Los modos son `SMART`, `ROUND_ROBIN` y `MANUAL`; el modo manual queda preparado para una selección explícita posterior. Las frases de grupo (por ejemplo «¿qué opinan todos?») habilitan una cadena acotada, nunca infinita.

## Prompt y privacidad

Cada turno resuelve proveedor/modelo mediante el `characterId` del personaje seleccionado. El prompt recibe la Character Card completa del hablante, su memoria/lore y un resumen compacto de los demás participantes. El transcript conserva el nombre del emisor y la instrucción explícita de no interpretar a otros personajes ni al usuario. Las memorias privadas no se copian al grupo. Los eventos públicos simples pueden guardarse como `SHARED_EVENT` cuando la memoria compartida está activa.

## UI, acciones y voz

Los mensajes muestran avatar y nombre del personaje. El compositor reutiliza el botón `**`, `Next` y muestra sugerencias `@Mención`. `Next` selecciona el siguiente hablante sin crear un mensaje de usuario falso. Las respuestas se generan secuencialmente; el TTS espera a que la reproducción anterior termine antes de continuar la cadena y `Detener` cancela inferencia y audio.

## Límites conocidos de esta primera entrega

- El selector LLM opcional, reordenamiento por arrastre y avatar compuesto persistente quedan preparados como siguientes iteraciones.
- El modo manual aún requiere exponer un selector de hablante en el compositor.
- Si dos participantes usan GGUF distintos, el gestor local existente puede necesitar recargar el modelo activo; se recomienda compartir el mismo GGUF.

## Contexto compartido y acciones de mensaje (0.8)

Room 8 añade `group_contexts` y `group_participant_contexts`. El contexto no modifica la Character Card: la precedencia del prompt es contexto/reglas del grupo, override del participante y, por último, escenario de la ficha; personalidad e identidad siempre se conservan. La política de lore puede ser `ADAPTIVE` (descarta contradicciones claras como magia en un mundo real), `ORIGINAL` o `DISABLED`. El `firstMessage` individual nunca se inserta automáticamente en un grupo.

Nuevo grupo y detalles permiten editar título, descripción, escenario, rol del usuario, reglas, situación inicial, apertura, notas y overrides por participante. Una pulsación larga sobre cualquier mensaje abre `MessageActionsBottomSheet` con copiar, voz, rama, rebobinar, memoria fijada y reporte local. Las acciones destructivas se confirman y la persistencia queda en ViewModels/repositorios.
