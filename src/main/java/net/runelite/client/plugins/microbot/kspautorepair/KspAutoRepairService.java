package net.runelite.client.plugins.microbot.kspautorepair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runtime observer and autonomous repair coordinator for KSP plugins.
 *
 * The service deliberately does not load replacement classes itself. It edits the repository consumed by
 * KSP Source Loader, validates the complete Java source set, pushes a new revision and then observes the
 * existing source loader accepting or rejecting that revision.
 */
final class KspAutoRepairService
{
    private static final Logger log = LoggerFactory.getLogger(KspAutoRepairService.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern REVISION_PATTERN = Pattern.compile("revision\\s+([0-9a-fA-F]{7,40})", Pattern.CASE_INSENSITIVE);
    private static final int TRACE_LIMIT = 300;
    private static final int LOG_LIMIT = 400;
    private static final long MONITOR_PERIOD_SECONDS = 2L;
    private static final String REPAIR_ROOT_NAME = "ksp-agent-repair";

    private final Client client;
    private final PluginManager pluginManager;
    private final KspAutoRepairConfig config;
    private final ScheduledExecutorService monitorExecutor;
    private final ScheduledExecutorService repairExecutor;
    private final Deque<RuntimeFrame> trace = new ArrayDeque<>();
    private final Deque<String> recentLogs = new ArrayDeque<>();
    private final Map<String, Long> incidentCooldowns = new HashMap<>();
    private final AtomicBoolean repairInProgress = new AtomicBoolean(false);
    private final AtomicReference<RuntimeFrame> lastFrame = new AtomicReference<>();

    private volatile boolean running;
    private volatile long lastProgressAtMs;
    private volatile String lastFingerprint = "";
    private volatile long logOffset;
    private volatile String lastSeenRevision = "";
    private volatile PendingRepair pendingRepair;

    KspAutoRepairService(Client client, PluginManager pluginManager, KspAutoRepairConfig config)
    {
        this.client = client;
        this.pluginManager = pluginManager;
        this.config = config;
        this.monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> daemonThread(r, "ksp-auto-repair-monitor"));
        this.repairExecutor = Executors.newSingleThreadScheduledExecutor(r -> daemonThread(r, "ksp-auto-repair-agent"));
    }

    void start()
    {
        if (running)
        {
            return;
        }

        running = true;
        lastProgressAtMs = System.currentTimeMillis();
        initialiseLogOffset();
        pendingRepair = readPendingRepair();

        monitorExecutor.scheduleWithFixedDelay(this::monitorSafely,
                MONITOR_PERIOD_SECONDS,
                MONITOR_PERIOD_SECONDS,
                TimeUnit.SECONDS);

        log.info("KSP Auto Repair service started | fullAuto={} backend={} source={}",
                config.fullAuto(), config.agentBackend(), config.repositoryUrl());
    }

    void stop()
    {
        running = false;
        monitorExecutor.shutdownNow();
        repairExecutor.shutdownNow();
        log.info("KSP Auto Repair service stopped");
    }

    void onGameTick()
    {
        if (!running)
        {
            return;
        }

        RuntimeFrame frame = captureRuntimeFrame();
        lastFrame.set(frame);

        synchronized (trace)
        {
            trace.addLast(frame);
            while (trace.size() > TRACE_LIMIT)
            {
                trace.removeFirst();
            }
        }

        if (!frame.fingerprint.equals(lastFingerprint))
        {
            lastFingerprint = frame.fingerprint;
            lastProgressAtMs = frame.timestampMs;
        }
    }

    private void monitorSafely()
    {
        if (!running)
        {
            return;
        }

        try
        {
            pollClientLog();
            evaluatePendingRepair();
            evaluateStall();
        }
        catch (Throwable t)
        {
            log.warn("KSP Auto Repair monitor cycle failed", t);
        }
    }

    private RuntimeFrame captureRuntimeFrame()
    {
        long now = System.currentTimeMillis();
        GameState gameState = client.getGameState();
        Player player = client.getLocalPlayer();

        int x = -1;
        int y = -1;
        int plane = -1;
        int animation = -1;
        String interacting = "";

        if (player != null)
        {
            if (player.getWorldLocation() != null)
            {
                x = player.getWorldLocation().getX();
                y = player.getWorldLocation().getY();
                plane = player.getWorldLocation().getPlane();
            }
            animation = player.getAnimation();
            Actor actor = player.getInteracting();
            if (actor != null && actor.getName() != null)
            {
                interacting = actor.getName();
            }
        }

        List<PluginIdentity> activeKspPlugins = getActiveKspPlugins();
        long inventoryHash = gameState == GameState.LOGGED_IN ? inventoryHash() : 0L;
        long xpHash = gameState == GameState.LOGGED_IN ? xpHash() : 0L;
        String status = safeString(Microbot.status);

        String fingerprint = gameState.name() + '|' + x + '|' + y + '|' + plane + '|' + animation + '|'
                + interacting + '|' + inventoryHash + '|' + xpHash + '|' + status + '|'
                + activeKspPlugins.stream().map(p -> p.className).sorted().collect(Collectors.joining(","));

        return new RuntimeFrame(now, gameState.name(), x, y, plane, animation, interacting,
                status, inventoryHash, xpHash, activeKspPlugins, fingerprint);
    }

    private long inventoryHash()
    {
        ItemContainer container = client.getItemContainer(InventoryID.INV);
        if (container == null)
        {
            return 0L;
        }

        long hash = 1125899906842597L;
        for (Item item : container.getItems())
        {
            hash = 31L * hash + item.getId();
            hash = 31L * hash + item.getQuantity();
        }
        return hash;
    }

    private long xpHash()
    {
        long hash = 17L;
        for (Skill skill : Skill.values())
        {
            hash = 31L * hash + client.getSkillExperience(skill);
        }
        return hash;
    }

    private List<PluginIdentity> getActiveKspPlugins()
    {
        Collection<Plugin> plugins = pluginManager.getPlugins();
        if (plugins == null || plugins.isEmpty())
        {
            return Collections.emptyList();
        }

        List<PluginIdentity> result = new ArrayList<>();
        for (Plugin plugin : plugins)
        {
            if (plugin == null || !pluginManager.isPluginActive(plugin) || !isKspPlugin(plugin))
            {
                continue;
            }

            PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
            String name = descriptor == null ? plugin.getClass().getSimpleName() : descriptor.name();
            result.add(new PluginIdentity(name, plugin.getClass().getName()));
        }
        result.sort(Comparator.comparing(p -> p.className));
        return result;
    }

    private boolean isKspPlugin(Plugin plugin)
    {
        String className = plugin.getClass().getName();
        String lowerClass = className.toLowerCase(Locale.ROOT);
        if (lowerClass.contains(".kspautorepair.") || lowerClass.contains(".loader.kspsourceloader"))
        {
            return false;
        }

        String simple = plugin.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (simple.startsWith("ksp"))
        {
            return true;
        }

        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        if (descriptor == null)
        {
            return false;
        }

        if (descriptor.name().toLowerCase(Locale.ROOT).contains("ksp"))
        {
            return true;
        }

        for (String tag : descriptor.tags())
        {
            if ("ksp".equalsIgnoreCase(tag))
            {
                return true;
            }
        }
        return false;
    }

    private void evaluateStall()
    {
        RuntimeFrame frame = lastFrame.get();
        if (frame == null || !GameState.LOGGED_IN.name().equals(frame.gameState) || frame.activeKspPlugins.isEmpty())
        {
            return;
        }
        if (repairInProgress.get() || pendingRepair != null)
        {
            return;
        }
        if (isIntentionalWait(frame.microbotStatus))
        {
            lastProgressAtMs = System.currentTimeMillis();
            return;
        }

        long stagnantForMs = System.currentTimeMillis() - lastProgressAtMs;
        if (stagnantForMs < TimeUnit.SECONDS.toMillis(config.stallSeconds()))
        {
            return;
        }

        PluginIdentity target = chooseTarget(frame.activeKspPlugins, null);
        if (target == null || isCoolingDown(target.className))
        {
            return;
        }

        lastProgressAtMs = System.currentTimeMillis();
        triggerIncident("STALL",
                "Meaningful runtime fingerprint has not changed for " + TimeUnit.MILLISECONDS.toSeconds(stagnantForMs) + " seconds",
                target,
                null);
    }

    private boolean isIntentionalWait(String status)
    {
        if (status == null)
        {
            return false;
        }
        String value = status.toLowerCase(Locale.ROOT);
        return value.equals("idle")
                || value.contains("break")
                || value.contains("logout")
                || value.contains("waiting for login")
                || value.contains("world hop")
                || value.contains("hopping world");
    }

    private void pollClientLog() throws IOException
    {
        Path logFile = getClientLogPath();
        if (!Files.isRegularFile(logFile))
        {
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r"))
        {
            long length = raf.length();
            if (length < logOffset)
            {
                logOffset = 0L;
            }
            raf.seek(logOffset);

            String line;
            while ((line = raf.readLine()) != null)
            {
                acceptLogLine(line);
            }
            logOffset = raf.getFilePointer();
        }
    }

    private void acceptLogLine(String line)
    {
        synchronized (recentLogs)
        {
            recentLogs.addLast(line);
            while (recentLogs.size() > LOG_LIMIT)
            {
                recentLogs.removeFirst();
            }
        }

        Matcher matcher = REVISION_PATTERN.matcher(line);
        if (matcher.find())
        {
            lastSeenRevision = matcher.group(1).toLowerCase(Locale.ROOT);
            PendingRepair pending = pendingRepair;
            if (pending != null && revisionMatches(lastSeenRevision, pending.commitSha))
            {
                if (pending.sourceLoaderSeenAtMs == 0L)
                {
                    pending.sourceLoaderSeenAtMs = System.currentTimeMillis();
                    pending.sourceLoaderRevision = lastSeenRevision;
                    writePendingRepair(pending);
                    log.info("KSP Auto Repair observed source loader fetching repaired revision {}", lastSeenRevision);
                }
            }
        }

        PendingRepair pending = pendingRepair;
        if (pending != null && pending.sourceLoaderSeenAtMs > 0L && isSourceLoaderFailure(line))
        {
            scheduleRollback(pending, "Source loader rejected repaired revision: " + line);
            return;
        }

        if (!config.fullAuto() || repairInProgress.get() || pendingRepair != null)
        {
            return;
        }

        RuntimeFrame frame = lastFrame.get();
        if (frame == null || frame.activeKspPlugins.isEmpty())
        {
            return;
        }

        if (isRelevantErrorLine(line, frame.activeKspPlugins))
        {
            PluginIdentity target = chooseTarget(frame.activeKspPlugins, line);
            if (target != null && !isCoolingDown(target.className))
            {
                triggerIncident("LOG_ERROR", line, target, line);
            }
        }
    }

    private boolean isRelevantErrorLine(String line, List<PluginIdentity> activePlugins)
    {
        String lower = line.toLowerCase(Locale.ROOT);
        boolean errorSignal = lower.contains(" error ")
                || lower.contains("exception")
                || lower.contains("must be called on client thread")
                || lower.contains("ksp source update failed")
                || lower.contains("could not switch")
                || lower.contains("stopped:");
        if (!errorSignal)
        {
            return false;
        }

        if (lower.contains("ksp") || lower.contains("microbot.loader"))
        {
            return true;
        }

        for (PluginIdentity plugin : activePlugins)
        {
            String simple = simpleName(plugin.className).toLowerCase(Locale.ROOT);
            if (lower.contains(simple))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isSourceLoaderFailure(String line)
    {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("ksp source update failed")
                || lower.contains("ksp runtime compilation failed")
                || lower.contains("source update rejected")
                || (lower.contains("pluginmanager.loadplugins failed") && lower.contains("ksp"));
    }

    private PluginIdentity chooseTarget(List<PluginIdentity> plugins, String evidence)
    {
        if (plugins == null || plugins.isEmpty())
        {
            return null;
        }

        if (evidence != null)
        {
            String lower = evidence.toLowerCase(Locale.ROOT);
            for (PluginIdentity plugin : plugins)
            {
                if (lower.contains(simpleName(plugin.className).toLowerCase(Locale.ROOT)))
                {
                    return plugin;
                }
            }
        }

        for (PluginIdentity plugin : plugins)
        {
            if (!plugin.className.toLowerCase(Locale.ROOT).contains("autorun"))
            {
                return plugin;
            }
        }
        return plugins.get(0);
    }

    private boolean isCoolingDown(String className)
    {
        Long last = incidentCooldowns.get(className);
        return last != null && System.currentTimeMillis() - last < TimeUnit.SECONDS.toMillis(config.incidentCooldownSeconds());
    }

    private void triggerIncident(String type, String reason, PluginIdentity target, String triggerLogLine)
    {
        if (!running || target == null || !repairInProgress.compareAndSet(false, true))
        {
            return;
        }

        incidentCooldowns.put(target.className, System.currentTimeMillis());
        repairExecutor.execute(() -> {
            try
            {
                handleIncident(type, reason, target, triggerLogLine);
            }
            catch (Throwable t)
            {
                log.error("KSP Auto Repair incident handling failed for {}", target.className, t);
            }
            finally
            {
                repairInProgress.set(false);
            }
        });
    }

    private void handleIncident(String type, String reason, PluginIdentity target, String triggerLogLine) throws Exception
    {
        Incident incident = createIncident(type, reason, target, triggerLogLine);
        Path incidentDir = getIncidentRoot().resolve(incident.incidentId);
        Files.createDirectories(incidentDir);

        Path screenshot = null;
        if (config.captureScreenshots())
        {
            screenshot = captureScreenshot(incidentDir.resolve("screenshot.png"));
        }
        incident.screenshot = screenshot == null ? null : screenshot.toAbsolutePath().toString();
        incident.pluginState = inspectPluginState(target.className);
        incident.lastSeenSourceRevision = lastSeenRevision;

        Path incidentJson = incidentDir.resolve("incident.json");
        writeJsonAtomic(incidentJson, incident);
        writeLines(incidentDir.resolve("client-tail.log"), snapshotLogs());

        log.warn("KSP Auto Repair incident {} created | type={} plugin={} reason={}",
                incident.incidentId, type, target.className, reason);

        if (!config.fullAuto() || config.agentBackend() == KspAgentBackend.DISABLED)
        {
            writeStatus(incidentDir, "MONITOR_ONLY", "Incident captured; automatic repair is disabled");
            return;
        }

        Path repo = managedRepositoryPath();
        syncManagedRepository(repo, incidentDir);

        String sourceArea = sourceArea(target.className);
        if (sourceArea.isEmpty() || !Files.isDirectory(repo.resolve(sourceArea)))
        {
            writeStatus(incidentDir, "UNMAPPED_SOURCE",
                    "No source directory for " + target.className + " exists in configured source repository");
            log.warn("KSP Auto Repair cannot auto-fix {} because source area '{}' is not present in {}",
                    target.className, sourceArea, repo);
            return;
        }

        String beforeSha = captureProcess(Arrays.asList("git", "rev-parse", "HEAD"), repo, Duration.ofSeconds(30)).trim();
        String originalFingerprint = lastFingerprint;
        boolean compiled = false;
        Path compileReport = incidentDir.resolve("compile-validation.txt");

        for (int attempt = 1; attempt <= config.maxAgentAttempts(); attempt++)
        {
            Path promptFile = incidentDir.resolve("agent-prompt-" + attempt + ".txt");
            String prompt = buildAgentPrompt(incident, incidentJson, incidentDir, repo, sourceArea, attempt, compileReport);
            Files.write(promptFile, prompt.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Path agentOutput = incidentDir.resolve("agent-output-" + attempt + ".log");
            ProcessResult agentResult = invokeAgent(prompt, repo, agentOutput);
            if (agentResult.exitCode != 0)
            {
                writeStatus(incidentDir, "AGENT_FAILED",
                        "Agent attempt " + attempt + " exited with code " + agentResult.exitCode);
                continue;
            }

            List<String> changedFiles = changedFiles(repo);
            if (changedFiles.isEmpty())
            {
                writeStatus(incidentDir, "NO_CHANGE",
                        "Agent attempt " + attempt + " completed without changing source");
                continue;
            }

            String violation = validateChangedPaths(changedFiles, sourceArea);
            if (violation != null)
            {
                resetWorkingTree(repo, incidentDir);
                writeStatus(incidentDir, "GUARDRAIL_REJECTED", violation);
                log.warn("KSP Auto Repair rejected agent edits: {}", violation);
                return;
            }

            CompilationResult compilation = compileRepository(repo, compileReport);
            if (compilation.success)
            {
                compiled = true;
                break;
            }

            writeStatus(incidentDir, "COMPILE_RETRY",
                    "Agent attempt " + attempt + " did not compile; diagnostics were returned to the next attempt");
        }

        if (!compiled)
        {
            resetWorkingTree(repo, incidentDir);
            writeStatus(incidentDir, "COMPILE_FAILED", "No agent attempt produced a compilable source tree");
            return;
        }

        List<String> finalChanges = changedFiles(repo);
        Files.write(incidentDir.resolve("changed-files.txt"), finalChanges, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        if (!config.autoPush())
        {
            writeStatus(incidentDir, "VALIDATED_NOT_PUSHED", "Repair compiles; auto-push is disabled");
            return;
        }

        runChecked(Arrays.asList("git", "add", "-A"), repo, Duration.ofSeconds(30), incidentDir.resolve("git.log"));
        runChecked(Arrays.asList("git", "commit", "-m", "agent: repair " + simpleName(target.className) + " [" + incident.incidentId + "]"),
                repo, Duration.ofSeconds(60), incidentDir.resolve("git.log"));
        String commitSha = captureProcess(Arrays.asList("git", "rev-parse", "HEAD"), repo, Duration.ofSeconds(30)).trim();
        runChecked(Arrays.asList("git", "push", "origin", "HEAD:" + config.repositoryBranch()),
                repo, Duration.ofMinutes(2), incidentDir.resolve("git.log"));

        PendingRepair pending = new PendingRepair();
        pending.incidentId = incident.incidentId;
        pending.incidentType = type;
        pending.targetPluginClass = target.className;
        pending.beforeSha = beforeSha;
        pending.commitSha = commitSha;
        pending.pushedAtMs = System.currentTimeMillis();
        pending.prePatchFingerprint = originalFingerprint;
        pending.repositoryPath = repo.toAbsolutePath().toString();
        pendingRepair = pending;
        writePendingRepair(pending);

        writeStatus(incidentDir, "PUSHED",
                "Validated repair pushed as " + commitSha + "; waiting for KSP Source Loader validation");
        log.info("KSP Auto Repair pushed {} for {}. Waiting for source loader to fetch the revision.",
                commitSha, target.className);
    }

    private Incident createIncident(String type, String reason, PluginIdentity target, String triggerLogLine)
    {
        Incident incident = new Incident();
        incident.incidentId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
                .withZone(java.time.ZoneOffset.UTC)
                .format(Instant.now()) + "-" + sanitizeFilePart(simpleName(target.className));
        incident.timestamp = Instant.now().toString();
        incident.type = type;
        incident.reason = reason;
        incident.triggerLogLine = triggerLogLine;
        incident.targetPluginName = target.displayName;
        incident.targetPluginClass = target.className;
        incident.microbotStatus = safeString(Microbot.status);
        incident.repositoryUrl = config.repositoryUrl();
        incident.repositoryBranch = config.repositoryBranch();
        incident.trace = snapshotTrace();
        incident.recentLogs = snapshotLogs();
        incident.runtimeFrame = lastFrame.get();
        return incident;
    }

    private Map<String, Object> inspectPluginState(String className)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        try
        {
            Plugin target = null;
            for (Plugin plugin : pluginManager.getPlugins())
            {
                if (plugin != null && plugin.getClass().getName().equals(className))
                {
                    target = plugin;
                    break;
                }
            }
            if (target == null)
            {
                return result;
            }

            result.put("pluginClass", target.getClass().getName());
            inspectFields(target, "plugin", result, false);

            for (Field field : allFields(target.getClass()))
            {
                if (!field.getName().toLowerCase(Locale.ROOT).contains("script"))
                {
                    continue;
                }
                try
                {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value != null)
                    {
                        inspectFields(value, "script." + field.getName(), result, true);
                    }
                }
                catch (Throwable ignored)
                {
                    // Diagnostic reflection must never interfere with the target plugin.
                }
            }
        }
        catch (Throwable t)
        {
            result.put("inspectionError", t.toString());
        }
        return result;
    }

    private void inspectFields(Object object, String prefix, Map<String, Object> destination, boolean includePrivate)
    {
        for (Field field : allFields(object.getClass()))
        {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()))
            {
                continue;
            }
            if (!includePrivate && !field.getName().toLowerCase(Locale.ROOT).contains("state"))
            {
                continue;
            }
            try
            {
                field.setAccessible(true);
                Object value = field.get(object);
                if (isSafeScalar(value))
                {
                    destination.put(prefix + "." + field.getName(), value == null ? null : String.valueOf(value));
                }
            }
            catch (Throwable ignored)
            {
                // Best-effort diagnostics only.
            }
        }
    }

    private List<Field> allFields(Class<?> type)
    {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        int depth = 0;
        while (current != null && current != Object.class && depth++ < 5)
        {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    private boolean isSafeScalar(Object value)
    {
        return value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value.getClass().isEnum();
    }

    private Path captureScreenshot(Path destination)
    {
        try
        {
            AtomicReference<BufferedImage> imageRef = new AtomicReference<>();
            Runnable capture = () -> {
                int width = Math.max(1, client.getCanvas().getWidth());
                int height = Math.max(1, client.getCanvas().getHeight());
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try
                {
                    client.getCanvas().paint(graphics);
                }
                finally
                {
                    graphics.dispose();
                }
                imageRef.set(image);
            };

            if (SwingUtilities.isEventDispatchThread())
            {
                capture.run();
            }
            else
            {
                SwingUtilities.invokeAndWait(capture);
            }

            BufferedImage image = imageRef.get();
            if (image != null)
            {
                ImageIO.write(image, "png", destination.toFile());
                return destination;
            }
        }
        catch (Throwable t)
        {
            log.warn("KSP Auto Repair screenshot capture failed", t);
        }
        return null;
    }

    private String buildAgentPrompt(Incident incident,
                                    Path incidentJson,
                                    Path incidentDir,
                                    Path repo,
                                    String sourceArea,
                                    int attempt,
                                    Path compileReport)
    {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the autonomous KSP Microbot source repair worker.\n");
        prompt.append("Repository root: ").append(repo.toAbsolutePath()).append('\n');
        prompt.append("Incident JSON: ").append(incidentJson.toAbsolutePath()).append('\n');
        prompt.append("Incident directory: ").append(incidentDir.toAbsolutePath()).append('\n');
        prompt.append("Target plugin: ").append(incident.targetPluginClass).append('\n');
        prompt.append("Allowed source directory: ").append(sourceArea).append("/\n");
        prompt.append("Attempt: ").append(attempt).append('\n');
        if (attempt > 1 && Files.isRegularFile(compileReport))
        {
            prompt.append("The previous attempt did not compile. Read diagnostics at: ")
                    .append(compileReport.toAbsolutePath()).append('\n');
        }
        prompt.append('\n');
        prompt.append("Read incident.json, client-tail.log, and screenshot.png when present. ");
        prompt.append("Inspect the target source and determine the concrete cause from evidence. ");
        prompt.append("Apply the smallest robust Java fix that addresses the observed failure.\n\n");
        prompt.append("HARD RULES:\n");
        prompt.append("1. Modify only .java files under ").append(sourceArea).append("/.\n");
        prompt.append("2. Do not modify the KSP source loader, auto-repair bridge, build infrastructure, credentials, or files outside the repository.\n");
        prompt.append("3. Do not run git commit, git push, git reset, git checkout, or git clean. The bridge owns source control.\n");
        prompt.append("4. Do not disable the failing behavior merely to make the error disappear. Preserve intended plugin functionality.\n");
        prompt.append("5. Do not invent Microbot API methods. Use APIs already present in the source or supported by evidence.\n");
        prompt.append("6. Finish after editing the source. The bridge will compile the full source tree and return diagnostics if needed.\n");
        return prompt.toString();
    }

    private ProcessResult invokeAgent(String prompt, Path repo, Path output) throws Exception
    {
        KspAgentBackend backend = config.agentBackend();
        if (backend == KspAgentBackend.CLAUDE_CODE)
        {
            List<String> args = Arrays.asList(
                    config.agentExecutable(),
                    "-p",
                    prompt,
                    "--allowedTools",
                    "Read,Edit,Write,Glob,Grep",
                    "--model",
                    config.claudeModel(),
                    "--output-format",
                    "json"
            );
            return runAgentCommand(args, repo, output, Duration.ofMinutes(config.agentTimeoutMinutes()));
        }

        if (backend == KspAgentBackend.CUSTOM)
        {
            String template = safeString(config.customAgentCommand()).trim();
            if (template.isEmpty())
            {
                throw new IllegalStateException("Custom agent backend selected but no command is configured");
            }
            Path promptFile = output.getParent().resolve("custom-agent-prompt.txt");
            Files.write(promptFile, prompt.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            String command = template
                    .replace("{repo}", repo.toAbsolutePath().toString())
                    .replace("{prompt}", promptFile.toAbsolutePath().toString());
            List<String> shell = isWindows()
                    ? Arrays.asList("cmd.exe", "/d", "/s", "/c", command)
                    : Arrays.asList("/bin/sh", "-lc", command);
            return runProcess(shell, repo, output, Duration.ofMinutes(config.agentTimeoutMinutes()), false);
        }

        throw new IllegalStateException("Agent backend is disabled");
    }

    private ProcessResult runAgentCommand(List<String> args, Path repo, Path output, Duration timeout) throws Exception
    {
        try
        {
            return runProcess(args, repo, output, timeout, false);
        }
        catch (IOException directFailure)
        {
            if (!isWindows())
            {
                throw directFailure;
            }

            // npm-installed CLIs are commonly .cmd shims on Windows. PowerShell's call operator resolves them
            // while the prompt remains in an environment variable instead of being interpolated into shell text.
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "& $env:KSP_AGENT_EXE -p $env:KSP_AGENT_PROMPT --allowedTools 'Read,Edit,Write,Glob,Grep' --model $env:KSP_AGENT_MODEL --output-format json"
            );
            pb.directory(repo.toFile());
            pb.environment().put("KSP_AGENT_EXE", config.agentExecutable());
            pb.environment().put("KSP_AGENT_PROMPT", args.get(2));
            pb.environment().put("KSP_AGENT_MODEL", config.claudeModel());
            pb.redirectErrorStream(true);
            pb.redirectOutput(output.toFile());
            Process process = pb.start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished)
            {
                process.destroyForcibly();
                return new ProcessResult(124, "timeout");
            }
            return new ProcessResult(process.exitValue(), "");
        }
    }

    private CompilationResult compileRepository(Path repo, Path report) throws IOException
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
        {
            Files.write(report,
                    Collections.singletonList("No system Java compiler is available in this JVM."),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return new CompilationResult(false);
        }

        List<File> javaFiles;
        try (Stream<Path> stream = Files.walk(repo))
        {
            javaFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains(File.separator + ".git" + File.separator))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path outputDir = Files.createTempDirectory(getRepairRoot(), "javac-");
        boolean success = false;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8))
        {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(javaFiles);
            List<String> options = Arrays.asList(
                    "-proc:none",
                    "-classpath", System.getProperty("java.class.path", ""),
                    "-d", outputDir.toAbsolutePath().toString()
            );
            Boolean result = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            success = Boolean.TRUE.equals(result);
        }
        finally
        {
            deleteRecursively(outputDir);
        }

        List<String> lines = new ArrayList<>();
        lines.add("success=" + success);
        lines.add("sources=" + javaFiles.size());
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics())
        {
            String source = diagnostic.getSource() == null ? "<unknown>" : diagnostic.getSource().getName();
            lines.add(diagnostic.getKind() + " " + source + ":" + diagnostic.getLineNumber() + ":" + diagnostic.getColumnNumber()
                    + " " + diagnostic.getMessage(Locale.ROOT));
        }
        Files.write(report, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return new CompilationResult(success);
    }

    private void syncManagedRepository(Path repo, Path incidentDir) throws Exception
    {
        Files.createDirectories(repo.getParent());
        Path gitLog = incidentDir.resolve("git.log");
        if (!Files.isDirectory(repo.resolve(".git")))
        {
            if (Files.exists(repo) && !isDirectoryEmpty(repo))
            {
                throw new IllegalStateException("Managed repository path exists but is not an empty Git repository: " + repo);
            }
            Files.createDirectories(repo);
            runChecked(Arrays.asList("git", "clone", "--branch", config.repositoryBranch(), "--single-branch", config.repositoryUrl(), repo.toAbsolutePath().toString()),
                    repo.getParent(), Duration.ofMinutes(3), gitLog);
            return;
        }

        runChecked(Arrays.asList("git", "fetch", "origin", config.repositoryBranch()), repo, Duration.ofMinutes(2), gitLog);
        runChecked(Arrays.asList("git", "checkout", config.repositoryBranch()), repo, Duration.ofSeconds(30), gitLog);
        runChecked(Arrays.asList("git", "reset", "--hard", "origin/" + config.repositoryBranch()), repo, Duration.ofSeconds(30), gitLog);
        runChecked(Arrays.asList("git", "clean", "-fd"), repo, Duration.ofSeconds(30), gitLog);
    }

    private void resetWorkingTree(Path repo, Path incidentDir) throws Exception
    {
        runChecked(Arrays.asList("git", "reset", "--hard", "HEAD"), repo, Duration.ofSeconds(30), incidentDir.resolve("git.log"));
        runChecked(Arrays.asList("git", "clean", "-fd"), repo, Duration.ofSeconds(30), incidentDir.resolve("git.log"));
    }

    private List<String> changedFiles(Path repo) throws Exception
    {
        String output = captureProcess(Arrays.asList("git", "status", "--porcelain"), repo, Duration.ofSeconds(30));
        if (output.trim().isEmpty())
        {
            return Collections.emptyList();
        }

        List<String> files = new ArrayList<>();
        for (String line : output.split("\\R"))
        {
            if (line.length() < 4)
            {
                continue;
            }
            String path = line.substring(3).trim().replace('\\', '/');
            files.add(path);
        }
        return files;
    }

    private String validateChangedPaths(List<String> changedFiles, String sourceArea)
    {
        String requiredPrefix = sourceArea.replace('\\', '/') + "/";
        for (String path : changedFiles)
        {
            String normalized = path.replace('\\', '/');
            if (normalized.contains(" -> "))
            {
                return "Renames are not allowed during autonomous repair: " + normalized;
            }
            if (!normalized.endsWith(".java"))
            {
                return "Agent changed a non-Java file: " + normalized;
            }
            if (!normalized.startsWith(requiredPrefix))
            {
                return "Agent changed a file outside the target plugin source directory: " + normalized;
            }
            if (normalized.toLowerCase(Locale.ROOT).startsWith("kspautorepair/"))
            {
                return "Agent attempted to modify the repair bridge itself: " + normalized;
            }
        }
        return null;
    }

    private void evaluatePendingRepair()
    {
        PendingRepair pending = pendingRepair;
        if (pending == null || pending.rollbackScheduled)
        {
            return;
        }

        if (pending.sourceLoaderSeenAtMs == 0L)
        {
            return;
        }

        long validationMs = TimeUnit.SECONDS.toMillis(config.validationSeconds());
        if (System.currentTimeMillis() - pending.sourceLoaderSeenAtMs < validationMs)
        {
            return;
        }

        boolean progressObserved = lastProgressAtMs > pending.sourceLoaderSeenAtMs;
        if ("STALL".equals(pending.incidentType) && !progressObserved)
        {
            PluginIdentity target = findActivePluginIdentity(pending.targetPluginClass);
            if (target != null && !repairInProgress.get())
            {
                Path incidentDir = getIncidentRoot().resolve(pending.incidentId);
                writeStatus(incidentDir, "STILL_STALLED", "Repaired revision loaded but no meaningful progress was observed");
                clearPendingRepair();
                triggerIncident("POST_FIX_STALL",
                        "Previous repair revision loaded successfully but the original stall condition remains",
                        target,
                        null);
            }
            return;
        }

        Path incidentDir = getIncidentRoot().resolve(pending.incidentId);
        writeStatus(incidentDir, "VALIDATED", "Source loader accepted the repair and runtime validation passed");
        log.info("KSP Auto Repair validated repaired revision {} for {}", pending.commitSha, pending.targetPluginClass);
        clearPendingRepair();
    }

    private void scheduleRollback(PendingRepair pending, String reason)
    {
        if (pending.rollbackScheduled)
        {
            return;
        }
        pending.rollbackScheduled = true;
        writePendingRepair(pending);
        repairExecutor.execute(() -> rollbackPendingRepair(pending, reason));
    }

    private void rollbackPendingRepair(PendingRepair pending, String reason)
    {
        repairInProgress.set(true);
        try
        {
            Path repo = Paths.get(pending.repositoryPath);
            Path incidentDir = getIncidentRoot().resolve(pending.incidentId);
            Path gitLog = incidentDir.resolve("git.log");

            runChecked(Arrays.asList("git", "fetch", "origin", config.repositoryBranch()), repo, Duration.ofMinutes(2), gitLog);
            runChecked(Arrays.asList("git", "checkout", config.repositoryBranch()), repo, Duration.ofSeconds(30), gitLog);
            runChecked(Arrays.asList("git", "reset", "--hard", "origin/" + config.repositoryBranch()), repo, Duration.ofSeconds(30), gitLog);
            runChecked(Arrays.asList("git", "revert", "--no-edit", pending.commitSha), repo, Duration.ofMinutes(1), gitLog);
            runChecked(Arrays.asList("git", "push", "origin", "HEAD:" + config.repositoryBranch()), repo, Duration.ofMinutes(2), gitLog);

            writeStatus(incidentDir, "ROLLED_BACK", reason);
            log.error("KSP Auto Repair rolled back revision {} because source-loader validation failed", pending.commitSha);
            clearPendingRepair();
        }
        catch (Throwable t)
        {
            log.error("KSP Auto Repair automatic rollback failed for {}", pending.commitSha, t);
            try
            {
                writeStatus(getIncidentRoot().resolve(pending.incidentId), "ROLLBACK_FAILED", reason + " | " + t);
            }
            catch (Throwable ignored)
            {
                // Nothing further can safely be done here.
            }
        }
        finally
        {
            repairInProgress.set(false);
        }
    }

    private PluginIdentity findActivePluginIdentity(String className)
    {
        RuntimeFrame frame = lastFrame.get();
        if (frame == null)
        {
            return null;
        }
        for (PluginIdentity identity : frame.activeKspPlugins)
        {
            if (identity.className.equals(className))
            {
                return identity;
            }
        }
        return null;
    }

    private boolean revisionMatches(String observed, String fullSha)
    {
        if (observed == null || fullSha == null)
        {
            return false;
        }
        String a = observed.toLowerCase(Locale.ROOT);
        String b = fullSha.toLowerCase(Locale.ROOT);
        return a.startsWith(b) || b.startsWith(a);
    }

    private Path managedRepositoryPath()
    {
        String configured = safeString(config.repositoryPath()).trim();
        if (!configured.isEmpty())
        {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        return getRepairRoot().resolve("worktree").resolve("ksppluginsrelease").toAbsolutePath().normalize();
    }

    private Path getRepairRoot()
    {
        Path root = Paths.get(System.getProperty("user.home"), ".runelite", REPAIR_ROOT_NAME);
        try
        {
            Files.createDirectories(root);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Unable to create KSP repair directory " + root, e);
        }
        return root;
    }

    private Path getIncidentRoot()
    {
        Path root = getRepairRoot().resolve("incidents");
        try
        {
            Files.createDirectories(root);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Unable to create KSP incident directory " + root, e);
        }
        return root;
    }

    private Path getPendingPath()
    {
        return getRepairRoot().resolve("pending.json");
    }

    private Path getClientLogPath()
    {
        return Paths.get(System.getProperty("user.home"), ".runelite", "logs", "client.log");
    }

    private void initialiseLogOffset()
    {
        try
        {
            Path file = getClientLogPath();
            logOffset = Files.isRegularFile(file) ? Files.size(file) : 0L;
        }
        catch (IOException e)
        {
            logOffset = 0L;
        }
    }

    private PendingRepair readPendingRepair()
    {
        Path path = getPendingPath();
        if (!Files.isRegularFile(path))
        {
            return null;
        }
        try
        {
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            PendingRepair pending = GSON.fromJson(json, PendingRepair.class);
            if (pending != null)
            {
                log.info("KSP Auto Repair recovered pending repair {} for {}", pending.commitSha, pending.targetPluginClass);
            }
            return pending;
        }
        catch (Throwable t)
        {
            log.warn("Unable to restore KSP Auto Repair pending state", t);
            return null;
        }
    }

    private void writePendingRepair(PendingRepair pending)
    {
        try
        {
            writeJsonAtomic(getPendingPath(), pending);
        }
        catch (IOException e)
        {
            log.warn("Unable to persist KSP Auto Repair pending state", e);
        }
    }

    private void clearPendingRepair()
    {
        pendingRepair = null;
        try
        {
            Files.deleteIfExists(getPendingPath());
        }
        catch (IOException e)
        {
            log.warn("Unable to clear KSP Auto Repair pending state", e);
        }
    }

    private List<RuntimeFrame> snapshotTrace()
    {
        synchronized (trace)
        {
            return new ArrayList<>(trace);
        }
    }

    private List<String> snapshotLogs()
    {
        synchronized (recentLogs)
        {
            return new ArrayList<>(recentLogs);
        }
    }

    private void writeJsonAtomic(Path path, Object value) throws IOException
    {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.write(temp, GSON.toJson(value).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try
        {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException atomicMoveFailure)
        {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeStatus(Path incidentDir, String status, String message)
    {
        try
        {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("timestamp", Instant.now().toString());
            payload.put("status", status);
            payload.put("message", message);
            writeJsonAtomic(incidentDir.resolve("repair-status.json"), payload);
        }
        catch (IOException e)
        {
            log.warn("Unable to write KSP repair status", e);
        }
    }

    private void writeLines(Path path, List<String> lines) throws IOException
    {
        Files.write(path, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private ProcessResult runAgentProcess(ProcessBuilder pb, Path output, Duration timeout) throws Exception
    {
        pb.redirectErrorStream(true);
        pb.redirectOutput(output.toFile());
        Process process = pb.start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished)
        {
            process.destroyForcibly();
            return new ProcessResult(124, "timeout");
        }
        return new ProcessResult(process.exitValue(), "");
    }

    private ProcessResult runProcess(List<String> command, Path workingDir, Path output, Duration timeout, boolean append) throws Exception
    {
        Files.createDirectories(output.getParent());
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(append ? ProcessBuilder.Redirect.appendTo(output.toFile()) : ProcessBuilder.Redirect.to(output.toFile()));
        Process process = pb.start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished)
        {
            process.destroyForcibly();
            return new ProcessResult(124, "timeout");
        }
        return new ProcessResult(process.exitValue(), "");
    }

    private void runChecked(List<String> command, Path workingDir, Duration timeout, Path logFile) throws Exception
    {
        ProcessResult result = runProcess(command, workingDir, logFile, timeout, true);
        if (result.exitCode != 0)
        {
            throw new IllegalStateException("Command failed with exit code " + result.exitCode + ": " + String.join(" ", command));
        }
    }

    private String captureProcess(List<String> command, Path workingDir, Duration timeout) throws Exception
    {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = process.getInputStream().read(buffer)) >= 0)
        {
            output.write(buffer, 0, read);
        }
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished)
        {
            process.destroyForcibly();
            throw new IllegalStateException("Command timed out: " + String.join(" ", command));
        }
        String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0)
        {
            throw new IllegalStateException("Command failed: " + String.join(" ", command) + "\n" + text);
        }
        return text;
    }

    private boolean isDirectoryEmpty(Path directory) throws IOException
    {
        if (!Files.isDirectory(directory))
        {
            return true;
        }
        try (Stream<Path> stream = Files.list(directory))
        {
            return !stream.findFirst().isPresent();
        }
    }

    private void deleteRecursively(Path root)
    {
        if (root == null || !Files.exists(root))
        {
            return;
        }
        try (Stream<Path> stream = Files.walk(root))
        {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            for (Path path : paths)
            {
                Files.deleteIfExists(path);
            }
        }
        catch (IOException e)
        {
            log.debug("Unable to remove temporary KSP compile directory {}", root, e);
        }
    }

    private String sourceArea(String className)
    {
        String marker = ".microbot.";
        int index = className.indexOf(marker);
        if (index < 0)
        {
            return "";
        }
        String remainder = className.substring(index + marker.length());
        int dot = remainder.indexOf('.');
        return dot < 0 ? "" : remainder.substring(0, dot);
    }

    private static Thread daemonThread(Runnable runnable, String name)
    {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static boolean isWindows()
    {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String simpleName(String className)
    {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    private static String sanitizeFilePart(String value)
    {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String safeString(String value)
    {
        return value == null ? "" : value;
    }

    static final class PluginIdentity
    {
        final String displayName;
        final String className;

        PluginIdentity(String displayName, String className)
        {
            this.displayName = displayName;
            this.className = className;
        }
    }

    static final class RuntimeFrame
    {
        final long timestampMs;
        final String timestamp;
        final String gameState;
        final int x;
        final int y;
        final int plane;
        final int animation;
        final String interacting;
        final String microbotStatus;
        final long inventoryHash;
        final long xpHash;
        final List<PluginIdentity> activeKspPlugins;
        final String fingerprint;

        RuntimeFrame(long timestampMs,
                     String gameState,
                     int x,
                     int y,
                     int plane,
                     int animation,
                     String interacting,
                     String microbotStatus,
                     long inventoryHash,
                     long xpHash,
                     List<PluginIdentity> activeKspPlugins,
                     String fingerprint)
        {
            this.timestampMs = timestampMs;
            this.timestamp = Instant.ofEpochMilli(timestampMs).toString();
            this.gameState = gameState;
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.animation = animation;
            this.interacting = interacting;
            this.microbotStatus = microbotStatus;
            this.inventoryHash = inventoryHash;
            this.xpHash = xpHash;
            this.activeKspPlugins = new ArrayList<>(activeKspPlugins);
            this.fingerprint = fingerprint;
        }
    }

    static final class Incident
    {
        String incidentId;
        String timestamp;
        String type;
        String reason;
        String triggerLogLine;
        String targetPluginName;
        String targetPluginClass;
        String microbotStatus;
        String repositoryUrl;
        String repositoryBranch;
        String lastSeenSourceRevision;
        String screenshot;
        RuntimeFrame runtimeFrame;
        List<RuntimeFrame> trace;
        List<String> recentLogs;
        Map<String, Object> pluginState;
    }

    static final class PendingRepair
    {
        String incidentId;
        String incidentType;
        String targetPluginClass;
        String beforeSha;
        String commitSha;
        String prePatchFingerprint;
        String repositoryPath;
        String sourceLoaderRevision;
        long pushedAtMs;
        long sourceLoaderSeenAtMs;
        boolean rollbackScheduled;
    }

    static final class CompilationResult
    {
        final boolean success;

        CompilationResult(boolean success)
        {
            this.success = success;
        }
    }

    static final class ProcessResult
    {
        final int exitCode;
        final String output;

        ProcessResult(int exitCode, String output)
        {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
