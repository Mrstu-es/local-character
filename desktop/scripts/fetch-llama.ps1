param(
    [string]$Destination = (Join-Path $PSScriptRoot "..\native\llama.cpp")
)

$ErrorActionPreference = "Stop"
$versionFile = Join-Path $PSScriptRoot "..\native\llama.cpp.version"
$version = Get-Content -Raw $versionFile
$tag = ([regex]::Match($version, "(?m)^tag=(.+)$")).Groups[1].Value.Trim()
$commit = ([regex]::Match($version, "(?m)^commit=(.+)$")).Groups[1].Value.Trim()

if ([string]::IsNullOrWhiteSpace($tag) -or [string]::IsNullOrWhiteSpace($commit)) {
    throw "native/llama.cpp.version no contiene tag y commit válidos."
}

if (Test-Path (Join-Path $Destination ".git")) {
    Push-Location $Destination
    try {
        git fetch --tags --force origin $tag
        git checkout --detach $commit
    } finally {
        Pop-Location
    }
    exit 0
}

if (Test-Path $Destination) {
    throw "La carpeta de llama.cpp existe pero no es un checkout Git: $Destination"
}

git clone --filter=blob:none --no-checkout https://github.com/ggml-org/llama.cpp.git $Destination
Push-Location $Destination
try {
    git fetch --tags --force origin $tag
    git checkout --detach $commit
} finally {
    Pop-Location
}

Write-Host "llama.cpp fijado en $tag ($commit)."
