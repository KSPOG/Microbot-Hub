param(
    [Parameter(Mandatory=$false)]
    [string]$RepoRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

$base = Join-Path $RepoRoot "src\main\java\net\runelite\client\plugins\microbot\kspautorepair"
$bridgePath = Join-Path $base "KspSourceLoaderBridge.java"
$pluginPath = Join-Path $base "KspAutoRepairPlugin.java"
$coordinatorPath = Join-Path $base "KspAutoRepairCoordinator.java"

foreach ($path in @($bridgePath, $pluginPath, $coordinatorPath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Required source file not found: $path"
    }
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# ---------- KspSourceLoaderBridge.java ----------
$bridge = [System.IO.File]::ReadAllText($bridgePath)

$oldCompilerMatch = @'
            if (!method.getName().toLowerCase(Locale.ROOT).contains("compile") || method.getParameterCount() != 1)
            {
                continue;
            }
'@

$newCompilerMatch = @'
            String methodName = method.getName().toLowerCase(Locale.ROOT);
            // v0.2.4: only invoke actual compiler entrypoints. The previous contains("compile")
            // check also matched helper/readback methods such as readCompiledClasses(...), because
            // "compiled" contains "compile". That could return an empty map and falsely reject a
            // valid Codex candidate before the real compiler ever ran.
            boolean compileEntryPoint = methodName.equals("compile")
                    || (methodName.startsWith("compile") && !methodName.startsWith("compiled"));
            if (!compileEntryPoint || method.getParameterCount() != 1)
            {
                continue;
            }
'@

if ($bridge.Contains($oldCompilerMatch)) {
    $bridge = $bridge.Replace($oldCompilerMatch, $newCompilerMatch)
}
elseif (-not $bridge.Contains('boolean compileEntryPoint = methodName.equals("compile")')) {
    throw "Could not find the old InMemoryJavaCompiler method-selection block. No bridge file was written."
}

$oldOuterCatch = @'
        catch (Throwable t)
        {
            return PreflightResult.result(false, "source-loader-bridge", t.toString());
        }
        return PreflightResult.unavailable("No compatible source-loader validation method was discoverable");
'@

$newOuterCatch = @'
        catch (Throwable t)
        {
            // Reflection/discovery failure is not proof that the candidate does not compile.
            // Mark the Source Loader preflight unavailable so the coordinator can fall back to
            // ToolProvider.getSystemJavaCompiler(). Actual discovered validation/compiler methods
            // still return authoritative success/failure results above.
            return PreflightResult.unavailable("Source-loader bridge preflight unavailable: " + t);
        }
        return PreflightResult.unavailable("No compatible source-loader validation method was discoverable");
'@

if ($bridge.Contains($oldOuterCatch)) {
    $bridge = $bridge.Replace($oldOuterCatch, $newOuterCatch)
}
elseif (-not $bridge.Contains("Source-loader bridge preflight unavailable: ")) {
    throw "Could not find the preflight outer-catch block. No bridge file was written."
}

$bridgeBackup = "$bridgePath.v0.2.3.bak"
if (-not (Test-Path -LiteralPath $bridgeBackup)) {
    Copy-Item -LiteralPath $bridgePath -Destination $bridgeBackup
}
[System.IO.File]::WriteAllText($bridgePath, $bridge, $utf8NoBom)

# ---------- Version labels ----------
$plugin = [System.IO.File]::ReadAllText($pluginPath)
if ($plugin.Contains('public static final String VERSION = "0.2.3";')) {
    $plugin = $plugin.Replace(
        'public static final String VERSION = "0.2.3";',
        'public static final String VERSION = "0.2.4";'
    )
}
elseif (-not $plugin.Contains('public static final String VERSION = "0.2.4";')) {
    throw "Expected v0.2.3/v0.2.4 VERSION marker was not found in KspAutoRepairPlugin.java."
}
[System.IO.File]::WriteAllText($pluginPath, $plugin, $utf8NoBom)

$coordinator = [System.IO.File]::ReadAllText($coordinatorPath)
$coordinator = $coordinator.Replace("KSP Auto Repair v0.2.3 ", "KSP Auto Repair v0.2.4 ")
[System.IO.File]::WriteAllText($coordinatorPath, $coordinator, $utf8NoBom)

Write-Host ""
Write-Host "KSP Auto Repair v0.2.4 preflight hotfix installed."
Write-Host ""
Write-Host "Fixed:"
Write-Host "  - readCompiledClasses(...) can no longer be mistaken for a compile entrypoint"
Write-Host "  - bridge discovery exceptions fall back to system javac instead of rejecting candidates"
Write-Host "  - plugin/runtime version labels updated to v0.2.4"
Write-Host ""
Write-Host "Backup:"
Write-Host "  $bridgeBackup"
Write-Host ""
Write-Host "Build with:"
Write-Host "  ./gradlew build -PpluginList=KspAutoRepairPlugin"
