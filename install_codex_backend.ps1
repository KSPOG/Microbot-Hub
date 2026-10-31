param(
    [Parameter(Mandatory=$false)]
    [string]$RepoRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

$sourceRoot = Join-Path $PSScriptRoot "src\main\java\net\runelite\client\plugins\microbot\kspautorepair"
$targetRoot = Join-Path $RepoRoot "src\main\java\net\runelite\client\plugins\microbot\kspautorepair"

if (-not (Test-Path $targetRoot)) {
    throw "KSP Auto Repair target folder not found: $targetRoot`nRun this script with -RepoRoot pointing at your Microbot-Hub checkout."
}

$files = @(
    "KspAgentBackend.java",
    "KspAutoRepairConfig.java",
    "KspAutoRepairPlugin.java"
)

foreach ($file in $files) {
    Copy-Item -Force (Join-Path $sourceRoot $file) (Join-Path $targetRoot $file)
    Write-Host "Installed $file"
}

Write-Host ""
Write-Host "Codex / ChatGPT backend files installed."
Write-Host "Build with:"
Write-Host "  ./gradlew build -PpluginList=KspAutoRepairPlugin"
