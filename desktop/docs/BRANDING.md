# Identidad visual de Local Character Desktop

El icono de escritorio se deriva directamente del recurso original de Android:

- Origen: `app/src/main/res/drawable/ic_launcher_foreground.xml`.
- Copia vectorial: `src-tauri/icons/icon.svg`.
- Artefactos generados para Windows/Linux/macOS: `src-tauri/icons/icon.ico`, PNG e ICNS.

Para regenerar los tamaños después de cambiar el vector:

```powershell
npm.cmd run tauri -- icon src-tauri/icons/icon.svg
```

`tauri.conf.json` incluye los artefactos generados en el bundle. El proceso de compilación no debe sustituir este icono por el icono genérico de Tauri.
