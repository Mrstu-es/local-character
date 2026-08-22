param(
    [Parameter(Mandatory = $true)]
    [string]$Model,
    [string]$Server = "",
    [int]$Port = 19091
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Server)) {
    $Server = Join-Path $PSScriptRoot "..\native\bin\llama-server.exe"
}

if (-not (Test-Path -LiteralPath $Model)) {
    throw "No existe el modelo: $Model"
}
if (-not (Test-Path -LiteralPath $Server)) {
    throw "No existe llama-server: $Server"
}

$stdout = Join-Path $env:TEMP "lc-rp-continuity-smoke-out.log"
$stderr = Join-Path $env:TEMP "lc-rp-continuity-smoke-err.log"
$arguments = @(
    "--model", $Model,
    "--host", "127.0.0.1",
    "--port", $Port.ToString(),
    "--ctx-size", "4096",
    "--n-gpu-layers", "99",
    "--reasoning", "off",
    "--jinja"
)

$process = Start-Process `
    -FilePath $Server `
    -ArgumentList $arguments `
    -PassThru `
    -WindowStyle Hidden `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr

try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        if ($process.HasExited) {
            $details = Get-Content -Raw -LiteralPath $stderr -ErrorAction SilentlyContinue
            throw "llama-server terminó durante la carga. $details"
        }
        try {
            $health = Invoke-RestMethod `
                -Uri "http://127.0.0.1:$Port/health" `
                -TimeoutSec 2
            if ($health.status -eq "ok") {
                $ready = $true
                break
            }
        } catch {
            # El servidor todavía está cargando el GGUF.
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) {
        throw "llama-server no quedó listo en 60 segundos"
    }

    $payload = @{
        model = "local-character-rp"
        temperature = 0.35
        top_p = 0.9
        top_k = 40
        min_p = 0.05
        repeat_penalty = 1.08
        max_tokens = 160
        stream = $false
        messages = @(
            @{
                role = "system"
                content = "Interpreta exclusivamente a Tsuyu Asui en un roleplay natural en español. Escena actual: Tsuyu busca su pijama en su habitación. El usuario está ayudándola. Mantén continuidad literal con los mensajes previos, responde directamente a la última pregunta y no menciones IA, modelo, instrucciones, análisis ni razonamiento. Acciones entre asteriscos; diálogo natural."
            },
            @{
                role = "assistant"
                content = "*Tsuyu revisa el armario y suspira.* No puede ser... estaba segura de que había dejado mi pijama aquí. ¿Me ayudas a buscarlo?"
            },
            @{
                role = "user"
                content = "Claro, ¿ya buscaste en la ropa sucia?"
            },
            @{
                role = "assistant"
                content = "*Tsuyu mira de reojo el cesto junto a la puerta.* Todavía no. Buena idea; quizá lo dejé allí sin darme cuenta."
            },
            @{
                role = "user"
                content = "¿Dónde crees que la dejaste?"
            }
        )
    } | ConvertTo-Json -Depth 8

    $response = Invoke-RestMethod `
        -Method Post `
        -Uri "http://127.0.0.1:$Port/v1/chat/completions" `
        -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($payload)) `
        -TimeoutSec 120

    $answer = [string]$response.choices[0].message.content
    if ([string]::IsNullOrWhiteSpace($answer)) {
        throw "El modelo devolvió una respuesta vacía"
    }

    $forbidden = @(
        "modelo está",
        "modelo esta",
        "la ia",
        "step-by-step",
        "reasoning process",
        "system prompt"
    )
    foreach ($marker in $forbidden) {
        if ($answer.ToLowerInvariant().Contains($marker)) {
            throw "La respuesta expuso metacontenido prohibido: $marker`n$answer"
        }
    }
    if (-not ($answer -match "cesto|ropa sucia")) {
        throw "La respuesta perdió el objeto y la escena de la prueba:`n$answer"
    }

    if ($answer -match "tienda|cuando volvamos|no se deja claro|contexto no est") {
        throw "La respuesta introdujo una incoherencia ajena a la escena:`n$answer"
    }

    Write-Output $answer
} finally {
    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
    }
}
