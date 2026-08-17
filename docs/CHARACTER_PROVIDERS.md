# Proveedores de personajes

Última verificación: 2026-08-14 desde Bolivia.

## Estado actual

| Proveedor | Estado | Mecanismo verificado | Decisión |
|---|---|---|---|
| AI Character Cards | Disponible | API HTTPS pública utilizada por su web: listado, búsqueda, paginación, detalle, campos de versión, PNG original y avatar | Integrado de extremo a extremo |
| Local | Disponible | Room y archivos privados de la app | Integrado directamente |
| Chub / CharacterHub | No compatible en esta región | OpenAPI oficial de solo lectura en `gateway.chub.ai`; las peticiones desde Bolivia responden HTTP 403 | Sin scraping ni endpoints antiguos |
| Pygmalion | No compatible | No se verificó un contrato público estable para catálogo + descarga sin cuenta | Visible con motivo; no se simula soporte |
| Character Tavern | No compatible | El sitio ofrece descargas manuales, pero no publica un contrato API estable para terceros | Visible con motivo; no se hace scraping HTML |
| Repositorio JSON | Configurable | Contrato genérico `repository.json` por HTTPS | Añadir, probar, editar, activar, sincronizar o borrar desde Ajustes → Repositorios |

La documentación de Chub indica que los endpoints de exploración/descarga son de solo lectura y no requieren inicio de sesión. Su OpenAPI también advierte que el uso comercial requiere autorización previa. La aplicación no intenta eludir el bloqueo regional ni reutiliza rutas históricas no documentadas.

## AI Character Cards

La integración se comprobó contra los mecanismos públicos que usa el frontend actual:

- `GET https://api.aicharactercards.com/api/cards` con `skip`, `limit`, `search`, `isNsfw`, `language`, `tags` y `orderBy`;
- la preferencia de idioma de Ajustes se envía como `language` y se vuelve a validar al mapear resultados; los repositorios JSON se filtran por su campo `language`;
- `GET /api/cards/trending` con periodo y paginación;
- `GET /api/cards/{id}` para detalle, versión vigente y URL del PNG;
- `GET /api/cards/{id}/versions/{versionId}/fields` para la introducción pública;
- archivos bajo `https://api.aicharactercards.com/uploads/...` para el PNG original y avatar WebP/PNG.

No se usa cuenta, token, cookie ni endpoint de escritura. El proveedor mapea sus DTO a modelos neutrales antes de entregar resultados al resto de la aplicación.

## Contrato interno

`CharacterCatalogProvider` expone:

- descriptor, capacidades y estado de salud;
- búsqueda/paginación con cursor;
- detalle neutral;
- descarga explícita de tarjeta y avatar.

`CharacterCatalogManager` mantiene el registro de proveedores y puede emitir resultados progresivos y aislados. Si una fuente falla, las demás conservan sus resultados. `LocalCharacterCatalogProvider` adapta Room al mismo contrato sin pasar por la red.

El chip **Todos** consulta en paralelo únicamente fuentes habilitadas. Los flags se guardan en DataStore desde Ajustes; desactivar una fuente no afecta la biblioteca local. Los estados previstos son `AVAILABLE`, `DEGRADED`, `UNAVAILABLE`, `AUTH_REQUIRED` y `UNSUPPORTED`; la autenticación queda diseñada para una futura integración legítima, pero esta versión no solicita cuentas.

## Repositorio JSON genérico

`GenericRepositoryProvider` acepta una URL HTTPS desde **Ajustes → Repositorios**. La configuración persiste en DataStore y el host del índice se convierte en la lista explícita de hosts permitidos. El índice mínimo es:

```json
{
  "schemaVersion": 1,
  "characters": [
    {
      "id": "stable-id",
      "name": "Nombre",
      "description": "Descripción",
      "avatarUrl": "https://catalog.example/avatar.webp",
      "cardUrl": "https://catalog.example/card.png",
      "sourceUrl": "https://catalog.example/characters/stable-id",
      "author": "Autor",
      "version": "1",
      "tags": ["RPG"],
      "language": "es",
      "isNsfw": false,
      "updatedAt": 1786723200000
    }
  ]
}
```

Una versión de esquema desconocida falla de forma segura. Las URLs del índice, fuente, tarjeta y avatar deben pertenecer a los hosts configurados.

## Instalación y persistencia

La identidad local se deriva de `providerId + remoteId`; una segunda instalación devuelve el personaje existente. El instalador:

1. descarga primero la tarjeta obligatoria y valida tamaño, MIME y firma;
2. parsea Character Card V1/V2, incluyendo lorebook;
3. intenta el avatar como archivo opcional;
4. escribe tarjeta original y avatar en un directorio temporal privado;
5. calcula SHA-256 de tarjeta y avatar;
6. dentro de una transacción Room guarda personaje, lore y procedencia, y renombra el directorio temporal al destino final;
7. ante cualquier excepción revierte Room y elimina los archivos finalizados como compensación.

`character_sources` conserva proveedor, ID remoto, URL pública, autor, versión, fecha remota, hashes, fecha de descarga, ruta de la tarjeta original, ruta del avatar y estado del avatar. Esto prepara una futura comprobación de actualizaciones sin sobrescribir ediciones locales hoy.

Si el avatar separado falla, el PNG original de la tarjeta actúa como imagen. Si tampoco puede usarse, la UI muestra la inicial; una tarjeta inválida nunca se instala.

## Seguridad y privacidad

- solo HTTPS, puerto 443 y hosts permitidos por proveedor;
- sin tráfico HTTP en el manifiesto;
- timeouts, máximo de tres redirecciones y revalidación de cada destino;
- JSON máximo 2 MiB, tarjeta 20 MiB y avatar 12 MiB;
- validación de firmas PNG, JPEG y WebP; no se confía en la extensión;
- Coil no sigue redirecciones y muestra fallback ante error;
- Coil limita su caché de disco a 128 MiB y su caché de memoria al 15 % disponible;
- no se envían chats, mensajes, memorias, prompts, modelos, perfiles ni identificadores personales;
- no se inicia una consulta de catálogo hasta que el usuario abre Explorar;
- sin login, analítica ni telemetría añadidos.

## Extender un proveedor

1. Confirmar un mecanismo público actual y sus términos; no deducir endpoints.
2. Crear DTO privados en `data/catalog` y mapearlos a modelos de dominio.
3. Definir hosts permitidos y usar `SecureCatalogHttpClient`.
4. Implementar salud, búsqueda, detalle, tarjeta y avatar.
5. Añadir pruebas de mapper, paginación, errores y archivos maliciosos.
6. Registrar el proveedor en `AppContainer`; si el contrato deja de ser fiable, cambiarlo a `UNSUPPORTED` con un motivo visible.
