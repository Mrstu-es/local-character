param(
    [string]$Tag = "b10434"
)

$ErrorActionPreference = "Stop"
$workspaceRoot = Split-Path -Parent $PSScriptRoot
$thirdPartyRoot = Join-Path $workspaceRoot "third_party"
$target = Join-Path $thirdPartyRoot "llama.cpp"
if (Test-Path -LiteralPath $target) {
    throw "llama.cpp already exists at $target"
}
$archive = Join-Path $thirdPartyRoot "llama-$Tag.zip"
$extract = Join-Path $thirdPartyRoot "llama-$Tag-extract"
New-Item -ItemType Directory -Force -Path $thirdPartyRoot | Out-Null
Invoke-WebRequest -Headers @{ "User-Agent" = "LocalCharacterBuild" } `
    -Uri "https://github.com/ggml-org/llama.cpp/archive/refs/tags/$Tag.zip" `
    -OutFile $archive
New-Item -ItemType Directory -Force -Path $extract | Out-Null
Expand-Archive -LiteralPath $archive -DestinationPath $extract
$source = Get-ChildItem -LiteralPath $extract -Directory | Select-Object -First 1
Move-Item -LiteralPath $source.FullName -Destination $target
Remove-Item -LiteralPath $archive
Remove-Item -LiteralPath $extract
Write-Host "Installed llama.cpp $Tag at $target"
