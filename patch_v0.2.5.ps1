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

# ---------------- Coordinator ----------------
$coordinator = [System.IO.File]::ReadAllText($coordinatorPath)

$startMarker = '        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();'
$endMarker   = '        return new CompilationResult(success, "system-javac");'

if (-not $coordinator.Contains('fallback.mode=parse-only')) {
    $start = $coordinator.IndexOf($startMarker)
    if ($start -lt 0) {
        throw "Could not find system-javac fallback start marker in KspAutoRepairCoordinator.java."
    }

    $end = $coordinator.IndexOf($endMarker, $start)
    if ($end -lt 0) {
        throw "Could not find system-javac fallback end marker in KspAutoRepairCoordinator.java."
    }
    $end += $endMarker.Length

    $newFallback = @'
        /*
         * v0.2.5 fallback policy:
         *
         * The managed ksppluginsrelease repository is source-only. The running
         * Source Loader compiles it with a richer runtime/classloader environment
         * than System.getProperty("java.class.path") exposes here (for example
         * PluginConstants and Lombok support). A repo-wide ordinary javac type
         * check therefore produces deterministic false negatives.
         *
         * When the Source Loader's compiler API is not reflectively callable,
         * this fallback performs PARSE-ONLY validation. That still rejects broken
         * Java syntax before a push. Full attribution/type checking is deferred
         * to the actual Source Loader after the candidate commit is pushed. If the
         * loader rejects that revision, the pending-repair watchdog/log path
         * automatically reverts the candidate.
         */
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
        {
            reportLines.add("fallback.compiler=system-javac");
            reportLines.add("fallback.mode=parse-only");
            reportLines.add("fallback.success=false");
            reportLines.add("fallback.error=No system Java compiler is available in this JVM");
            Files.write(report, reportLines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return new CompilationResult(false, "system-javac-parse-only");
        }

        List<File> javaFiles;
        try (Stream<Path> stream = Files.walk(repository))
        {
            javaFiles = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains(File.separator + ".git" + File.separator))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean success;
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8))
        {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(javaFiles);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    Collections.singletonList("-proc:none"),
                    null,
                    units);

            if (!(task instanceof com.sun.source.util.JavacTask))
            {
                reportLines.add("fallback.compiler=system-javac");
                reportLines.add("fallback.mode=parse-only");
                reportLines.add("fallback.success=false");
                reportLines.add("fallback.error=Compiler task does not implement com.sun.source.util.JavacTask");
                Files.write(report, reportLines, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return new CompilationResult(false, "system-javac-parse-only");
            }

            ((com.sun.source.util.JavacTask) task).parse();
            success = diagnostics.getDiagnostics().stream()
                    .noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR);
        }

        reportLines.add("fallback.compiler=system-javac");
        reportLines.add("fallback.mode=parse-only");
        reportLines.add("fallback.success=" + success);
        reportLines.add("fallback.sources=" + javaFiles.size());
        reportLines.add("fallback.authority=Source Loader runtime compile after push");
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics())
        {
            String source = diagnostic.getSource() == null ? "<unknown>" : diagnostic.getSource().getName();
            reportLines.add(diagnostic.getKind() + " " + source + ":" + diagnostic.getLineNumber()
                    + ":" + diagnostic.getColumnNumber() + " " + diagnostic.getMessage(Locale.ROOT));
        }
        Files.write(report, reportLines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return new CompilationResult(success, "system-javac-parse-only");
'@

    $coordinator = $coordinator.Substring(0, $start) + $newFallback + $coordinator.Substring($end)
}

# Roll back immediately if the Source Loader rejects the pushed candidate while
# we are still waiting for its revision to become loadable.
$oldFailureGate = @'
        PendingRepair pending = pendingRepair;
        if (pending != null && pending.loaderSeenAtMs > 0L && isSourceLoaderFailure(line))
        {
            scheduleRollback(pending, "Source Loader rejected repaired revision: " + truncate(line, 600));
            return;
        }
'@

$newFailureGate = @'
        PendingRepair pending = pendingRepair;
        if (pending != null
                && isSourceLoaderFailure(line)
                && (pending.loaderSeenAtMs > 0L
                    || phase == Phase.WAITING_FOR_LOADER
                    || phase == Phase.VALIDATING_RUNTIME))
        {
            scheduleRollback(pending, "Source Loader rejected repaired revision: " + truncate(line, 600));
            return;
        }
'@

if ($coordinator.Contains($oldFailureGate)) {
    $coordinator = $coordinator.Replace($oldFailureGate, $newFailureGate)
}
elseif (-not $coordinator.Contains('|| phase == Phase.WAITING_FOR_LOADER')) {
    throw "Could not find Source Loader failure gate in KspAutoRepairCoordinator.java."
}

$coordinator = $coordinator.Replace("KSP Auto Repair v0.2.4 ", "KSP Auto Repair v0.2.5 ")
$coordinator = $coordinator.Replace("KSP Auto Repair v0.2.4`"", "KSP Auto Repair v0.2.5`"")

$coordBackup = "$coordinatorPath.v0.2.4.bak"
if (-not (Test-Path -LiteralPath $coordBackup)) {
    Copy-Item -LiteralPath $coordinatorPath -Destination $coordBackup
}
[System.IO.File]::WriteAllText($coordinatorPath, $coordinator, $utf8NoBom)

# ---------------- Plugin version ----------------
$plugin = [System.IO.File]::ReadAllText($pluginPath)
if ($plugin.Contains('public static final String VERSION = "0.2.4";')) {
    $plugin = $plugin.Replace(
        'public static final String VERSION = "0.2.4";',
        'public static final String VERSION = "0.2.5";'
    )
}
elseif (-not $plugin.Contains('public static final String VERSION = "0.2.5";')) {
    throw "Expected v0.2.4/v0.2.5 VERSION marker was not found in KspAutoRepairPlugin.java."
}
[System.IO.File]::WriteAllText($pluginPath, $plugin, $utf8NoBom)

Write-Host ""
Write-Host "KSP Auto Repair v0.2.5 validation hotfix installed."
Write-Host ""
Write-Host "Changes:"
Write-Host "  - removes repo-wide incomplete-classpath javac type-check fallback"
Write-Host "  - fallback now performs Java parse/syntax validation only"
Write-Host "  - Source Loader remains authoritative for full compile/type validation"
Write-Host "  - Source Loader compile rejection can trigger rollback while WAITING_FOR_LOADER"
Write-Host "  - runtime labels updated to v0.2.5"
Write-Host ""
Write-Host "Backup:"
Write-Host "  $coordBackup"
Write-Host ""
Write-Host "Build:"
Write-Host "  ./gradlew build -PpluginList=KspAutoRepairPlugin"
