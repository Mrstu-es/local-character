# Modelos recomendados de Local Character RP

Esta página reúne los GGUF entrenados para conversar con personajes en Local Character. Se publican como archivos adjuntos de una release para que se puedan descargar sin inflar el historial Git.

## Descargas

| Modelo | Tamaño aproximado | Recomendado para | Descarga |
| --- | ---: | --- | --- |
| **LocalCharacter RP Qwen3 0.6B Q8_0** | 610 MiB | Android y equipos con poca RAM. Es la opción ligera para empezar. | [Descargar GGUF](https://github.com/Mrstu-es/local-character/releases/download/models-v0.1/LocalCharacter-RP-Qwen3-0.6B-v0.1-Q8_0.gguf) |
| **Llama LocalCharacter RP 3B Q4_K_M** | 1,93 GiB | Desktop y teléfonos con más memoria disponible. Ofrece más capacidad de contexto y expresión. | [Descargar GGUF](https://github.com/Mrstu-es/local-character/releases/download/models-v0.1/Llama-LocalCharacter-RP-3B-v0.1-Q4_K_M.gguf) |

Ambos son modelos de generación de texto en formato GGUF y se pueden importar desde **Modelos → Añadir modelo GGUF**. La aplicación detecta la plantilla del archivo en modo `AUTO`; si el dispositivo no tiene memoria suficiente, empieza con el Qwen3 0.6B.

## Integridad

Verifica el SHA-256 después de descargar. En PowerShell:

```powershell
Get-FileHash .\LocalCharacter-RP-Qwen3-0.6B-v0.1-Q8_0.gguf -Algorithm SHA256
Get-FileHash .\Llama-LocalCharacter-RP-3B-v0.1-Q4_K_M.gguf -Algorithm SHA256
```

| Archivo | SHA-256 |
| --- | --- |
| `LocalCharacter-RP-Qwen3-0.6B-v0.1-Q8_0.gguf` | `675D70B5EF08E69AB5B153F28080CC4D24F72348173DEB83325D5B37967A2D35` |
| `Llama-LocalCharacter-RP-3B-v0.1-Q4_K_M.gguf` | `2838CEA4034D96BC9DED5D3531C405039806DA7BBD8683A54684CBF657B7C0D7` |

La release de modelos es independiente de los instaladores Android/Windows. Las conversaciones siguen siendo locales cuando se usa cualquiera de estos GGUF.
