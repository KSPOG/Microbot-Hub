param(
    [Parameter(Mandatory=$false)]
    [string]$RepoRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

$sourceRoot = Join-Path $PSScriptRoot "src\main\java\net\runelite\client\plugins\microbot\kspautorepair"
$targetRoot = Join-Path $RepoRoot "src\main\java\net\runelite\client\plugins\microbot\kspautorepair"

if (-not (Test-Path $targetRoot)) {
    throw "KSP Auto Repair target folder not found: $targetRoot"
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

& (Join-Path $PSScriptRoot "patch_coordinator_v0.2.3.ps1") -RepoRoot $RepoRoot

Write-Host ""
Write-Host "KSP Auto Repair v0.2.3 hotfix installed."
Write-Host "Build with:"
Write-Host "  ./gradlew build -PpluginList=KspAutoRepairPlugin"
