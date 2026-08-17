# Persona del usuario

## Perfil principal

**Ajustes → Mi perfil** permite guardar nombre, avatar opcional y una descripción breve. El avatar se elige con Android Photo Picker, se valida como imagen y se copia a almacenamiento privado. Room admite varias filas `UserPersonaEntity`; sólo una es la predeterminada en esta fase.

Cada conversación conserva `userPersonaId`. Una conversación antigua que todavía tenga `NULL` recibe la persona predeterminada al abrirse, sin modificar mensajes previos. Las memorias, relaciones y eventos ya se consultan mediante `characterId + userPersonaId + conversationId`; por tanto dos perfiles futuros no mezclarán contexto.

## Variables de plantilla

`TemplateVariableResolver` reconoce únicamente variables completas y sin distinguir mayúsculas:

- `{{user}}` → nombre actual de `UserPersona`;
- `{{char}}` → nombre actual del personaje.

Admite espacios internos como `{{ user }}`. No reemplaza `{{username}}`, variables desconocidas ni texto parcial. La resolución ocurre al construir cada prompt, así que cambiar Tadeo por Theo afecta turnos futuros sin reescribir el historial.

`PromptBuilder` incluye siempre una sección pequeña de alta prioridad:

```text
USER PERSONA
Name: Tadeo
Description: ...
```

La descripción se limita a 600 caracteres. El prompt ordena no repetir el nombre en cada frase, sino usarlo naturalmente. Character Cards, system prompt, personalidad, escenario, ejemplos, lore y eventos pasan por el mismo resolver seguro.

## Acción y Next

El composer usa `ComposerMode.NORMAL` y `ComposerMode.ACTION`. Acción envuelve el borrador en `**...**` una sola vez. La representación guardada es la acción real, por lo que memoria puede interpretarla como conducta del usuario sin convertirla automáticamente en un hecho permanente.

`GenerationMode.CHARACTER_CONTINUE` agrega una instrucción temporal al sistema. No crea, inserta ni simula un mensaje USER; tampoco borra el borrador. La respuesta del personaje sí se guarda y pasa por relación, resumen y extracción de memoria. La instrucción prohíbe decidir o hablar por la persona del usuario y pide no repetir la respuesta anterior.
