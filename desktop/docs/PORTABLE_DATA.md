# Formatos portables Android ↔ Desktop

Desktop reutiliza estándares y formatos, no código Kotlin. El objetivo es que una tarjeta exportada desde Android se pueda importar en Windows y viceversa.

## Character Cards

- entrada: Character Card V1/V2 JSON;
- entrada: PNG con metadata `chara` en chunks tEXt, zTXt o iTXt;
- salida: Character Card V2 JSON;
- conservar personalidad, escenario, saludo, ejemplos, system prompt, tags, creator notes y lorebook;
- no ejecutar jamás el contenido importado.

## UserPersona

Formato previsto:

```json
{
  "name": "Nombre",
  "description": "Descripción breve",
  "avatar": "relative/private/path.png"
}
```

## Group Context

El formato futuro conservará título, descripción, escenario, rol del usuario, reglas, situación inicial, apertura, notas y overrides por participante sin modificar la Character Card original.

## Biblioteca

La exportación completa de Android a ZIP/JSON se implementará después del primer milestone. No hay sincronización cloud en V1.
