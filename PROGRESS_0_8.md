# Progreso 0.8

- Contexto persistente de grupos en Room 8: escenario, descripción, rol del usuario, reglas, situación inicial/actual, apertura, notas, estado y política de lore.
- Overrides por participante sin modificar Character Cards; `GroupContextResolver` y pruebas de precedencia/contradicciones.
- `MessageActionsBottomSheet` en chats directos y grupales con copiar, TTS, rama, rebobinado confirmado, memoria fijada y reporte local.
- Ramas copian el historial hasta el mensaje elegido; rebobinar elimina desde el mensaje elegido. La UI no contiene lógica de persistencia.
- Navegación medida en debug (`LocalNavigation`) y transiciones de pantalla reducidas a 185 ms; release no añade instrumentación.
- Verificado: `testDebugUnitTest`, `testReleaseUnitTest`, `lintDebug`, `assembleDebug` y `assembleRelease` pasan. La prueba física no se pudo repetir en esta ejecución porque ADB no detectó un dispositivo conectado.
