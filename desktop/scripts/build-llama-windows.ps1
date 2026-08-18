param(
    [ValidateSet("cpu", "cuda", "vulkan", "cuda-vulkan")]
    [string]$Backend = "cpu",
    [string]$BuildDirectory = (Join-Path $PSScriptRoot "..\native\build")
)

$ErrorActionPreference = "Stop"
$llamaSource = Join-Path $PSScriptRoot "..\native\llama.cpp"
$binDirectory = Join-Path $PSScriptRoot "..\native\bin"

if (-not (Test-Path (Join-Path $llamaSource "CMakeLists.txt"))) {
    throw "Falta native/llama.cpp. Ejecuta primero .\scripts\fetch-llama.ps1"
}

$flags = @("-DGGML_NATIVE=OFF", "-DGGML_BACKEND_DL=ON")
switch ($Backend) {
    "cuda" { $flags += "-DGGML_CUDA=ON" }
    "vulkan" { $flags += "-DGGML_VULKAN=ON" }
    "cuda-vulkan" { $flags += "-DGGML_CUDA=ON"; $flags += "-DGGML_VULKAN=ON" }
}

cmake -S $llamaSource -B $BuildDirectory @flags -DCMAKE_BUILD_TYPE=Release
cmake --build $BuildDirectory --config Release --target llama-cli
cmake --build $BuildDirectory --config Release --target llama-server
New-Item -ItemType Directory -Force -Path $binDirectory | Out-Null
$built = Get-ChildItem -Path $BuildDirectory -Filter "llama-cli.exe" -Recurse | Select-Object -First 1
if (-not $built) { throw "CMake terminó sin producir llama-cli.exe" }
Copy-Item -Force $built.FullName (Join-Path $binDirectory "llama-cli.exe")
$serverBuilt = Get-ChildItem -Path $BuildDirectory -Filter "llama-server.exe" -Recurse | Select-Object -First 1
if (-not $serverBuilt) { throw "CMake no produjo llama-server.exe" }
Copy-Item -Force $serverBuilt.FullName (Join-Path $binDirectory "llama-server.exe")
# llama-cli.exe se construye como un ejecutable pequeño y depende de las DLL
# generadas en el mismo directorio de salida. Copiarlas junto al binario hace
# que el runtime funcione tanto desde desarrollo como desde el instalador NSIS.
$releaseDirectory = Split-Path -Parent $built.FullName
Get-ChildItem -Path $releaseDirectory -Filter "*.dll" -File | ForEach-Object {
    Copy-Item -Force $_.FullName (Join-Path $binDirectory $_.Name)
}
Write-Host "Backend $Backend preparado en $binDirectory\llama-cli.exe y llama-server.exe"
