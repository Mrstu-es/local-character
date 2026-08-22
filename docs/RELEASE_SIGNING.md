# Firma y publicación de releases

## Android

El flujo `.github/workflows/release.yml` publica `assembleRelease`, no un APK de depuración. La clave privada nunca debe entrar al repositorio.

El repositorio necesita estos secretos de GitHub Actions:

- `LOCAL_CHARACTER_ANDROID_KEYSTORE_BASE64`
- `LOCAL_CHARACTER_ANDROID_STORE_PASSWORD`
- `LOCAL_CHARACTER_ANDROID_KEY_ALIAS`
- `LOCAL_CHARACTER_ANDROID_KEY_PASSWORD`

El primer valor es el keystore PKCS#12 completo codificado en Base64. Los otros tres corresponden a la misma clave. El workflow reconstruye el keystore dentro de `RUNNER_TEMP`, compila el APK firmado y ejecuta `apksigner verify` antes de publicarlo.

La clave de publicación es permanente: perderla impide instalar futuras actualizaciones sobre las versiones ya publicadas. Debe conservarse en una copia de seguridad privada fuera del repositorio.

## Windows

Tauri genera un instalador NSIS (`.exe`) y un paquete MSI. Actualmente no se aplica Authenticode, por lo que Windows puede mostrar “editor desconocido” o SmartScreen. Añadir una firma reconocida requiere un certificado real de firma de código y secretos separados; nunca se debe inventar ni versionar uno dentro del proyecto.
