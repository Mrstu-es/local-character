# Rendimiento y benchmarks

## Métricas

- tiempo de carga del modelo;
- TTFT (time to first token);
- prompt tokens/s;
- generation tokens/s;
- tokens generados;
- RAM y VRAM, indicando si la lectura es exacta o aproximada;
- backend, GPU layers, contexto, batch y ubatch.

## Reproducibilidad

Cada benchmark debe guardar el modelo, cuantización, backend, configuración completa, fecha y hardware detectado. No se suben resultados ni conversaciones a un servidor.

## Reglas de UI

El streaming se agrupa para no pagar una llamada IPC por carácter. React no debe renderizar toda la aplicación por cada delta y el historial debe usar virtualización cuando sea grande. La inferencia siempre corre fuera del hilo de la interfaz.
