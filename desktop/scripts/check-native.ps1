$ErrorActionPreference = "Stop"
$cli = Join-Path $PSScriptRoot "..\native\bin\llama-cli.exe"
$server = Join-Path $PSScriptRoot "..\native\bin\llama-server.exe"
if (-not (Test-Path $cli)) {
    throw "No existe llama-cli.exe. Ejecuta fetch-llama.ps1 y build-llama-windows.ps1."
}
& $cli --version
if ($LASTEXITCODE -ne 0) { throw "llama-cli --version fallo con codigo $LASTEXITCODE" }
& $cli --list-devices
if ($LASTEXITCODE -ne 0) { throw "llama-cli --list-devices fallo con codigo $LASTEXITCODE" }
if (-not (Test-Path $server)) {
    throw "No existe llama-server.exe. Ejecuta build-llama-windows.ps1 para preparar el runtime de chat."
}
& $server --version
if ($LASTEXITCODE -ne 0) { throw "llama-server --version fallo con codigo $LASTEXITCODE" }
