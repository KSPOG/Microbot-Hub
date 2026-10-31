param(
    [Parameter(Mandatory=$false)]
    [string]$RepoRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

$path = Join-Path $RepoRoot "src\main\java\net\runelite\client\plugins\microbot\kspautorepair\KspAutoRepairCoordinator.java"
if (-not (Test-Path $path)) {
    throw "KspAutoRepairCoordinator.java not found at: $path"
}

$text = Get-Content -Raw -LiteralPath $path

$oldTargetBlock = @'
        PluginIdentity target = chooseTarget(frame.activeKspPlugins, line);
        if (target == null || isCoolingDown(target.className))
        {
            return;
        }
        double confidence = logConfidence(kind, line, target);
'@

$newTargetBlock = @'
        PluginIdentity target = chooseTarget(frame.activeKspPlugins, line);
        if (target == null || isCoolingDown(target.className))
        {
            return;
        }

        // Never assign an arbitrary external RuneLite/Microbot exception to whichever KSP plugin
        // happens to be active. A log-derived incident must identify the KSP plugin in the current
        // line or the immediately preceding log context. Action/state incidents are unaffected.
        if (!recentLogContextIdentifiesPlugin(target))
        {
            recordEvent("UNATTRIBUTED_LOG_ERROR", null, truncate(line, 800),
                    detail("classification", kind.name()), false);
            return;
        }

        double confidence = logConfidence(kind, line, target);
'@

if (-not $text.Contains($oldTargetBlock)) {
    if ($text.Contains('recentLogContextIdentifiesPlugin(target)')) {
        Write-Host "Log attribution guard is already installed."
    }
    else {
        throw "Could not find the expected log-target block. Coordinator source has changed; no partial edit was made."
    }
}
else {
    $text = $text.Replace($oldTargetBlock, $newTargetBlock)
}

$helperMarker = @'
    private FailureKind classifyLogLine(String line)
'@

$helper = @'
    private boolean recentLogContextIdentifiesPlugin(PluginIdentity target)
    {
        if (target == null)
        {
            return false;
        }

        String simple = simpleName(target.className).toLowerCase(Locale.ROOT);
        String display = safe(target.displayName).toLowerCase(Locale.ROOT);
        List<String> context;
        synchronized (recentLogs)
        {
            context = new ArrayList<>(recentLogs);
        }

        int inspected = 0;
        for (int i = context.size() - 1; i >= 0 && inspected < 6; i--, inspected++)
        {
            String lower = safe(context.get(i)).toLowerCase(Locale.ROOT);
            if ((!simple.isEmpty() && lower.contains(simple))
                    || (!display.isEmpty() && lower.contains(display)))
            {
                return true;
            }
        }
        return false;
    }

    private FailureKind classifyLogLine(String line)
'@

if (-not $text.Contains('private boolean recentLogContextIdentifiesPlugin(PluginIdentity target)')) {
    if (-not $text.Contains($helperMarker)) {
        throw "Could not find classifyLogLine insertion point. No file was written."
    }
    $text = $text.Replace($helperMarker, $helper)
}

# Make runtime logging show the actual hotfix version instead of the older hard-coded v0.2 label.
$text = $text.Replace("KSP Auto Repair v0.2 ", "KSP Auto Repair v0.2.3 ")

$backup = "$path.v0.2.2.bak"
if (-not (Test-Path $backup)) {
    Copy-Item -LiteralPath $path -Destination $backup
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($path, $text, $utf8NoBom)
Write-Host "Patched coordinator:"
Write-Host "  $path"
Write-Host "Backup:"
Write-Host "  $backup"
Write-Host ""
Write-Host "Installed:"
Write-Host "  - external-log attribution guard"
Write-Host "  - v0.2.3 coordinator log labels"
