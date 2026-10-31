param(
    [Parameter(Mandatory=$false)]
    [string]$RepoRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

$base = Join-Path $RepoRoot "src\main\java\net\runelite\client\plugins\microbot\kspautorepair"
$coordinatorPath = Join-Path $base "KspAutoRepairCoordinator.java"
$pluginPath = Join-Path $base "KspAutoRepairPlugin.java"

foreach ($path in @($coordinatorPath, $pluginPath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Required source file not found: $path"
    }
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Replace-Required {
    param(
        [string]$Text,
        [string]$Old,
        [string]$New,
        [string]$Description
    )

    if ($Text.Contains($New)) {
        return $Text
    }
    if (-not $Text.Contains($Old)) {
        throw "Could not patch $Description because the expected source block was not found."
    }
    return $Text.Replace($Old, $New)
}

function Replace-BetweenRequired {
    param(
        [string]$Text,
        [string]$StartMarker,
        [string]$EndMarker,
        [string]$Replacement,
        [string]$Description
    )

    $start = $Text.IndexOf($StartMarker)
    if ($start -lt 0) {
        throw "Could not patch $Description because the start marker was not found."
    }
    $end = $Text.IndexOf($EndMarker, $start)
    if ($end -lt 0) {
        throw "Could not patch $Description because the end marker was not found."
    }
    return $Text.Substring(0, $start) + $Replacement + $Text.Substring($end)
}

$coordinator = [System.IO.File]::ReadAllText($coordinatorPath).Replace("`r`n", "`n")

# ---------------------------------------------------------------------------
# Imports needed for client-log timestamp recovery.
# ---------------------------------------------------------------------------
if (-not $coordinator.Contains("import java.time.LocalDateTime;")) {
    $coordinator = Replace-Required `
        -Text $coordinator `
        -Old "import java.time.Instant;`nimport java.time.ZoneOffset;" `
        -New "import java.time.Instant;`nimport java.time.LocalDateTime;`nimport java.time.ZoneId;`nimport java.time.ZoneOffset;" `
        -Description "loader recovery imports"
}

# ---------------------------------------------------------------------------
# Constants used to detect authoritative successful Source Loader load lines.
# ---------------------------------------------------------------------------
if (-not $coordinator.Contains("LOADER_LOG_RECOVERY_BYTES")) {
    $shaLine = '    private static final Pattern SHA_PATTERN = Pattern.compile("\\b([0-9a-fA-F]{7,40})\\b");'
    $constants = @'
    private static final Pattern SHA_PATTERN = Pattern.compile("\\b([0-9a-fA-F]{7,40})\\b");
    private static final DateTimeFormatter CLIENT_LOG_LOCAL_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private static final long LOADER_LOG_RECOVERY_BYTES = 16L * 1024L * 1024L;
'@
    $coordinator = Replace-Required `
        -Text $coordinator `
        -Old $shaLine `
        -New $constants.TrimEnd() `
        -Description "loader handshake constants"
}

# ---------------------------------------------------------------------------
# Startup recovery. This is important after a restart with pending-v2.json.
# It also initializes lastSeenRevision from the last *successfully loaded*
# Source Loader revision, not from an arbitrary fetched token.
# ---------------------------------------------------------------------------
$oldStart = @'
        pendingRepair = readPendingRepair();
        sourceLoaderBridge.attach();
        KspObservation.install(this);
'@
$newStart = @'
        pendingRepair = readPendingRepair();
        sourceLoaderBridge.attach();
        recoverLoaderStateFromLog();
        KspObservation.install(this);
'@
$coordinator = Replace-Required `
    -Text $coordinator `
    -Old $oldStart `
    -New $newStart `
    -Description "startup loader revision recovery"

# ---------------------------------------------------------------------------
# Do not use the reflective currentRevision value to accept a pending repair.
# It may describe a fetched/current token before the full source load is known
# to have succeeded. Successful load logs/listener events are authoritative.
# ---------------------------------------------------------------------------
$oldMonitor = '            sourceLoaderBridge.currentRevision().ifPresent(this::observeRevision);'
$newMonitor = @'
            if (pendingRepair == null)
            {
                sourceLoaderBridge.currentRevision().ifPresent(this::observeRevision);
            }
'@
$coordinator = Replace-Required `
    -Text $coordinator `
    -Old $oldMonitor `
    -New $newMonitor.TrimEnd() `
    -Description "pending-repair currentRevision gating"

# ---------------------------------------------------------------------------
# Direct listener callbacks: only acceptance/loaded/completed callbacks may
# move a pending repair into runtime validation.
# ---------------------------------------------------------------------------
$oldLoaderEvent = @'
        recordEvent("SOURCE_LOADER_" + event, null, event, details, false);
        extractRevision(details).ifPresent(this::observeRevision);
        String lower = (event + " " + String.valueOf(details)).toLowerCase(Locale.ROOT);
'@
$newLoaderEvent = @'
        recordEvent("SOURCE_LOADER_" + event, null, event, details, false);
        String lower = (event + " " + String.valueOf(details)).toLowerCase(Locale.ROOT);
        Optional<String> callbackRevision = extractRevision(details);
        if (callbackRevision.isPresent())
        {
            if (isLoaderAcceptanceText(lower))
            {
                observeLoadedRevision(callbackRevision.get(), "loader-callback:" + event);
            }
            else if (pendingRepair == null)
            {
                observeRevision(callbackRevision.get());
            }
        }
'@
$coordinator = Replace-Required `
    -Text $coordinator `
    -Old $oldLoaderEvent `
    -New $newLoaderEvent `
    -Description "Source Loader callback acceptance semantics"

# ---------------------------------------------------------------------------
# Persist the Source Loader baseline separately from the Git commit SHA.
# Git SHA and Source Loader revision are different identifier domains.
# ---------------------------------------------------------------------------
$oldPendingCommit = @'
        pending.beforeSha = beforeSha;
        pending.commitSha = repairSha;
        pending.pushedAtMs = System.currentTimeMillis();
'@
$newPendingCommit = @'
        pending.beforeSha = beforeSha;
        pending.commitSha = repairSha;
        pending.loaderRevisionBeforePush = safe(incident.lastSeenSourceRevision);
        if (pending.loaderRevisionBeforePush.isEmpty())
        {
            pending.loaderRevisionBeforePush = safe(lastSeenRevision);
        }
        pending.pushedAtMs = System.currentTimeMillis();
'@
$coordinator = Replace-Required `
    -Text $coordinator `
    -Old $oldPendingCommit `
    -New $newPendingCommit `
    -Description "pending Source Loader baseline"

# ---------------------------------------------------------------------------
# Only "Loaded ... from source revision ..." is an authoritative log-based
# acceptance signal. "Fetched ..." and "Preparing to compile ..." are not.
# ---------------------------------------------------------------------------
$oldRevisionLog = @'
        Matcher revisionMatcher = REVISION_PATTERN.matcher(line);
        if (revisionMatcher.find())
        {
            observeRevision(revisionMatcher.group(1));
        }
'@
$newRevisionLog = @'
        Matcher revisionMatcher = REVISION_PATTERN.matcher(line);
        if (revisionMatcher.find() && isSuccessfulSourceLoaderLoadLine(line))
        {
            observeLoadedRevision(revisionMatcher.group(1), "client-log");
        }
'@
$coordinator = Replace-Required `
    -Text $coordinator `
    -Old $oldRevisionLog `
    -New $newRevisionLog `
    -Description "authoritative Source Loader log acceptance"

# ---------------------------------------------------------------------------
# Replace the old Git-SHA == loader-revision handshake with a separate loader
# revision state machine.
# ---------------------------------------------------------------------------
$observeStart = "    private void observeRevision(String revision)`n    {"
$scheduleStart = "    private void scheduleRollback(PendingRepair pending, String reason)"
$newObserve = @'
    private void observeRevision(String revision)
    {
        if (revision == null || revision.isEmpty())
        {
            return;
        }
        lastSeenRevision = revision.toLowerCase(Locale.ROOT);
    }

    /**
     * Accepts a Source Loader revision only after the loader has positively
     * reported that the source revision was loaded. The loader revision token
     * is deliberately NOT compared to the Git commit SHA: they are different
     * identifier domains.
     */
    private void observeLoadedRevision(String revision, String mechanism)
    {
        if (revision == null || revision.isEmpty())
        {
            return;
        }

        String normalized = revision.toLowerCase(Locale.ROOT);
        lastSeenRevision = normalized;

        PendingRepair pending = pendingRepair;
        if (pending == null || pending.loaderSeenAtMs > 0L || pending.rollbackScheduled)
        {
            return;
        }

        String baseline = safe(pending.loaderRevisionBeforePush).toLowerCase(Locale.ROOT);
        if (!baseline.isEmpty() && revisionMatches(normalized, baseline))
        {
            recordEvent("SOURCE_LOADER_REVISION_UNCHANGED", pending.targetPluginClass,
                    normalized, detail("mechanism", mechanism), false);
            return;
        }

        pending.loadedSourceRevision = normalized;
        pending.loaderSeenAtMs = System.currentTimeMillis();
        pending.validationStartProgress = semanticProgress.get();
        writePendingRepair(pending);

        setPhase(Phase.VALIDATING_RUNTIME,
                "commit=" + safe(pending.commitSha) + " source=" + normalized);
        Path incidentDir = getIncidentRoot().resolve(pending.incidentId);
        writeStatus(incidentDir, "VALIDATING_RUNTIME",
                "Source Loader successfully loaded source revision " + normalized
                        + " after repair commit " + safe(pending.commitSha)
                        + " via " + mechanism);
        recordEvent("REPAIR_REVISION_LOADED", pending.targetPluginClass,
                normalized, detail("mechanism", mechanism), false);
    }

'@
$coordinator = Replace-BetweenRequired `
    -Text $coordinator `
    -StartMarker $observeStart `
    -EndMarker $scheduleStart `
    -Replacement $newObserve `
    -Description "Git SHA / Source Loader revision handshake"

# ---------------------------------------------------------------------------
# Log recovery and rollback fail-safe.
# ---------------------------------------------------------------------------
if (-not $coordinator.Contains("private void recoverLoaderStateFromLog()")) {
    $initMarker = "    private void initialiseLogOffset()`n    {"
    $recoveryMethods = @'
    /**
     * Recovers Source Loader state from authoritative successful-load log
     * entries. This also migrates pending repairs created by v0.2.5.
     */
    private void recoverLoaderStateFromLog()
    {
        PendingRepair pending = pendingRepair;
        long pushedAtMs = pending == null ? 0L : pending.pushedAtMs;
        LoaderRevisionRecovery recovery = scanLoaderRevisionLog(pushedAtMs);

        if (!recovery.latestLoadedRevision.isEmpty())
        {
            lastSeenRevision = recovery.latestLoadedRevision;
        }

        if (pending == null)
        {
            return;
        }

        boolean changed = false;
        if (safe(pending.loaderRevisionBeforePush).isEmpty()
                && !recovery.beforePushRevision.isEmpty())
        {
            pending.loaderRevisionBeforePush = recovery.beforePushRevision;
            changed = true;
        }

        if (pending.loaderSeenAtMs == 0L
                && !recovery.afterPushRevision.isEmpty())
        {
            String baseline = safe(pending.loaderRevisionBeforePush);
            if (baseline.isEmpty()
                    || !revisionMatches(recovery.afterPushRevision, baseline))
            {
                observeLoadedRevision(recovery.afterPushRevision, "startup/watchdog-log-recovery");
                return;
            }
        }

        if (changed)
        {
            writePendingRepair(pending);
        }
    }

    private LoaderRevisionRecovery scanLoaderRevisionLog(long pushedAtMs)
    {
        LoaderRevisionRecovery result = new LoaderRevisionRecovery();
        Path path = getClientLogPath();
        if (!Files.isRegularFile(path))
        {
            return result;
        }

        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r"))
        {
            long length = file.length();
            long start = Math.max(0L, length - LOADER_LOG_RECOVERY_BYTES);
            file.seek(start);
            if (start > 0L)
            {
                file.readLine(); // discard a partial first line
            }

            String line;
            while ((line = file.readLine()) != null)
            {
                if (!isSuccessfulSourceLoaderLoadLine(line))
                {
                    continue;
                }

                Matcher matcher = REVISION_PATTERN.matcher(line);
                if (!matcher.find())
                {
                    continue;
                }

                String revision = matcher.group(1).toLowerCase(Locale.ROOT);
                result.latestLoadedRevision = revision;

                if (pushedAtMs <= 0L)
                {
                    continue;
                }

                long timestampMs = clientLogTimestampMs(line);
                if (timestampMs <= 0L)
                {
                    continue;
                }

                if (timestampMs <= pushedAtMs)
                {
                    result.beforePushRevision = revision;
                }
                else
                {
                    result.afterPushRevision = revision;
                    result.afterPushTimestampMs = timestampMs;
                }
            }
        }
        catch (Throwable t)
        {
            log.debug("Unable to recover Source Loader revision state from client log", t);
        }
        return result;
    }

    private long clientLogTimestampMs(String line)
    {
        if (line == null || line.length() < 19)
        {
            return -1L;
        }
        try
        {
            LocalDateTime local = LocalDateTime.parse(line.substring(0, 19), CLIENT_LOG_LOCAL_TIME);
            return local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        catch (Throwable ignored)
        {
            return -1L;
        }
    }

    private boolean isSuccessfulSourceLoaderLoadLine(String line)
    {
        String lower = safe(line).toLowerCase(Locale.ROOT);
        return lower.contains("loaded ")
                && lower.contains("ksp plugin")
                && lower.contains("from source revision");
    }

    private boolean isLoaderAcceptanceText(String text)
    {
        String lower = safe(text).toLowerCase(Locale.ROOT);
        boolean failure = lower.contains("fail") || lower.contains("reject") || lower.contains("error");
        boolean accepted = lower.contains("loaded") || lower.contains("accepted")
                || lower.contains("load_complete") || lower.contains("loadcomplete");
        return accepted && !failure;
    }

'@
    $coordinator = Replace-BetweenRequired `
        -Text $coordinator `
        -StartMarker $initMarker `
        -EndMarker $initMarker `
        -Replacement $recoveryMethods `
        -Description "loader log recovery methods"
}

# ---------------------------------------------------------------------------
# Before the watchdog rolls back a supposedly unseen loader revision, rescan
# successful loader logs. If the repair was actually loaded, the phase changes
# to VALIDATING_RUNTIME and rollback is skipped.
# ---------------------------------------------------------------------------
$oldWatchdogRollback = @'
        if (current == Phase.WAITING_FOR_LOADER && pendingRepair != null && config.autoRollback())
        {
            scheduleRollback(pendingRepair,
                    "Source Loader did not observe repaired revision within watchdog deadline");
        }
'@
$newWatchdogRollback = @'
        if (current == Phase.WAITING_FOR_LOADER && pendingRepair != null && config.autoRollback())
        {
            recoverLoaderStateFromLog();
            if (phase != Phase.WAITING_FOR_LOADER
                    || pendingRepair == null
                    || pendingRepair.loaderSeenAtMs > 0L)
            {
                return;
            }
            scheduleRollback(pendingRepair,
                    "Source Loader did not report a successfully loaded source revision after the repair push");
        }
'@
$coordinator = Replace-Required `
    -Text $coordinator `
    -Old $oldWatchdogRollback `
    -New $newWatchdogRollback `
    -Description "watchdog pre-rollback loader rescan"

# ---------------------------------------------------------------------------
# v0.2.5 may have recorded the correct fix as rolled back only because of the
# handshake bug. Do not tell Codex that every historical rollback is a bad code
# fix; distinguish runtime/compiler regressions from loader-observation failures.
# ---------------------------------------------------------------------------
$oldHistoryPrompt = 'Use successful outcomes as evidence and do not repeat rolled-back fixes.'
$newHistoryPrompt = 'Use successful outcomes as evidence. Treat rolled-back fixes as negative code evidence only when the recorded outcome shows a compile rejection or runtime regression; loader-observation/time-out rollbacks are infrastructure evidence and may be retried when the source diagnosis remains supported.'
if ($coordinator.Contains($oldHistoryPrompt)) {
    $coordinator = $coordinator.Replace($oldHistoryPrompt, $newHistoryPrompt)
}

# ---------------------------------------------------------------------------
# Pending repair schema.
# ---------------------------------------------------------------------------
$oldPendingFields = @'
        String beforeSha;
        String commitSha;
        String repositoryPath;
        String agentSummary;
        long pushedAtMs;
        long loaderSeenAtMs;
'@
$newPendingFields = @'
        String beforeSha;
        String commitSha;
        String loaderRevisionBeforePush;
        String loadedSourceRevision;
        String repositoryPath;
        String agentSummary;
        long pushedAtMs;
        long loaderSeenAtMs;
'@
$coordinator = Replace-Required `
    -Text $coordinator `
    -Old $oldPendingFields `
    -New $newPendingFields `
    -Description "pending loader revision fields"

# Loader revision recovery DTO.
if (-not $coordinator.Contains("static final class LoaderRevisionRecovery")) {
    $pendingClassMarker = "    static final class PendingRepair`n    {"
    $recoveryClass = @'
    static final class LoaderRevisionRecovery
    {
        String latestLoadedRevision = "";
        String beforePushRevision = "";
        String afterPushRevision = "";
        long afterPushTimestampMs;
    }

'@
    $coordinator = Replace-BetweenRequired `
        -Text $coordinator `
        -StartMarker $pendingClassMarker `
        -EndMarker $pendingClassMarker `
        -Replacement $recoveryClass `
        -Description "LoaderRevisionRecovery DTO"
}

# Version labels.
$coordinator = $coordinator.Replace("KSP Auto Repair v0.2.5", "KSP Auto Repair v0.2.6")

$coordBackup = "$coordinatorPath.v0.2.5.bak"
if (-not (Test-Path -LiteralPath $coordBackup)) {
    Copy-Item -LiteralPath $coordinatorPath -Destination $coordBackup
}
[System.IO.File]::WriteAllText($coordinatorPath, $coordinator, $utf8NoBom)

# ---------------------------------------------------------------------------
# Plugin version.
# ---------------------------------------------------------------------------
$plugin = [System.IO.File]::ReadAllText($pluginPath)
if ($plugin.Contains('public static final String VERSION = "0.2.5";')) {
    $plugin = $plugin.Replace(
        'public static final String VERSION = "0.2.5";',
        'public static final String VERSION = "0.2.6";'
    )
}
elseif (-not $plugin.Contains('public static final String VERSION = "0.2.6";')) {
    throw "Expected v0.2.5/v0.2.6 VERSION marker was not found in KspAutoRepairPlugin.java."
}
[System.IO.File]::WriteAllText($pluginPath, $plugin, $utf8NoBom)

Write-Host ""
Write-Host "KSP Auto Repair v0.2.6 loader-handshake hotfix installed."
Write-Host ""
Write-Host "Fixes:"
Write-Host "  - Git commit SHA is no longer compared to Source Loader revision tokens"
Write-Host "  - only successful 'Loaded ... from source revision ...' lines accept a repair"
Write-Host "  - Source Loader revision before push is stored separately"
Write-Host "  - loaded source revision is stored separately"
Write-Host "  - startup can recover/migrate pending repairs from client.log"
Write-Host "  - watchdog rescans successful load logs before rollback"
Write-Host "  - loader timeout rollbacks no longer poison Codex history as bad code fixes"
Write-Host ""
Write-Host "Backup:"
Write-Host "  $coordBackup"
Write-Host ""
Write-Host "Build:"
Write-Host "  ./gradlew build -PpluginList=KspAutoRepairPlugin"
