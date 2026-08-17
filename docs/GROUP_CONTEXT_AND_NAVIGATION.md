# Contexto de grupo y navegación

Room 8 mantiene `GroupContext` y `GroupParticipantContext` separados de las Character Cards. `GroupContextResolver` aplica la precedencia contexto/reglas del grupo → override del participante → escenario de la ficha, conservando personalidad e identidad. El lore es `ADAPTIVE`, `ORIGINAL` o `DISABLED`; el primer mensaje individual no se copia al grupo.

La pulsación larga de un mensaje abre `MessageActionsBottomSheet`. La UI solo despacha acciones; ViewModels y repositorios implementan copiar, voz, ramas, rebobinado, memoria fijada y reporte local. Rama y rebobinado solicitan confirmación antes de modificar el historial.

Las transiciones de pantalla usan 185 ms o menos. `NavigationPerformance` existe únicamente en debug y registra `tap route`, `firstComposition` y `dataReady`; no bloquea la navegación ni la inferencia. No se incluye una afirmación de Macrobenchmark porque este proyecto todavía no tiene módulo de benchmark.
