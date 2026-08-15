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
import net.runelite.api.events.MenuOptionClicked;
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
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * v0.2 reliability coordinator.
 *
 * Detection is event driven, augmented by state-machine reflection and optional KspObservation
 * intent markers. Repairs are staged, guarded, compiled, loaded, observed and either promoted or
 * automatically rolled back. KSP Source Loader remains a runtime dependency discovered by
 * reflection so an older loader cannot prevent this plugin from loading.
 */
final class KspAutoRepairCoordinator implements KspObservation.Sink, KspSourceLoaderBridge.Listener
{
    private static final Logger log = LoggerFactory.getLogger(KspAutoRepairCoordinator.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern REVISION_PATTERN =
            Pattern.compile("revision\\s+([0-9a-fA-F]{7,40})", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHA_PATTERN = Pattern.compile("\\b([0-9a-fA-F]{7,40})\\b");
    private static final int TRACE_LIMIT = 360;
    private static final int ACTION_LIMIT = 240;
    private static final int LOG_LIMIT = 500;
    private static final int STATE_HISTORY_LIMIT = 80;
    private static final int MAX_WIDGETS_IN_SNAPSHOT = 250;
    private static final int MAX_SCENE_CACHE = 600;
    private static final long MONITOR_PERIOD_SECONDS = 2L;
    private static final String REPAIR_ROOT_NAME = "ksp-agent-repair";

    enum Phase
    {
        IDLE,
        CAPTURING_INCIDENT,
        SYNCING_SOURCE,
        RUNNING_AGENT,
        VALIDATING_CHANGES,
        COMMITTING,
        PUSHING,
        WAITING_FOR_LOADER,
        VALIDATING_RUNTIME,
        ROLLING_BACK
    }

    enum FailureKind
    {
        CLIENT_THREAD,
        NULL_POINTER,
        API_CHANGED,
        WIDGET_TIMEOUT,
        PATHING,
        INTERACTION_TIMEOUT,
        DIALOGUE,
        BANKING,
        REPEATING_STATE,
        STATE_STALL,
        COMPILATION,
        SOURCE_LOADER,
        NETWORK,
        GIT_AUTH,
        AGENT_AUTH,
        AGENT_EXECUTABLE,
        ENVIRONMENT,
        UNKNOWN
    }

    private final Client client;
    private final PluginManager pluginManager;
    private final KspAutoRepairConfig config;
    private final KspSourceLoaderBridge sourceLoaderBridge;
    private final ScheduledExecutorService monitorExecutor;
    private final ScheduledExecutorService repairExecutor;

    private final Deque<RuntimeFrame> trace = new ArrayDeque<>();
    private final Deque<TimelineEvent> timeline = new ArrayDeque<>();
    private final Deque<ActionRecord> actions = new ArrayDeque<>();
    private final Deque<String> recentLogs = new ArrayDeque<>();
    private final Map<String, PluginRuntime> pluginRuntime = new HashMap<>();
    private final Map<String, SceneEntity> sceneCache = new LinkedHashMap<>();
    private final Map<String, Long> incidentCooldowns = new HashMap<>();
    private final AtomicReference<RuntimeFrame> lastFrame = new AtomicReference<>();
    private final AtomicBoolean repairInProgress = new AtomicBoolean(false);
    private final AtomicLong semanticProgress = new AtomicLong(0L);
    private final AtomicLong widgetGeneration = new AtomicLong(0L);
    private final AtomicLong dialogueGeneration = new AtomicLong(0L);
    private final AtomicLong varbitGeneration = new AtomicLong(0L);
    private final AtomicLong animationGeneration = new AtomicLong(0L);

    private volatile boolean running;
    private volatile long lastProgressAtMs;
    private volatile long logOffset;
    private volatile String lastSeenRevision = "";
    private volatile Phase phase = Phase.IDLE;
    private volatile long phaseStartedAtMs;
    private volatile String phaseDetail = "";
    private volatile Process activeAgentProcess;
    private volatile PendingRepair pendingRepair;
    private volatile boolean loaderRefreshRetried;
    private volatile int positionAnchorX = Integer.MIN_VALUE;
    private volatile int positionAnchorY = Integer.MIN_VALUE;

    KspAutoRepairCoordinator(Client client, PluginManager pluginManager, KspAutoRepairConfig config)
    {
        this.client = client;
        this.pluginManager = pluginManager;
        this.config = config;
        this.sourceLoaderBridge = new KspSourceLoaderBridge(pluginManager, this);
        this.monitorExecutor = Executors.newSingleThreadScheduledExecutor(
                runnable -> daemonThread(runnable, "ksp-auto-repair-v2-monitor"));
        this.repairExecutor = Executors.newSingleThreadScheduledExecutor(
                runnable -> daemonThread(runnable, "ksp-auto-repair-v2-worker"));
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
        sourceLoaderBridge.attach();
        KspObservation.install(this);

        monitorExecutor.scheduleWithFixedDelay(this::monitorSafely,
                MONITOR_PERIOD_SECONDS, MONITOR_PERIOD_SECONDS, TimeUnit.SECONDS);

        recordEvent("SYSTEM", null, "KSP Auto Repair v0.2 observer started",
                detail("loaderBridge", sourceLoaderBridge.hookDescription()), false);
        log.info("KSP Auto Repair v0.2 started | fullAuto={} confidence={} loaderBridge={}",
                config.fullAuto(), config.autoRepairConfidencePercent(), sourceLoaderBridge.hookDescription());
    }

    void stop()
    {
        running = false;
        KspObservation.uninstall(this);
        Process process = activeAgentProcess;
        if (process != null && process.isAlive())
        {
            process.destroyForcibly();
        }
        monitorExecutor.shutdownNow();
        repairExecutor.shutdownNow();
        log.info("KSP Auto Repair v0.2 stopped");
    }

    void onGameTick()
    {
        if (!running)
        {
            return;
        }

        RuntimeFrame frame = captureRuntimeFrame();
        RuntimeFrame previous = lastFrame.getAndSet(frame);
        appendTrace(frame);

        if (previous != null && (frame.x != previous.x || frame.y != previous.y))
        {
            if (positionAnchorX == Integer.MIN_VALUE)
            {
                positionAnchorX = previous.x;
                positionAnchorY = previous.y;
            }
            if (distance(positionAnchorX, positionAnchorY, frame.x, frame.y) >= 3)
            {
                positionAnchorX = frame.x;
                positionAnchorY = frame.y;
                markProgress("POSITION_PROGRESS", null, "Player moved at least three tiles");
            }
        }

        updatePluginStates(frame);
        evaluateActions(frame);
        evaluateStateHealth(frame);
        evaluateGlobalStall(frame);
    }

    void onGameStateChanged(Object event)
    {
        recordEvent("GAME_STATE", null, String.valueOf(event), Collections.emptyMap(), true);
    }

    void onItemContainerChanged(Object event)
    {
        recordEvent("ITEM_CONTAINER", null, String.valueOf(event), Collections.emptyMap(), true);
    }

    void onVarbitChanged(Object event)
    {
        varbitGeneration.incrementAndGet();
        recordEvent("VARBIT", null, String.valueOf(event), Collections.emptyMap(), true);
    }

    void onWidgetLoaded(Object event)
    {
        widgetGeneration.incrementAndGet();
        dialogueGeneration.incrementAndGet();
        recordEvent("WIDGET_LOADED", null, String.valueOf(event),
                reflectedProperties(event, "getGroupId"), true);
    }

    void onWidgetClosed(Object event)
    {
        widgetGeneration.incrementAndGet();
        dialogueGeneration.incrementAndGet();
        recordEvent("WIDGET_CLOSED", null, String.valueOf(event),
                reflectedProperties(event, "getGroupId", "getModalMode", "isUnload"), true);
    }

    void onAnimationChanged(Object event)
    {
        animationGeneration.incrementAndGet();
        Object actor = reflectValue(event, "getActor");
        boolean playerProgress = actor != null && actor == client.getLocalPlayer();
        recordEvent("ANIMATION", null, summarizeActorEvent(event), Collections.emptyMap(), playerProgress);
    }

    void onChatMessage(Object event)
    {
        String text = reflectedString(event, "getMessage");
        if (text.isEmpty())
        {
            text = String.valueOf(event);
        }
        if (looksLikeDialogue(text))
        {
            dialogueGeneration.incrementAndGet();
        }
        recordEvent("CHAT", null, truncate(text, 500),
                reflectedProperties(event, "getType", "getName"), false);
    }

    void onStatChanged(Object event)
    {
        recordEvent("STAT", null, String.valueOf(event),
                reflectedProperties(event, "getSkill", "getXp", "getBoostedLevel", "getLevel"), true);
    }

    void onNpcSpawned(Object event)
    {
        updateSceneEntity("NPC", event, true);
    }

    void onNpcDespawned(Object event)
    {
        updateSceneEntity("NPC", event, false);
    }

    void onGameObjectSpawned(Object event)
    {
        updateSceneEntity("OBJECT", event, true);
    }

    void onGameObjectDespawned(Object event)
    {
        updateSceneEntity("OBJECT", event, false);
    }

    void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!running || event == null)
        {
            return;
        }

        String option = safe(event.getMenuOption());
        String target = safe(event.getMenuTarget());
        Map<String, String> details = new LinkedHashMap<>();
        details.put("option", option);
        details.put("target", target);
        details.put("id", String.valueOf(event.getId()));
        details.put("action", String.valueOf(event.getMenuAction()));
        details.put("param0", String.valueOf(event.getParam0()));
        details.put("param1", String.valueOf(event.getParam1()));
        recordEvent("MENU_ACTION", null, option + " " + target, details, false);

        RuntimeFrame frame = lastFrame.get();
        if (frame == null || frame.activeKspPlugins.isEmpty() || isIntentionalStatus(frame.microbotStatus))
        {
            return;
        }

        PluginIdentity targetPlugin = chooseTarget(frame.activeKspPlugins, option + " " + target);
        if (targetPlugin == null)
        {
            return;
        }

        InferredExpectation inferred = inferExpectation(option, target);
        createAction(UUID.randomUUID().toString(), targetPlugin.className, option, target,
                "INFERRED_MENU", inferred.expectation, inferred.timeoutMs, Collections.emptyMap());
    }

    @Override
    public void onState(String pluginHint, String state, Map<String, String> metadata)
    {
        PluginIdentity plugin = resolvePluginHint(pluginHint);
        String className = plugin == null ? safe(pluginHint) : plugin.className;
        recordEvent("EXPLICIT_STATE", className, state, metadata, true);
        if (!className.isEmpty())
        {
            synchronized (pluginRuntime)
            {
                PluginRuntime runtime = pluginRuntime.computeIfAbsent(className, PluginRuntime::new);
                transitionState(runtime, "explicit:" + state, System.currentTimeMillis());
            }
        }
    }

    @Override
    public void onAction(String actionId, String pluginHint, String action, String target,
                         KspObservation.Expectation expectation, long timeoutMs,
                         Map<String, String> metadata)
    {
        PluginIdentity plugin = resolvePluginHint(pluginHint);
        String className = plugin == null ? safe(pluginHint) : plugin.className;
        if (className.isEmpty())
        {
            RuntimeFrame frame = lastFrame.get();
            PluginIdentity fallback = frame == null ? null
                    : chooseTarget(frame.activeKspPlugins, action + " " + target);
            className = fallback == null ? "" : fallback.className;
        }

        createAction(actionId, className, action, target, "EXPLICIT",
                expectation == null ? KspObservation.Expectation.ANY_PROGRESS : expectation,
                timeoutMs, metadata);
    }

    @Override
    public void onProgress(String pluginHint, String marker, Map<String, String> metadata)
    {
        PluginIdentity plugin = resolvePluginHint(pluginHint);
        String className = plugin == null ? safe(pluginHint) : plugin.className;
        recordEvent("EXPLICIT_PROGRESS", className, marker, metadata, true);
    }

    @Override
    public void onWaitReason(String pluginHint, String reason, long expectedUntilMs,
                             Map<String, String> metadata)
    {
        PluginIdentity plugin = resolvePluginHint(pluginHint);
        String className = plugin == null ? safe(pluginHint) : plugin.className;
        if (!className.isEmpty())
        {
            synchronized (pluginRuntime)
            {
                PluginRuntime runtime = pluginRuntime.computeIfAbsent(className, PluginRuntime::new);
                runtime.intentionalWaitUntilMs = expectedUntilMs;
                runtime.waitReason = reason;
            }
        }
        recordEvent("WAIT_REASON", className, reason, metadata, false);
    }

    @Override
    public void onLoaderEvent(String event, Map<String, String> details)
    {
        recordEvent("SOURCE_LOADER_" + event, null, event, details, false);
        extractRevision(details).ifPresent(this::observeRevision);
        String lower = (event + " " + String.valueOf(details)).toLowerCase(Locale.ROOT);
        PendingRepair pending = pendingRepair;
        if (pending != null && pending.loaderSeenAtMs > 0L
                && (lower.contains("fail") || lower.contains("reject") || lower.contains("error")))
        {
            scheduleRollback(pending, "Source Loader callback reported failure: " + event);
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
            sourceLoaderBridge.currentRevision().ifPresent(this::observeRevision);
            evaluatePendingRepair();
            evaluatePhaseWatchdog();
        }
        catch (Throwable t)
        {
            log.warn("KSP Auto Repair v0.2 monitor cycle failed", t);
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

        List<PluginIdentity> plugins = getActiveKspPlugins();
        long inventoryHash = gameState == GameState.LOGGED_IN ? containerHash(InventoryID.INV) : 0L;
        long equipmentHash = gameState == GameState.LOGGED_IN ? containerHash(InventoryID.WORN) : 0L;
        long xpHash = gameState == GameState.LOGGED_IN ? xpHash() : 0L;
        String status = safe(Microbot.status);
        Map<String, String> stateSignatures = inspectStateSignatures(plugins);

        String fingerprint = gameState.name() + '|' + x + '|' + y + '|' + plane + '|' + animation + '|'
                + interacting + '|' + inventoryHash + '|' + equipmentHash + '|' + xpHash + '|' + status + '|'
                + stateSignatures.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + '=' + entry.getValue()).collect(Collectors.joining(";"));

        return new RuntimeFrame(now, gameState.name(), x, y, plane, animation, interacting,
                status, inventoryHash, equipmentHash, xpHash, plugins, stateSignatures, fingerprint);
    }

    private long containerHash(int inventoryId)
    {
        ItemContainer container = client.getItemContainer(inventoryId);
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
            String displayName = descriptor == null ? plugin.getClass().getSimpleName() : descriptor.name();
            result.add(new PluginIdentity(displayName, plugin.getClass().getName()));
        }
        result.sort(Comparator.comparing(identity -> identity.className));
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
        if (plugin.getClass().getSimpleName().toLowerCase(Locale.ROOT).startsWith("ksp"))
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

    private Map<String, String> inspectStateSignatures(List<PluginIdentity> plugins)
    {
        if (plugins.isEmpty())
        {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, Plugin> instances = new HashMap<>();
        for (Plugin plugin : pluginManager.getPlugins())
        {
            if (plugin != null)
            {
                instances.put(plugin.getClass().getName(), plugin);
            }
        }
        for (PluginIdentity identity : plugins)
        {
            Plugin plugin = instances.get(identity.className);
            if (plugin != null)
            {
                result.put(identity.className, stateSignature(plugin));
            }
        }
        return result;
    }

    private String stateSignature(Plugin plugin)
    {
        Map<String, String> values = new LinkedHashMap<>();
        inspectStateFields(plugin, "plugin", values, false);
        for (Field field : allFields(plugin.getClass()))
        {
            String name = field.getName().toLowerCase(Locale.ROOT);
            if (!name.contains("script"))
            {
                continue;
            }
            try
            {
                field.setAccessible(true);
                Object script = field.get(plugin);
                if (script != null)
                {
                    inspectStateFields(script, "script." + field.getName(), values, true);
                }
            }
            catch (Throwable ignored)
            {
            }
        }
        if (values.isEmpty())
        {
            return safe(Microbot.status);
        }
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(40)
                .map(entry -> entry.getKey() + '=' + entry.getValue()).collect(Collectors.joining("|"));
    }

    private void inspectStateFields(Object object, String prefix, Map<String, String> destination,
                                    boolean includeLikelyControlFields)
    {
        for (Field field : allFields(object.getClass()))
        {
            if (Modifier.isStatic(field.getModifiers()))
            {
                continue;
            }
            String name = field.getName().toLowerCase(Locale.ROOT);
            boolean stateName = name.contains("state") || name.contains("phase") || name.contains("step")
                    || name.contains("stage") || name.contains("task") || name.contains("mode");
            if (!stateName && !(includeLikelyControlFields
                    && (name.contains("target") || name.contains("retry") || name.contains("attempt"))))
            {
                continue;
            }
            try
            {
                field.setAccessible(true);
                Object value = field.get(object);
                if (isSafeScalar(value))
                {
                    destination.put(prefix + "." + field.getName(), value == null ? "null" : String.valueOf(value));
                }
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    private void updatePluginStates(RuntimeFrame frame)
    {
        long now = frame.timestampMs;
        synchronized (pluginRuntime)
        {
            Set<String> active = new HashSet<>();
            for (PluginIdentity identity : frame.activeKspPlugins)
            {
                active.add(identity.className);
                PluginRuntime runtime = pluginRuntime.computeIfAbsent(identity.className, PluginRuntime::new);
                String signature = safe(frame.stateSignatures.get(identity.className));
                runtime.lastTouchedAtMs = now;
                if (!signature.equals(runtime.currentStateSignature))
                {
                    String previous = runtime.currentStateSignature;
                    transitionState(runtime, signature, now);
                    recordEvent("STATE_TRANSITION", identity.className, signature,
                            detail("previous", previous), true);
                }
                else
                {
                    runtime.ticksInState++;
                }
            }
            pluginRuntime.keySet().retainAll(active);
        }
    }

    private void transitionState(PluginRuntime runtime, String signature, long now)
    {
        runtime.previousStateSignature = runtime.currentStateSignature;
        runtime.currentStateSignature = safe(signature);
        runtime.stateEnteredAtMs = now;
        runtime.progressAtStateEntry = semanticProgress.get();
        runtime.ticksInState = 0;
        if (!runtime.currentStateSignature.isEmpty())
        {
            runtime.stateHistory.addLast(runtime.currentStateSignature);
            while (runtime.stateHistory.size() > STATE_HISTORY_LIMIT)
            {
                runtime.stateHistory.removeFirst();
            }
        }
    }

    private void evaluateStateHealth(RuntimeFrame frame)
    {
        if (!GameState.LOGGED_IN.name().equals(frame.gameState)
                || frame.activeKspPlugins.isEmpty() || repairInProgress.get())
        {
            return;
        }
        List<PluginRuntime> runtimes;
        synchronized (pluginRuntime)
        {
            runtimes = new ArrayList<>(pluginRuntime.values());
        }
        for (PluginRuntime runtime : runtimes)
        {
            if (isIntentionalWait(runtime, frame.microbotStatus))
            {
                continue;
            }
            String loop = detectLoop(runtime.stateHistory, config.loopRepetitions());
            if (loop != null && !isCoolingDown(runtime.pluginClass))
            {
                PluginIdentity target = findIdentity(frame.activeKspPlugins, runtime.pluginClass);
                if (target != null)
                {
                    createIncidentRequest("STATE_LOOP", FailureKind.REPEATING_STATE,
                            "Repeated state sequence detected: " + loop, target, 0.92,
                            null, runtime.currentStateSignature);
                    return;
                }
            }
            long stateDuration = frame.timestampMs - runtime.stateEnteredAtMs;
            boolean noProgressSinceEntry = semanticProgress.get() <= runtime.progressAtStateEntry;
            if (!runtime.currentStateSignature.isEmpty()
                    && stateDuration >= TimeUnit.SECONDS.toMillis(config.stateStallSeconds())
                    && noProgressSinceEntry && !isCoolingDown(runtime.pluginClass))
            {
                PluginIdentity target = findIdentity(frame.activeKspPlugins, runtime.pluginClass);
                if (target != null)
                {
                    createIncidentRequest("STATE_STALL", FailureKind.STATE_STALL,
                            "Plugin state has not changed or produced semantic progress for "
                                    + TimeUnit.MILLISECONDS.toSeconds(stateDuration) + " seconds",
                            target, 0.88, null, runtime.currentStateSignature);
                    return;
                }
            }
        }
    }

    private String detectLoop(Deque<String> history, int repetitions)
    {
        if (repetitions < 2)
        {
            return null;
        }
        List<String> states = new ArrayList<>(history);
        for (int period = 2; period <= 6; period++)
        {
            int needed = period * repetitions;
            if (states.size() < needed)
            {
                continue;
            }
            int start = states.size() - needed;
            boolean matches = true;
            for (int offset = period; offset < needed && matches; offset += period)
            {
                for (int i = 0; i < period; i++)
                {
                    if (!states.get(start + i).equals(states.get(start + offset + i)))
                    {
                        matches = false;
                        break;
                    }
                }
            }
            if (matches)
            {
                List<String> periodStates = states.subList(start, start + period);
                if (new HashSet<>(periodStates).size() > 1)
                {
                    return periodStates.stream().map(this::shortState).collect(Collectors.joining(" -> "))
                            + " x" + repetitions;
                }
            }
        }
        return null;
    }

    private String shortState(String signature)
    {
        return truncate(signature, 120);
    }

    private void evaluateGlobalStall(RuntimeFrame frame)
    {
        if (!GameState.LOGGED_IN.name().equals(frame.gameState) || frame.activeKspPlugins.isEmpty()
                || repairInProgress.get() || pendingRepair != null)
        {
            return;
        }
        if (isIntentionalStatus(frame.microbotStatus))
        {
            lastProgressAtMs = System.currentTimeMillis();
            return;
        }
        long stagnantFor = System.currentTimeMillis() - lastProgressAtMs;
        if (stagnantFor < TimeUnit.SECONDS.toMillis(config.stallSeconds()))
        {
            return;
        }
        PluginIdentity target = chooseTarget(frame.activeKspPlugins, null);
        if (target == null || isCoolingDown(target.className))
        {
            return;
        }
        lastProgressAtMs = System.currentTimeMillis();
        createIncidentRequest("GLOBAL_STALL", FailureKind.STATE_STALL,
                "No semantic progress was observed for " + TimeUnit.MILLISECONDS.toSeconds(stagnantFor) + " seconds",
                target, 0.76, null, frame.stateSignatures.get(target.className));
    }

    private boolean isIntentionalWait(PluginRuntime runtime, String status)
    {
        return (runtime != null && runtime.intentionalWaitUntilMs > System.currentTimeMillis())
                || isIntentionalStatus(status);
    }

    private boolean isIntentionalStatus(String status)
    {
        String value = safe(status).toLowerCase(Locale.ROOT);
        return value.equals("idle") || value.contains("break") || value.contains("logout")
                || value.contains("waiting for login") || value.contains("world hop")
                || value.contains("hopping world") || value.contains("cooldown");
    }

    private void createAction(String id, String pluginClass, String action, String target,
                              String source, KspObservation.Expectation expectation, long timeoutMs,
                              Map<String, String> metadata)
    {
        RuntimeFrame frame = lastFrame.get();
        if (frame == null)
        {
            return;
        }
        ActionRecord record = new ActionRecord();
        record.id = safe(id).isEmpty() ? UUID.randomUUID().toString() : id;
        record.pluginClass = safe(pluginClass);
        record.action = safe(action);
        record.target = safe(target);
        record.source = safe(source);
        record.expectation = expectation == null ? KspObservation.Expectation.ANY_PROGRESS : expectation;
        record.startedAtMs = System.currentTimeMillis();
        record.deadlineMs = record.startedAtMs + Math.max(250L, timeoutMs);
        record.baselineProgress = semanticProgress.get();
        record.baselineX = frame.x;
        record.baselineY = frame.y;
        record.baselineAnimation = frame.animation;
        record.baselineInventoryHash = frame.inventoryHash;
        record.baselineEquipmentHash = frame.equipmentHash;
        record.baselineXpHash = frame.xpHash;
        record.baselineWidgetGeneration = widgetGeneration.get();
        record.baselineDialogueGeneration = dialogueGeneration.get();
        record.baselineVarbitGeneration = varbitGeneration.get();
        record.baselineAnimationGeneration = animationGeneration.get();
        record.baselineStateSignature = safe(frame.stateSignatures.get(record.pluginClass));
        record.metadata = metadata == null ? Collections.emptyMap() : new LinkedHashMap<>(metadata);
        record.result = "PENDING";
        synchronized (actions)
        {
            actions.addLast(record);
            while (actions.size() > ACTION_LIMIT)
            {
                actions.removeFirst();
            }
        }
        Map<String, String> details = new LinkedHashMap<>(record.metadata);
        details.put("id", record.id);
        details.put("source", record.source);
        details.put("expectation", record.expectation.name());
        details.put("timeoutMs", String.valueOf(timeoutMs));
        recordEvent("ACTION_BEGIN", record.pluginClass,
                record.action + " -> " + record.target, details, false);
    }

    private void evaluateActions(RuntimeFrame frame)
    {
        List<ActionRecord> snapshot;
        synchronized (actions)
        {
            snapshot = new ArrayList<>(actions);
        }
        long now = System.currentTimeMillis();
        for (ActionRecord action : snapshot)
        {
            if (!"PENDING".equals(action.result))
            {
                continue;
            }
            if (postconditionSatisfied(action, frame))
            {
                action.result = "SUCCESS";
                action.completedAtMs = now;
                action.failureReason = "";
                markProgress("ACTION_POSTCONDITION", action.pluginClass,
                        action.action + " fulfilled " + action.expectation);
                recordEvent("ACTION_SUCCESS", action.pluginClass,
                        action.action + " -> " + action.target,
                        detail("expectation", action.expectation.name()), false);
                continue;
            }
            if (now < action.deadlineMs)
            {
                continue;
            }
            action.result = "FAILED";
            action.completedAtMs = now;
            action.failureReason = "Expected " + action.expectation + " before timeout";
            recordEvent("ACTION_TIMEOUT", action.pluginClass,
                    action.action + " -> " + action.target,
                    detail("expectation", action.expectation.name()), false);
            PluginIdentity target = resolvePluginHint(action.pluginClass);
            if (target == null || isCoolingDown(target.className))
            {
                continue;
            }
            FailureKind kind = classifyAction(action);
            double confidence = "EXPLICIT".equals(action.source) ? 0.96 : inferredActionConfidence(action);
            createIncidentRequest("FAILED_POSTCONDITION", kind,
                    action.failureReason + " for action " + action.action + " target=" + action.target,
                    target, confidence, action, action.baselineStateSignature);
            return;
        }
    }

    private boolean postconditionSatisfied(ActionRecord action, RuntimeFrame frame)
    {
        switch (action.expectation)
        {
            case POSITION_CHANGE:
                return frame.x != action.baselineX || frame.y != action.baselineY;
            case INVENTORY_CHANGE:
                return frame.inventoryHash != action.baselineInventoryHash
                        || frame.equipmentHash != action.baselineEquipmentHash;
            case WIDGET_CHANGE:
                return widgetGeneration.get() > action.baselineWidgetGeneration;
            case DIALOGUE_CHANGE:
                return dialogueGeneration.get() > action.baselineDialogueGeneration;
            case ANIMATION_CHANGE:
                return animationGeneration.get() > action.baselineAnimationGeneration
                        || frame.animation != action.baselineAnimation;
            case STATE_CHANGE:
                return !safe(frame.stateSignatures.get(action.pluginClass)).equals(action.baselineStateSignature);
            case VARBIT_CHANGE:
                return varbitGeneration.get() > action.baselineVarbitGeneration;
            case XP_CHANGE:
                return frame.xpHash != action.baselineXpHash;
            case ANY_PROGRESS:
            default:
                return semanticProgress.get() > action.baselineProgress
                        || widgetGeneration.get() > action.baselineWidgetGeneration
                        || varbitGeneration.get() > action.baselineVarbitGeneration
                        || animationGeneration.get() > action.baselineAnimationGeneration;
        }
    }

    private FailureKind classifyAction(ActionRecord action)
    {
        String text = (action.action + " " + action.target).toLowerCase(Locale.ROOT);
        if (text.contains("bank") || text.contains("withdraw") || text.contains("deposit"))
        {
            return FailureKind.BANKING;
        }
        if (text.contains("talk") || text.contains("dialog") || text.contains("continue"))
        {
            return FailureKind.DIALOGUE;
        }
        if (text.contains("walk") || text.contains("travel"))
        {
            return FailureKind.PATHING;
        }
        if (action.expectation == KspObservation.Expectation.WIDGET_CHANGE)
        {
            return FailureKind.WIDGET_TIMEOUT;
        }
        return FailureKind.INTERACTION_TIMEOUT;
    }

    private double inferredActionConfidence(ActionRecord action)
    {
        double confidence = 0.82;
        PluginRuntime runtime;
        synchronized (pluginRuntime)
        {
            runtime = pluginRuntime.get(action.pluginClass);
        }
        if (runtime != null && System.currentTimeMillis() - runtime.stateEnteredAtMs
                >= TimeUnit.SECONDS.toMillis(Math.min(20, config.stateStallSeconds())))
        {
            confidence += 0.05;
        }
        int failedSimilar = 0;
        synchronized (actions)
        {
            for (ActionRecord candidate : actions)
            {
                if (candidate == action || !"FAILED".equals(candidate.result)
                        || !candidate.pluginClass.equals(action.pluginClass))
                {
                    continue;
                }
                if (candidate.action.equalsIgnoreCase(action.action)
                        && candidate.target.equalsIgnoreCase(action.target))
                {
                    failedSimilar++;
                }
            }
        }
        confidence += Math.min(0.08, failedSimilar * 0.03);
        return Math.min(0.92, confidence);
    }

    private InferredExpectation inferExpectation(String option, String target)
    {
        String text = (safe(option) + " " + safe(target)).toLowerCase(Locale.ROOT);
        if (text.contains("walk here") || text.startsWith("walk"))
        {
            return new InferredExpectation(KspObservation.Expectation.POSITION_CHANGE, 15_000L);
        }
        if (text.contains("bank"))
        {
            return new InferredExpectation(KspObservation.Expectation.WIDGET_CHANGE, 7_000L);
        }
        if (text.contains("talk-to") || text.contains("talk to"))
        {
            return new InferredExpectation(KspObservation.Expectation.DIALOGUE_CHANGE, 8_000L);
        }
        if (text.contains("withdraw") || text.contains("deposit"))
        {
            return new InferredExpectation(KspObservation.Expectation.INVENTORY_CHANGE, 5_000L);
        }
        if (text.contains("teleport"))
        {
            return new InferredExpectation(KspObservation.Expectation.POSITION_CHANGE, 12_000L);
        }
        if (text.contains("wield") || text.contains("wear") || text.contains("equip")
                || text.contains("eat") || text.contains("drink"))
        {
            return new InferredExpectation(KspObservation.Expectation.INVENTORY_CHANGE, 5_000L);
        }
        if (text.contains("continue") || text.contains("select") || text.contains("choose"))
        {
            return new InferredExpectation(KspObservation.Expectation.DIALOGUE_CHANGE, 5_000L);
        }
        return new InferredExpectation(KspObservation.Expectation.ANY_PROGRESS, 6_000L);
    }

    private void createIncidentRequest(String type, FailureKind kind, String reason,
                                       PluginIdentity target, double confidence,
                                       ActionRecord action, String stateSignature)
    {
        if (!running || target == null)
        {
            return;
        }
        PendingRepair pending = pendingRepair;
        if (pending != null && pending.targetPluginClass.equals(target.className)
                && pending.loaderSeenAtMs > 0L)
        {
            if (config.autoRollback())
            {
                scheduleRollback(pending,
                        "Original/similar failure recurred during post-fix validation: " + type + " " + reason);
            }
            return;
        }
        if (!repairInProgress.compareAndSet(false, true))
        {
            return;
        }
        incidentCooldowns.put(target.className, System.currentTimeMillis());
        final double boundedConfidence = Math.max(0D, Math.min(1D, confidence));
        repairExecutor.execute(() -> {
            try
            {
                handleIncident(type, kind, reason, target, boundedConfidence, action, stateSignature);
            }
            catch (Throwable t)
            {
                log.error("KSP Auto Repair v0.2 incident handling failed for {}", target.className, t);
            }
            finally
            {
                if (pendingRepair == null)
                {
                    setPhase(Phase.IDLE, "");
                }
                repairInProgress.set(false);
            }
        });
    }

    private void handleIncident(String type, FailureKind kind, String reason,
                                PluginIdentity target, double confidence,
                                ActionRecord action, String stateSignature) throws Exception
    {
        setPhase(Phase.CAPTURING_INCIDENT, target.className);
        Incident incident = createIncident(type, kind, reason, target, confidence, action, stateSignature);
        Path incidentDir = getIncidentRoot().resolve(incident.incidentId);
        Files.createDirectories(incidentDir);
        incident.environment = captureEnvironment();
        incident.pluginState = inspectPluginState(target.className);
        incident.lastSeenSourceRevision = lastSeenRevision;
        incident.sourceLoaderBridge = sourceLoaderBridge.hookDescription();
        if (config.captureScreenshots())
        {
            Path screenshot = captureScreenshot(incidentDir.resolve("screenshot.png"));
            incident.screenshot = screenshot == null ? null : screenshot.toAbsolutePath().toString();
        }
        List<RepairHistoryRecord> matches = findSimilarRepairHistory(incident, 3);
        incident.similarRepairs = matches;

        Path incidentJson = incidentDir.resolve("incident.json");
        writeJsonAtomic(incidentJson, incident);
        writeJsonLines(incidentDir.resolve("events.jsonl"), snapshotTimeline());
        writeJsonLines(incidentDir.resolve("actions.jsonl"), snapshotActions());
        writeJsonLines(incidentDir.resolve("states.jsonl"), snapshotPluginRuntime());
        writeJsonAtomic(incidentDir.resolve("environment.json"), incident.environment);
        writeJsonAtomic(incidentDir.resolve("repair-history-match.json"), matches);
        writeLines(incidentDir.resolve("client-tail.log"), snapshotLogs());
        Files.write(incidentDir.resolve("source-revision.txt"), Collections.singletonList(safe(lastSeenRevision)),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        log.warn("KSP Auto Repair v0.2 incident {} | kind={} confidence={} plugin={} reason={}",
                incident.incidentId, kind, String.format(Locale.ROOT, "%.2f", confidence),
                target.className, reason);

        if (!isRepairable(kind))
        {
            writeStatus(incidentDir, "INFRASTRUCTURE_OR_ENVIRONMENT",
                    "Incident classified as " + kind + "; source repair was intentionally not attempted");
            appendRepairHistory(historyFor(incident, null, null, "NOT_REPAIRABLE", ""));
            return;
        }
        double threshold = config.autoRepairConfidencePercent() / 100.0;
        if (!config.fullAuto() || config.agentBackend() == KspAgentBackend.DISABLED || confidence < threshold)
        {
            writeStatus(incidentDir, "CAPTURED_NOT_AUTO_REPAIRED",
                    "Confidence " + confidence + " is below threshold " + threshold
                            + " or full-auto/agent backend is disabled");
            appendRepairHistory(historyFor(incident, null, null, "CAPTURED", ""));
            return;
        }

        setPhase(Phase.SYNCING_SOURCE, target.className);
        Path repository = managedRepositoryPath();
        syncManagedRepository(repository, incidentDir);
        String sourceArea = sourceArea(target.className);
        if (sourceArea.isEmpty() || !Files.isDirectory(repository.resolve(sourceArea)))
        {
            writeStatus(incidentDir, "UNMAPPED_SOURCE",
                    "No target source directory '" + sourceArea + "' exists in the configured repository");
            appendRepairHistory(historyFor(incident, null, null, "UNMAPPED_SOURCE", ""));
            return;
        }

        String beforeSha = captureProcess(Arrays.asList("git", "rev-parse", "HEAD"),
                repository, Duration.ofSeconds(30)).trim();
        Path compileReport = incidentDir.resolve("compile-validation.txt");
        boolean compiled = false;
        String agentSummary = "";

        for (int attempt = 1; attempt <= config.maxAgentAttempts(); attempt++)
        {
            resetWorkingTree(repository, incidentDir);
            Path promptFile = incidentDir.resolve("agent-prompt-" + attempt + ".txt");
            String prompt = buildAgentPrompt(incident, incidentJson, incidentDir,
                    repository, sourceArea, attempt, compileReport, matches);
            Files.write(promptFile, prompt.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            setPhase(Phase.RUNNING_AGENT, "attempt " + attempt + " " + target.className);
            Path output = incidentDir.resolve("agent-output-" + attempt + ".log");
            ProcessResult agentResult = invokeAgent(prompt, repository, output);
            agentSummary = readSummary(output);
            if (agentResult.exitCode != 0)
            {
                FailureKind agentFailure = classifyInfrastructureText(agentSummary);
                writeStatus(incidentDir, "AGENT_ATTEMPT_FAILED",
                        "Attempt " + attempt + " exit=" + agentResult.exitCode
                                + " classification=" + agentFailure);
                if (agentFailure == FailureKind.AGENT_AUTH || agentFailure == FailureKind.AGENT_EXECUTABLE)
                {
                    appendRepairHistory(historyFor(incident, beforeSha, null,
                            "AGENT_INFRASTRUCTURE_FAILED", agentSummary));
                    return;
                }
                continue;
            }

            List<String> changedFiles = changedFiles(repository);
            if (changedFiles.isEmpty())
            {
                writeStatus(incidentDir, "NO_CHANGE",
                        "Agent attempt " + attempt + " completed without changing source");
                continue;
            }
            String violation = validateChangedPaths(changedFiles, sourceArea);
            if (violation != null)
            {
                resetWorkingTree(repository, incidentDir);
                writeStatus(incidentDir, "GUARDRAIL_REJECTED", violation);
                appendRepairHistory(historyFor(incident, beforeSha, null,
                        "GUARDRAIL_REJECTED", violation));
                return;
            }
            ProcessResult diffCheck = runProcess(Arrays.asList("git", "diff", "--check"),
                    repository, incidentDir.resolve("diff-check.txt"), Duration.ofSeconds(30), false);
            if (diffCheck.exitCode != 0)
            {
                writeStatus(incidentDir, "DIFF_CHECK_RETRY",
                        "git diff --check rejected attempt " + attempt);
                continue;
            }

            setPhase(Phase.VALIDATING_CHANGES, "attempt " + attempt);
            CompilationResult result = compileCandidate(repository, compileReport);
            if (result.success)
            {
                compiled = true;
                break;
            }
            writeStatus(incidentDir, "COMPILE_RETRY",
                    "Attempt " + attempt + " failed candidate validation; diagnostics will be supplied to the next attempt");
        }

        if (!compiled)
        {
            resetWorkingTree(repository, incidentDir);
            writeStatus(incidentDir, "COMPILE_FAILED",
                    "No agent attempt produced a candidate that passed compilation/preflight");
            appendRepairHistory(historyFor(incident, beforeSha, null, "COMPILE_FAILED", agentSummary));
            return;
        }

        List<String> finalChanges = changedFiles(repository);
        Files.write(incidentDir.resolve("changed-files.txt"), finalChanges, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        if (!config.autoPush())
        {
            writeStatus(incidentDir, "VALIDATED_NOT_PUSHED",
                    "Candidate passed staging validation; automatic push is disabled");
            appendRepairHistory(historyFor(incident, beforeSha, null, "VALIDATED_NOT_PUSHED", agentSummary));
            return;
        }

        setPhase(Phase.COMMITTING, target.className);
        runChecked(Arrays.asList("git", "add", "-A"), repository,
                Duration.ofSeconds(30), incidentDir.resolve("git.log"));
        runChecked(Arrays.asList("git", "commit", "-m",
                        "agent: repair " + simpleName(target.className) + " [" + incident.incidentId + "]"),
                repository, Duration.ofSeconds(60), incidentDir.resolve("git.log"));
        String repairSha = captureProcess(Arrays.asList("git", "rev-parse", "HEAD"),
                repository, Duration.ofSeconds(30)).trim();

        setPhase(Phase.PUSHING, repairSha);
        runChecked(Arrays.asList("git", "push", "origin", "HEAD:" + config.repositoryBranch()),
                repository, Duration.ofMinutes(2), incidentDir.resolve("git.log"));

        PendingRepair pending = new PendingRepair();
        pending.incidentId = incident.incidentId;
        pending.incidentType = type;
        pending.failureKind = kind.name();
        pending.targetPluginClass = target.className;
        pending.stateSignature = stateSignature;
        pending.beforeSha = beforeSha;
        pending.commitSha = repairSha;
        pending.pushedAtMs = System.currentTimeMillis();
        pending.repositoryPath = repository.toAbsolutePath().toString();
        pending.agentSummary = agentSummary;
        pendingRepair = pending;
        writePendingRepair(pending);

        setPhase(Phase.WAITING_FOR_LOADER, repairSha);
        loaderRefreshRetried = false;
        if (config.requestLoaderRefresh())
        {
            sourceLoaderBridge.requestRefresh();
        }
        writeStatus(incidentDir, "PUSHED_WAITING_FOR_LOADER",
                "Staged candidate passed validation and was pushed as " + repairSha);
    }

    private Incident createIncident(String type, FailureKind kind, String reason,
                                    PluginIdentity target, double confidence,
                                    ActionRecord action, String stateSignature)
    {
        Incident incident = new Incident();
        incident.incidentId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC)
                .format(Instant.now()) + "-" + sanitizeFilePart(simpleName(target.className));
        incident.timestamp = Instant.now().toString();
        incident.type = type;
        incident.failureKind = kind.name();
        incident.reason = reason;
        incident.confidence = confidence;
        incident.autoRepairThreshold = config.autoRepairConfidencePercent() / 100.0;
        incident.targetPluginName = target.displayName;
        incident.targetPluginClass = target.className;
        incident.stateSignature = safe(stateSignature);
        incident.microbotStatus = safe(Microbot.status);
        incident.repositoryUrl = config.repositoryUrl();
        incident.repositoryBranch = config.repositoryBranch();
        incident.runtimeFrame = lastFrame.get();
        incident.trace = snapshotTrace();
        incident.recentLogs = snapshotLogs();
        incident.failedAction = action;
        return incident;
    }

    private boolean isRepairable(FailureKind kind)
    {
        switch (kind)
        {
            case NETWORK:
            case GIT_AUTH:
            case AGENT_AUTH:
            case AGENT_EXECUTABLE:
            case ENVIRONMENT:
                return false;
            default:
                return true;
        }
    }

    private CompilationResult compileCandidate(Path repository, Path report) throws IOException
    {
        List<String> reportLines = new ArrayList<>();
        if (config.preferSourceLoaderCompiler())
        {
            KspSourceLoaderBridge.PreflightResult preflight = sourceLoaderBridge.preflight(repository);
            reportLines.add("sourceLoaderPreflight.available=" + preflight.available);
            reportLines.add("sourceLoaderPreflight.mechanism=" + preflight.mechanism);
            reportLines.add("sourceLoaderPreflight.success=" + preflight.success);
            reportLines.add("sourceLoaderPreflight.details=" + preflight.details);
            if (preflight.available)
            {
                Files.write(report, reportLines, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return new CompilationResult(preflight.success, preflight.mechanism);
            }
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
        {
            reportLines.add("fallback.success=false");
            reportLines.add("fallback.error=No system Java compiler is available in this JVM");
            Files.write(report, reportLines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return new CompilationResult(false, "system-javac");
        }

        List<File> javaFiles;
        try (Stream<Path> stream = Files.walk(repository))
        {
            javaFiles = stream.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains(File.separator + ".git" + File.separator))
                    .map(Path::toFile).collect(Collectors.toList());
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path outputDir = Files.createTempDirectory(getRepairRoot(), "javac-v2-");
        boolean success = false;
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8))
        {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(javaFiles);
            List<String> options = Arrays.asList("-proc:none", "-classpath",
                    System.getProperty("java.class.path", ""), "-d", outputDir.toAbsolutePath().toString());
            Boolean result = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            success = Boolean.TRUE.equals(result);
        }
        finally
        {
            deleteRecursively(outputDir);
        }
        reportLines.add("fallback.compiler=system-javac");
        reportLines.add("fallback.success=" + success);
        reportLines.add("fallback.sources=" + javaFiles.size());
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics())
        {
            String source = diagnostic.getSource() == null ? "<unknown>" : diagnostic.getSource().getName();
            reportLines.add(diagnostic.getKind() + " " + source + ":" + diagnostic.getLineNumber()
                    + ":" + diagnostic.getColumnNumber() + " " + diagnostic.getMessage(Locale.ROOT));
        }
        Files.write(report, reportLines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return new CompilationResult(success, "system-javac");
    }

    private String buildAgentPrompt(Incident incident, Path incidentJson, Path incidentDir,
                                    Path repository, String sourceArea, int attempt,
                                    Path compileReport, List<RepairHistoryRecord> matches)
    {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the autonomous KSP Microbot source repair worker.\\n");
        prompt.append("Repository root: ").append(repository.toAbsolutePath()).append('\n');
        prompt.append("Incident JSON: ").append(incidentJson.toAbsolutePath()).append('\n');
        prompt.append("Replay evidence directory: ").append(incidentDir.toAbsolutePath()).append('\n');
        prompt.append("Target plugin: ").append(incident.targetPluginClass).append('\n');
        prompt.append("Failure classification: ").append(incident.failureKind).append('\n');
        prompt.append("Deterministic confidence: ").append(incident.confidence).append('\n');
        prompt.append("Allowed source directory: ").append(sourceArea).append("/\\n");
        prompt.append("Attempt: ").append(attempt).append('\n');
        if (attempt > 1 && Files.isRegularFile(compileReport))
        {
            prompt.append("The previous candidate failed validation. Read: ")
                    .append(compileReport.toAbsolutePath()).append('\n');
        }
        if (matches != null && !matches.isEmpty())
        {
            prompt.append("Similar historical repairs and outcomes are in repair-history-match.json. ")
                    .append("Use successful outcomes as evidence and do not repeat rolled-back fixes.\\n");
        }
        prompt.append("\\nRead incident.json, events.jsonl, actions.jsonl, states.jsonl, environment.json, ");
        prompt.append("client-tail.log and screenshot.png when present. Reconstruct intent -> action -> postcondition ");
        prompt.append("before changing code. Prefer the smallest robust fix supported by evidence.\\n\\nHARD RULES:\\n");
        prompt.append("1. Modify only .java files under ").append(sourceArea).append("/.\\n");
        prompt.append("2. Do not modify KSP Source Loader, KSP Auto Repair, build infrastructure, credentials or other plugins.\\n");
        prompt.append("3. Do not run git commit, git push, git reset, git checkout or git clean. The bridge owns source control.\\n");
        prompt.append("4. Do not hide, suppress or disable failing behavior merely to make the incident disappear.\\n");
        prompt.append("5. Do not invent Microbot APIs. Reuse APIs supported by repository code/runtime evidence.\\n");
        prompt.append("6. Preserve intentional waits and prefer state/postcondition checks over arbitrary fixed sleeps.\\n");
        prompt.append("7. Finish after editing source. The bridge performs compile/load/runtime validation.\\n");
        return prompt.toString();
    }

    private ProcessResult invokeAgent(String prompt, Path repository, Path output) throws Exception
    {
        KspAgentBackend backend = config.agentBackend();
        if (backend == KspAgentBackend.CLAUDE_CODE)
        {
            List<String> command = Arrays.asList(config.agentExecutable(), "-p", prompt,
                    "--allowedTools", "Read,Edit,Write,Glob,Grep", "--model", config.claudeModel(),
                    "--output-format", "json");
            try
            {
                return runAgentProcess(command, repository, output,
                        Duration.ofMinutes(config.agentTimeoutMinutes()));
            }
            catch (IOException directFailure)
            {
                if (!isWindows())
                {
                    throw directFailure;
                }
                ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                        "-Command", "& $env:KSP_AGENT_EXE -p $env:KSP_AGENT_PROMPT "
                        + "--allowedTools 'Read,Edit,Write,Glob,Grep' --model $env:KSP_AGENT_MODEL --output-format json");
                builder.directory(repository.toFile());
                builder.environment().put("KSP_AGENT_EXE", config.agentExecutable());
                builder.environment().put("KSP_AGENT_PROMPT", prompt);
                builder.environment().put("KSP_AGENT_MODEL", config.claudeModel());
                return runAgentProcess(builder, output, Duration.ofMinutes(config.agentTimeoutMinutes()));
            }
        }
        if (backend == KspAgentBackend.CUSTOM)
        {
            String template = safe(config.customAgentCommand()).trim();
            if (template.isEmpty())
            {
                throw new IllegalStateException("Custom agent backend selected but no command is configured");
            }
            Path promptFile = output.getParent().resolve("custom-agent-prompt.txt");
            Files.write(promptFile, prompt.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            String command = template.replace("{repo}", repository.toAbsolutePath().toString())
                    .replace("{prompt}", promptFile.toAbsolutePath().toString());
            List<String> shell = isWindows()
                    ? Arrays.asList("cmd.exe", "/d", "/s", "/c", command)
                    : Arrays.asList("/bin/sh", "-lc", command);
            return runAgentProcess(shell, repository, output, Duration.ofMinutes(config.agentTimeoutMinutes()));
        }
        throw new IllegalStateException("Agent backend is disabled");
    }

    private ProcessResult runAgentProcess(List<String> command, Path workingDirectory,
                                          Path output, Duration timeout) throws Exception
    {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        return runAgentProcess(builder, output, timeout);
    }

    private ProcessResult runAgentProcess(ProcessBuilder builder, Path output, Duration timeout) throws Exception
    {
        Files.createDirectories(output.getParent());
        builder.redirectErrorStream(true);
        builder.redirectOutput(output.toFile());
        Process process = builder.start();
        activeAgentProcess = process;
        try
        {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished)
            {
                process.destroyForcibly();
                return new ProcessResult(124);
            }
            return new ProcessResult(process.exitValue());
        }
        finally
        {
            activeAgentProcess = null;
        }
    }

    private void syncManagedRepository(Path repository, Path incidentDir) throws Exception
    {
        Files.createDirectories(repository.getParent());
        Path gitLog = incidentDir.resolve("git.log");
        if (!Files.isDirectory(repository.resolve(".git")))
        {
            if (Files.exists(repository) && !isDirectoryEmpty(repository))
            {
                throw new IllegalStateException("Managed repository path exists but is not empty Git repo: " + repository);
            }
            Files.createDirectories(repository);
            runChecked(Arrays.asList("git", "clone", "--branch", config.repositoryBranch(),
                            "--single-branch", config.repositoryUrl(), repository.toAbsolutePath().toString()),
                    repository.getParent(), Duration.ofMinutes(3), gitLog);
            return;
        }
        runChecked(Arrays.asList("git", "fetch", "origin", config.repositoryBranch()),
                repository, Duration.ofMinutes(2), gitLog);
        runChecked(Arrays.asList("git", "checkout", config.repositoryBranch()),
                repository, Duration.ofSeconds(30), gitLog);
        runChecked(Arrays.asList("git", "reset", "--hard", "origin/" + config.repositoryBranch()),
                repository, Duration.ofSeconds(30), gitLog);
        runChecked(Arrays.asList("git", "clean", "-fd"),
                repository, Duration.ofSeconds(30), gitLog);
    }

    private void resetWorkingTree(Path repository, Path incidentDir) throws Exception
    {
        runChecked(Arrays.asList("git", "reset", "--hard", "HEAD"), repository,
                Duration.ofSeconds(30), incidentDir.resolve("git.log"));
        runChecked(Arrays.asList("git", "clean", "-fd"), repository,
                Duration.ofSeconds(30), incidentDir.resolve("git.log"));
    }

    private List<String> changedFiles(Path repository) throws Exception
    {
        String output = captureProcess(Arrays.asList("git", "status", "--porcelain"),
                repository, Duration.ofSeconds(30));
        if (output.trim().isEmpty())
        {
            return Collections.emptyList();
        }
        List<String> files = new ArrayList<>();
        for (String line : output.split("\\R"))
        {
            if (line.length() >= 4)
            {
                files.add(line.substring(3).trim().replace('\\', '/'));
            }
        }
        return files;
    }

    private String validateChangedPaths(List<String> files, String sourceArea)
    {
        String prefix = sourceArea.replace('\\', '/') + "/";
        for (String path : files)
        {
            String normalized = path.replace('\\', '/');
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (normalized.contains(" -> "))
            {
                return "Renames are not allowed during autonomous repair: " + normalized;
            }
            if (!normalized.endsWith(".java"))
            {
                return "Agent changed a non-Java file: " + normalized;
            }
            if (!normalized.startsWith(prefix))
            {
                return "Agent changed a file outside target plugin source directory: " + normalized;
            }
            if (lower.contains("kspautorepair") || lower.contains("kspsourceloader")
                    || lower.contains(".github/") || lower.contains("gradle"))
            {
                return "Agent attempted to modify protected infrastructure: " + normalized;
            }
        }
        return null;
    }

    private void evaluatePendingRepair()
    {
        PendingRepair pending = pendingRepair;
        if (pending == null || pending.rollbackScheduled || pending.loaderSeenAtMs == 0L)
        {
            return;
        }
        long validationMs = TimeUnit.SECONDS.toMillis(config.validationSeconds());
        if (System.currentTimeMillis() - pending.loaderSeenAtMs < validationMs)
        {
            return;
        }
        long progressDelta = semanticProgress.get() - pending.validationStartProgress;
        if (progressDelta < config.minValidationProgressEvents())
        {
            if (config.autoRollback())
            {
                scheduleRollback(pending, "Runtime validation produced only " + progressDelta
                        + " semantic progress event(s); required " + config.minValidationProgressEvents());
            }
            else
            {
                writeStatus(getIncidentRoot().resolve(pending.incidentId), "VALIDATION_INCONCLUSIVE",
                        "Insufficient semantic progress and automatic rollback is disabled");
            }
            return;
        }
        Path incidentDir = getIncidentRoot().resolve(pending.incidentId);
        writeStatus(incidentDir, "VALIDATED",
                "Repaired revision was accepted and produced " + progressDelta + " semantic progress event(s)");
        appendRepairHistory(historyFromPending(pending, "VALIDATED"));
        recordEvent("REPAIR_VALIDATED", pending.targetPluginClass, pending.commitSha,
                detail("progressEvents", String.valueOf(progressDelta)), true);
        clearPendingRepair();
        setPhase(Phase.IDLE, "");
    }

    private void observeRevision(String revision)
    {
        if (revision == null || revision.isEmpty())
        {
            return;
        }
        String normalized = revision.toLowerCase(Locale.ROOT);
        lastSeenRevision = normalized;
        PendingRepair pending = pendingRepair;
        if (pending != null && pending.loaderSeenAtMs == 0L && revisionMatches(normalized, pending.commitSha))
        {
            pending.loaderSeenAtMs = System.currentTimeMillis();
            pending.validationStartProgress = semanticProgress.get();
            writePendingRepair(pending);
            setPhase(Phase.VALIDATING_RUNTIME, pending.commitSha);
            recordEvent("REPAIR_REVISION_LOADED", pending.targetPluginClass,
                    normalized, Collections.emptyMap(), false);
        }
    }

    private void scheduleRollback(PendingRepair pending, String reason)
    {
        if (pending == null || pending.rollbackScheduled)
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
        setPhase(Phase.ROLLING_BACK, pending.commitSha);
        try
        {
            Path repository = Paths.get(pending.repositoryPath);
            Path incidentDir = getIncidentRoot().resolve(pending.incidentId);
            Path gitLog = incidentDir.resolve("git.log");
            runChecked(Arrays.asList("git", "fetch", "origin", config.repositoryBranch()),
                    repository, Duration.ofMinutes(2), gitLog);
            runChecked(Arrays.asList("git", "checkout", config.repositoryBranch()),
                    repository, Duration.ofSeconds(30), gitLog);
            runChecked(Arrays.asList("git", "reset", "--hard", "origin/" + config.repositoryBranch()),
                    repository, Duration.ofSeconds(30), gitLog);
            runChecked(Arrays.asList("git", "revert", "--no-edit", pending.commitSha),
                    repository, Duration.ofMinutes(1), gitLog);
            runChecked(Arrays.asList("git", "push", "origin", "HEAD:" + config.repositoryBranch()),
                    repository, Duration.ofMinutes(2), gitLog);
            if (config.requestLoaderRefresh())
            {
                sourceLoaderBridge.requestRefresh();
            }
            writeStatus(incidentDir, "ROLLED_BACK", reason);
            appendRepairHistory(historyFromPending(pending, "ROLLED_BACK: " + reason));
            recordEvent("REPAIR_ROLLBACK", pending.targetPluginClass, pending.commitSha,
                    detail("reason", reason), false);
            clearPendingRepair();
        }
        catch (Throwable t)
        {
            log.error("KSP Auto Repair v0.2 rollback failed for {}", pending.commitSha, t);
            writeStatus(getIncidentRoot().resolve(pending.incidentId),
                    "ROLLBACK_FAILED", reason + " | " + t);
            appendRepairHistory(historyFromPending(pending, "ROLLBACK_FAILED: " + t));
        }
        finally
        {
            repairInProgress.set(false);
            if (pendingRepair == null)
            {
                setPhase(Phase.IDLE, "");
            }
        }
    }

    private void evaluatePhaseWatchdog()
    {
        Phase current = phase;
        if (current == Phase.IDLE)
        {
            return;
        }
        long age = System.currentTimeMillis() - phaseStartedAtMs;
        long limit;
        switch (current)
        {
            case CAPTURING_INCIDENT:
                limit = TimeUnit.SECONDS.toMillis(30); break;
            case SYNCING_SOURCE:
                limit = TimeUnit.MINUTES.toMillis(3); break;
            case RUNNING_AGENT:
                limit = TimeUnit.MINUTES.toMillis(config.agentTimeoutMinutes()) + TimeUnit.SECONDS.toMillis(30); break;
            case VALIDATING_CHANGES:
                limit = TimeUnit.MINUTES.toMillis(3); break;
            case COMMITTING:
            case PUSHING:
                limit = TimeUnit.MINUTES.toMillis(2); break;
            case WAITING_FOR_LOADER:
                limit = TimeUnit.MINUTES.toMillis(3); break;
            case VALIDATING_RUNTIME:
                limit = TimeUnit.SECONDS.toMillis(config.validationSeconds() + 90L); break;
            case ROLLING_BACK:
                limit = TimeUnit.MINUTES.toMillis(3); break;
            default:
                limit = TimeUnit.MINUTES.toMillis(5); break;
        }
        if (age <= limit)
        {
            return;
        }
        if (current == Phase.RUNNING_AGENT)
        {
            Process process = activeAgentProcess;
            if (process != null && process.isAlive())
            {
                process.destroyForcibly();
            }
            recordEvent("WATCHDOG", null, "Agent process exceeded phase deadline",
                    detail("phaseDetail", phaseDetail), false);
            return;
        }
        if (current == Phase.WAITING_FOR_LOADER && !loaderRefreshRetried)
        {
            loaderRefreshRetried = true;
            sourceLoaderBridge.requestRefresh();
            phaseStartedAtMs = System.currentTimeMillis();
            recordEvent("WATCHDOG", null,
                    "Source Loader wait exceeded initial deadline; refresh requested",
                    Collections.emptyMap(), false);
            return;
        }
        if (current == Phase.WAITING_FOR_LOADER && pendingRepair != null && config.autoRollback())
        {
            scheduleRollback(pendingRepair,
                    "Source Loader did not observe repaired revision within watchdog deadline");
        }
    }

    private void setPhase(Phase newPhase, String detailValue)
    {
        phase = newPhase;
        phaseDetail = safe(detailValue);
        phaseStartedAtMs = System.currentTimeMillis();
        recordEvent("REPAIR_PHASE", null, newPhase.name(), detail("detail", phaseDetail), false);
    }

    private void pollClientLog() throws IOException
    {
        Path logFile = getClientLogPath();
        if (!Files.isRegularFile(logFile))
        {
            return;
        }
        try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r"))
        {
            long length = file.length();
            if (length < logOffset)
            {
                logOffset = 0L;
            }
            file.seek(logOffset);
            String line;
            while ((line = file.readLine()) != null)
            {
                acceptLogLine(line);
            }
            logOffset = file.getFilePointer();
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
        Matcher revisionMatcher = REVISION_PATTERN.matcher(line);
        if (revisionMatcher.find())
        {
            observeRevision(revisionMatcher.group(1));
        }
        PendingRepair pending = pendingRepair;
        if (pending != null && pending.loaderSeenAtMs > 0L && isSourceLoaderFailure(line))
        {
            scheduleRollback(pending, "Source Loader rejected repaired revision: " + truncate(line, 600));
            return;
        }
        FailureKind kind = classifyLogLine(line);
        if (kind == null)
        {
            return;
        }
        recordEvent("LOG_ERROR", null, truncate(line, 800),
                detail("classification", kind.name()), false);
        if (pendingRepair != null || repairInProgress.get())
        {
            return;
        }
        RuntimeFrame frame = lastFrame.get();
        if (frame == null || frame.activeKspPlugins.isEmpty())
        {
            return;
        }
        PluginIdentity target = chooseTarget(frame.activeKspPlugins, line);
        if (target == null || isCoolingDown(target.className))
        {
            return;
        }
        double confidence = logConfidence(kind, line, target);
        createIncidentRequest("LOG_ERROR", kind, truncate(line, 1000), target,
                confidence, null, frame.stateSignatures.get(target.className));
    }

    private FailureKind classifyLogLine(String line)
    {
        String lower = safe(line).toLowerCase(Locale.ROOT);
        if (lower.contains("permission denied") || lower.contains("authentication failed")
                || lower.contains("could not read username") || lower.contains("repository not found"))
        {
            return FailureKind.GIT_AUTH;
        }
        if (lower.contains("connection timed out") || lower.contains("unknownhost")
                || lower.contains("connection reset") || lower.contains("network is unreachable"))
        {
            return FailureKind.NETWORK;
        }
        if (lower.contains("must be called on client thread")) return FailureKind.CLIENT_THREAD;
        if (lower.contains("nullpointerexception")) return FailureKind.NULL_POINTER;
        if (lower.contains("cannot find symbol") || lower.contains("nosuchmethod")
                || lower.contains("nosuchfielderror") || lower.contains("abstractmethoderror"))
        {
            return FailureKind.API_CHANGED;
        }
        if (lower.contains("runtime compilation failed") || lower.contains("compilation failed"))
        {
            return FailureKind.COMPILATION;
        }
        if (lower.contains("source update rejected") || lower.contains("pluginmanager.loadplugins failed"))
        {
            return FailureKind.SOURCE_LOADER;
        }
        if (lower.contains("widget") && (lower.contains("timeout") || lower.contains("not found")))
        {
            return FailureKind.WIDGET_TIMEOUT;
        }
        if (lower.contains("dialog") && (lower.contains("failed") || lower.contains("timeout")))
        {
            return FailureKind.DIALOGUE;
        }
        if (lower.contains("walk") && (lower.contains("failed") || lower.contains("stuck")
                || lower.contains("timeout")))
        {
            return FailureKind.PATHING;
        }
        if (lower.contains("bank") && (lower.contains("failed") || lower.contains("could not")
                || lower.contains("timeout") || lower.contains("stopped:")))
        {
            return FailureKind.BANKING;
        }
        boolean errorSignal = lower.contains(" error ") || lower.contains("exception")
                || lower.contains("failed") || lower.contains("could not switch") || lower.contains("stopped:");
        if (!errorSignal)
        {
            return null;
        }
        return lower.contains("ksp") || lower.contains("microbot") ? FailureKind.UNKNOWN : null;
    }

    private FailureKind classifyInfrastructureText(String text)
    {
        String lower = safe(text).toLowerCase(Locale.ROOT);
        if (lower.contains("not recognized") || lower.contains("command not found")
                || lower.contains("no such file") || lower.contains("cannot find the file"))
        {
            return FailureKind.AGENT_EXECUTABLE;
        }
        if (lower.contains("unauthorized") || lower.contains("authentication")
                || lower.contains("api key") || lower.contains("login required"))
        {
            return FailureKind.AGENT_AUTH;
        }
        if (lower.contains("permission denied") || lower.contains("repository not found"))
        {
            return FailureKind.GIT_AUTH;
        }
        if (lower.contains("network") || lower.contains("connection") || lower.contains("timed out"))
        {
            return FailureKind.NETWORK;
        }
        return FailureKind.UNKNOWN;
    }

    private double logConfidence(FailureKind kind, String line, PluginIdentity target)
    {
        String lower = safe(line).toLowerCase(Locale.ROOT);
        boolean named = lower.contains(simpleName(target.className).toLowerCase(Locale.ROOT));
        switch (kind)
        {
            case CLIENT_THREAD:
            case NULL_POINTER:
            case API_CHANGED:
                return named ? 0.98 : 0.90;
            case COMPILATION:
            case SOURCE_LOADER:
                return named ? 0.96 : 0.86;
            case BANKING:
            case WIDGET_TIMEOUT:
            case DIALOGUE:
            case PATHING:
                return named ? 0.94 : 0.87;
            case NETWORK:
            case GIT_AUTH:
            case AGENT_AUTH:
            case AGENT_EXECUTABLE:
            case ENVIRONMENT:
                return 0.10;
            default:
                return named ? 0.90 : 0.78;
        }
    }

    private boolean isSourceLoaderFailure(String line)
    {
        String lower = safe(line).toLowerCase(Locale.ROOT);
        return lower.contains("ksp source update failed") || lower.contains("ksp runtime compilation failed")
                || lower.contains("source update rejected")
                || (lower.contains("pluginmanager.loadplugins failed") && lower.contains("ksp"));
    }

    private void recordEvent(String type, String pluginClass, String summary,
                             Map<String, String> details, boolean progress)
    {
        TimelineEvent event = new TimelineEvent();
        event.timestampMs = System.currentTimeMillis();
        event.timestamp = Instant.ofEpochMilli(event.timestampMs).toString();
        event.type = safe(type);
        event.pluginClass = safe(pluginClass);
        event.summary = truncate(safe(summary), 1000);
        event.details = details == null ? Collections.emptyMap() : new LinkedHashMap<>(details);
        synchronized (timeline)
        {
            timeline.addLast(event);
            int max = Math.max(200, config.maxTimelineEvents());
            while (timeline.size() > max)
            {
                timeline.removeFirst();
            }
        }
        if (progress)
        {
            markProgress(type, pluginClass, summary);
        }
    }

    private void markProgress(String type, String pluginClass, String summary)
    {
        semanticProgress.incrementAndGet();
        lastProgressAtMs = System.currentTimeMillis();
        if (pluginClass != null && !pluginClass.isEmpty())
        {
            synchronized (pluginRuntime)
            {
                PluginRuntime runtime = pluginRuntime.get(pluginClass);
                if (runtime != null)
                {
                    runtime.lastSemanticProgressAtMs = lastProgressAtMs;
                }
            }
        }
    }

    private Map<String, Object> captureEnvironment()
    {
        Map<String, Object> environment = new LinkedHashMap<>();
        RuntimeFrame frame = lastFrame.get();
        environment.put("runtimeFrame", frame);
        environment.put("inventory", containerSnapshot(InventoryID.INV));
        environment.put("equipment", containerSnapshot(InventoryID.WORN));
        environment.put("destination", reflectClientDestination());
        environment.put("selection", reflectClientSelection());
        environment.put("widgets", visibleWidgetSnapshot());
        environment.put("nearbyScene", nearbySceneSnapshot(frame));
        environment.put("phase", phase.name());
        environment.put("phaseDetail", phaseDetail);
        environment.put("semanticProgressCounter", semanticProgress.get());
        return environment;
    }

    private List<Map<String, Object>> containerSnapshot(int inventoryId)
    {
        ItemContainer container = client.getItemContainer(inventoryId);
        if (container == null)
        {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        int slot = 0;
        for (Item item : container.getItems())
        {
            if (item.getId() < 0 || item.getQuantity() <= 0)
            {
                slot++;
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("slot", slot++);
            entry.put("id", item.getId());
            entry.put("quantity", item.getQuantity());
            result.add(entry);
        }
        return result;
    }

    private Map<String, Object> reflectClientDestination()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        putReflective(result, "localDestination", client, "getLocalDestinationLocation");
        putReflective(result, "destination", client, "getDestination");
        putReflective(result, "plane", client, "getPlane");
        return result;
    }

    private Map<String, Object> reflectClientSelection()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        String[] methods = {"isSpellSelected", "getSelectedSpellWidget", "getSelectedSpellChildIndex",
                "getSelectedItemId", "getSelectedItemWidget", "getSelectedItemSlot"};
        for (String method : methods)
        {
            putReflective(result, method, client, method);
        }
        return result;
    }

    private List<Map<String, Object>> visibleWidgetSnapshot()
    {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        try
        {
            Method method = client.getClass().getMethod("getWidgetRoots");
            Object roots = method.invoke(client);
            for (Object root : iterableValues(roots))
            {
                collectWidget(root, result, seen, 0);
                if (result.size() >= MAX_WIDGETS_IN_SNAPSHOT) break;
            }
        }
        catch (Throwable t)
        {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("snapshotError", t.toString());
            result.add(error);
        }
        return result;
    }

    private void collectWidget(Object widget, List<Map<String, Object>> output,
                               Set<Integer> seen, int depth)
    {
        if (widget == null || depth > 8 || output.size() >= MAX_WIDGETS_IN_SNAPSHOT)
        {
            return;
        }
        int identity = System.identityHashCode(widget);
        if (!seen.add(identity)) return;
        Boolean hidden = reflectBoolean(widget, "isHidden");
        if (!Boolean.TRUE.equals(hidden))
        {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", reflectValue(widget, "getId"));
            info.put("type", reflectValue(widget, "getType"));
            info.put("text", truncate(String.valueOf(reflectValue(widget, "getText")), 500));
            info.put("name", truncate(String.valueOf(reflectValue(widget, "getName")), 300));
            info.put("actions", reflectValue(widget, "getActions"));
            output.add(info);
        }
        String[] childMethods = {"getChildren", "getDynamicChildren", "getStaticChildren", "getNestedChildren"};
        for (String childMethod : childMethods)
        {
            for (Object child : iterableValues(reflectValue(widget, childMethod)))
            {
                collectWidget(child, output, seen, depth + 1);
                if (output.size() >= MAX_WIDGETS_IN_SNAPSHOT) return;
            }
        }
    }

    private List<SceneEntity> nearbySceneSnapshot(RuntimeFrame frame)
    {
        if (frame == null || frame.x < 0 || frame.y < 0)
        {
            return Collections.emptyList();
        }
        int radius = config.sceneRadius();
        List<SceneEntity> result = new ArrayList<>();
        synchronized (sceneCache)
        {
            for (SceneEntity entity : sceneCache.values())
            {
                if (entity.x < 0 || entity.y < 0 || entity.plane != frame.plane) continue;
                if (distance(frame.x, frame.y, entity.x, entity.y) <= radius) result.add(entity);
            }
        }
        result.sort(Comparator.comparingInt(entity -> distance(frame.x, frame.y, entity.x, entity.y)));
        return result;
    }

    private void updateSceneEntity(String kind, Object event, boolean spawned)
    {
        Object entity = reflectValue(event, "getNpc");
        if (entity == null) entity = reflectValue(event, "getGameObject");
        if (entity == null) return;
        SceneEntity record = sceneEntity(kind, entity);
        if (record == null) return;
        synchronized (sceneCache)
        {
            if (spawned)
            {
                sceneCache.put(record.key, record);
                while (sceneCache.size() > MAX_SCENE_CACHE)
                {
                    String first = sceneCache.keySet().iterator().next();
                    sceneCache.remove(first);
                }
            }
            else
            {
                removeMatchingSceneEntity(record);
            }
        }
        recordEvent(spawned ? kind + "_SPAWNED" : kind + "_DESPAWNED", null,
                record.name + " id=" + record.id,
                detail("location", record.x + "," + record.y + "," + record.plane), false);
    }

    private void removeMatchingSceneEntity(SceneEntity record)
    {
        String matching = null;
        for (Map.Entry<String, SceneEntity> entry : sceneCache.entrySet())
        {
            SceneEntity current = entry.getValue();
            if (current.kind.equals(record.kind) && current.id == record.id && current.x == record.x
                    && current.y == record.y && current.plane == record.plane)
            {
                matching = entry.getKey();
                break;
            }
        }
        if (matching != null) sceneCache.remove(matching);
    }

    private SceneEntity sceneEntity(String kind, Object entity)
    {
        try
        {
            int id = reflectInt(entity, "getId", -1);
            String name = reflectedString(entity, "getName");
            Object location = reflectValue(entity, "getWorldLocation");
            int x = reflectInt(location, "getX", -1);
            int y = reflectInt(location, "getY", -1);
            int plane = reflectInt(location, "getPlane", -1);
            String[] entityActions = reflectStringArray(entity, "getActions");
            SceneEntity result = new SceneEntity();
            result.kind = kind;
            result.id = id;
            result.name = name;
            result.x = x;
            result.y = y;
            result.plane = plane;
            result.actions = entityActions == null ? Collections.emptyList() : Arrays.asList(entityActions);
            result.key = kind + ":" + id + ":" + x + ":" + y + ":" + plane + ":"
                    + System.identityHashCode(entity);
            return result;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private Map<String, Object> inspectPluginState(String className)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Plugin plugin : pluginManager.getPlugins())
        {
            if (plugin != null && plugin.getClass().getName().equals(className))
            {
                result.put("pluginClass", className);
                Map<String, String> fields = new LinkedHashMap<>();
                inspectStateFields(plugin, "plugin", fields, true);
                result.put("fields", fields);
                break;
            }
        }
        synchronized (pluginRuntime)
        {
            PluginRuntime runtime = pluginRuntime.get(className);
            if (runtime != null) result.put("runtime", runtime);
        }
        return result;
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
                try { client.getCanvas().paint(graphics); }
                finally { graphics.dispose(); }
                imageRef.set(image);
            };
            if (SwingUtilities.isEventDispatchThread()) capture.run();
            else SwingUtilities.invokeAndWait(capture);
            BufferedImage image = imageRef.get();
            if (image != null)
            {
                ImageIO.write(image, "png", destination.toFile());
                return destination;
            }
        }
        catch (Throwable t)
        {
            log.warn("KSP Auto Repair v0.2 screenshot capture failed", t);
        }
        return null;
    }

    private List<RepairHistoryRecord> findSimilarRepairHistory(Incident incident, int max)
    {
        Path history = getHistoryPath();
        if (!Files.isRegularFile(history)) return Collections.emptyList();
        List<RepairHistoryRecord> matches = new ArrayList<>();
        try
        {
            for (String line : Files.readAllLines(history, StandardCharsets.UTF_8))
            {
                if (line.trim().isEmpty()) continue;
                RepairHistoryRecord record = GSON.fromJson(line, RepairHistoryRecord.class);
                if (record == null || !incident.targetPluginClass.equals(record.pluginClass)) continue;
                int score = 0;
                if (incident.failureKind.equals(record.failureKind)) score += 3;
                if (!incident.stateSignature.isEmpty() && incident.stateSignature.equals(record.stateSignature)) score += 2;
                record.similarityScore = score;
                if (score > 0) matches.add(record);
            }
        }
        catch (Throwable t)
        {
            log.debug("Unable to read repair history", t);
        }
        matches.sort((left, right) -> {
            int score = Integer.compare(right.similarityScore, left.similarityScore);
            return score != 0 ? score : safe(right.timestamp).compareTo(safe(left.timestamp));
        });
        return matches.size() > max ? new ArrayList<>(matches.subList(0, max)) : matches;
    }

    private void appendRepairHistory(RepairHistoryRecord record)
    {
        if (record == null) return;
        try
        {
            Path path = getHistoryPath();
            Files.createDirectories(path.getParent());
            Files.write(path, (GSON.toJson(record) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (Throwable t)
        {
            log.warn("Unable to append KSP repair history", t);
        }
    }

    private RepairHistoryRecord historyFor(Incident incident, String beforeSha, String repairSha,
                                           String outcome, String summary)
    {
        RepairHistoryRecord record = new RepairHistoryRecord();
        record.timestamp = Instant.now().toString();
        record.incidentId = incident.incidentId;
        record.pluginClass = incident.targetPluginClass;
        record.failureKind = incident.failureKind;
        record.stateSignature = incident.stateSignature;
        record.beforeSha = safe(beforeSha);
        record.repairSha = safe(repairSha);
        record.outcome = outcome;
        record.agentSummary = truncate(safe(summary), 4000);
        record.confidence = incident.confidence;
        return record;
    }

    private RepairHistoryRecord historyFromPending(PendingRepair pending, String outcome)
    {
        RepairHistoryRecord record = new RepairHistoryRecord();
        record.timestamp = Instant.now().toString();
        record.incidentId = pending.incidentId;
        record.pluginClass = pending.targetPluginClass;
        record.failureKind = pending.failureKind;
        record.stateSignature = safe(pending.stateSignature);
        record.beforeSha = safe(pending.beforeSha);
        record.repairSha = safe(pending.commitSha);
        record.outcome = outcome;
        record.agentSummary = truncate(safe(pending.agentSummary), 4000);
        record.validationProgressEvents = Math.max(0L,
                semanticProgress.get() - pending.validationStartProgress);
        return record;
    }

    private PluginIdentity chooseTarget(List<PluginIdentity> plugins, String evidence)
    {
        if (plugins == null || plugins.isEmpty()) return null;
        if (evidence != null)
        {
            String lower = evidence.toLowerCase(Locale.ROOT);
            for (PluginIdentity plugin : plugins)
            {
                if (lower.contains(simpleName(plugin.className).toLowerCase(Locale.ROOT))) return plugin;
            }
        }
        PluginIdentity newest = null;
        long newestTouched = Long.MIN_VALUE;
        synchronized (pluginRuntime)
        {
            for (PluginIdentity plugin : plugins)
            {
                if (plugin.className.toLowerCase(Locale.ROOT).contains("autorun")) continue;
                PluginRuntime runtime = pluginRuntime.get(plugin.className);
                long touched = runtime == null ? 0L : runtime.lastTouchedAtMs;
                if (newest == null || touched > newestTouched)
                {
                    newest = plugin;
                    newestTouched = touched;
                }
            }
        }
        return newest != null ? newest : plugins.get(0);
    }

    private PluginIdentity resolvePluginHint(String hint)
    {
        RuntimeFrame frame = lastFrame.get();
        if (frame == null || frame.activeKspPlugins.isEmpty()) return null;
        String normalized = safe(hint).toLowerCase(Locale.ROOT);
        if (!normalized.isEmpty())
        {
            for (PluginIdentity identity : frame.activeKspPlugins)
            {
                if (identity.className.equalsIgnoreCase(hint)
                        || simpleName(identity.className).equalsIgnoreCase(hint)
                        || identity.displayName.equalsIgnoreCase(hint)
                        || identity.className.toLowerCase(Locale.ROOT).contains(normalized)) return identity;
            }
        }
        return chooseTarget(frame.activeKspPlugins, hint);
    }

    private PluginIdentity findIdentity(List<PluginIdentity> plugins, String className)
    {
        for (PluginIdentity plugin : plugins) if (plugin.className.equals(className)) return plugin;
        return null;
    }

    private boolean isCoolingDown(String className)
    {
        Long last = incidentCooldowns.get(className);
        return last != null && System.currentTimeMillis() - last
                < TimeUnit.SECONDS.toMillis(config.incidentCooldownSeconds());
    }

    private void initialiseLogOffset()
    {
        try
        {
            Path path = getClientLogPath();
            logOffset = Files.isRegularFile(path) ? Files.size(path) : 0L;
        }
        catch (IOException ignored) { logOffset = 0L; }
    }

    private PendingRepair readPendingRepair()
    {
        Path path = getPendingPath();
        if (!Files.isRegularFile(path)) return null;
        try
        {
            PendingRepair pending = GSON.fromJson(new String(Files.readAllBytes(path), StandardCharsets.UTF_8),
                    PendingRepair.class);
            if (pending != null)
            {
                setPhase(pending.loaderSeenAtMs > 0L ? Phase.VALIDATING_RUNTIME : Phase.WAITING_FOR_LOADER,
                        pending.commitSha);
            }
            return pending;
        }
        catch (Throwable t)
        {
            log.warn("Unable to restore KSP v0.2 pending repair", t);
            return null;
        }
    }

    private void writePendingRepair(PendingRepair pending)
    {
        try { writeJsonAtomic(getPendingPath(), pending); }
        catch (IOException e) { log.warn("Unable to persist KSP v0.2 pending repair", e); }
    }

    private void clearPendingRepair()
    {
        pendingRepair = null;
        try { Files.deleteIfExists(getPendingPath()); }
        catch (IOException e) { log.warn("Unable to clear KSP v0.2 pending repair", e); }
    }

    private Path managedRepositoryPath()
    {
        String configured = safe(config.repositoryPath()).trim();
        if (!configured.isEmpty()) return Paths.get(configured).toAbsolutePath().normalize();
        return getRepairRoot().resolve("worktree").resolve("ksppluginsrelease").toAbsolutePath().normalize();
    }

    private Path getRepairRoot()
    {
        Path root = Paths.get(System.getProperty("user.home"), ".runelite", REPAIR_ROOT_NAME);
        try { Files.createDirectories(root); }
        catch (IOException e) { throw new IllegalStateException("Unable to create KSP repair directory " + root, e); }
        return root;
    }

    private Path getIncidentRoot()
    {
        Path root = getRepairRoot().resolve("incidents");
        try { Files.createDirectories(root); }
        catch (IOException e) { throw new IllegalStateException("Unable to create KSP incident directory " + root, e); }
        return root;
    }

    private Path getPendingPath() { return getRepairRoot().resolve("pending-v2.json"); }
    private Path getHistoryPath() { return getRepairRoot().resolve("repair-history.jsonl"); }
    private Path getClientLogPath() { return Paths.get(System.getProperty("user.home"), ".runelite", "logs", "client.log"); }

    private void appendTrace(RuntimeFrame frame)
    {
        synchronized (trace)
        {
            trace.addLast(frame);
            while (trace.size() > TRACE_LIMIT) trace.removeFirst();
        }
    }

    private List<RuntimeFrame> snapshotTrace() { synchronized (trace) { return new ArrayList<>(trace); } }
    private List<TimelineEvent> snapshotTimeline() { synchronized (timeline) { return new ArrayList<>(timeline); } }
    private List<ActionRecord> snapshotActions() { synchronized (actions) { return new ArrayList<>(actions); } }
    private List<PluginRuntime> snapshotPluginRuntime() { synchronized (pluginRuntime) { return new ArrayList<>(pluginRuntime.values()); } }
    private List<String> snapshotLogs() { synchronized (recentLogs) { return new ArrayList<>(recentLogs); } }

    private void writeJsonAtomic(Path path, Object value) throws IOException
    {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.write(temp, GSON.toJson(value).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException atomicFailure) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
    }

    private void writeJsonLines(Path path, Collection<?> values) throws IOException
    {
        List<String> lines = new ArrayList<>();
        for (Object value : values) lines.add(GSON.toJson(value));
        Files.write(path, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writeLines(Path path, List<String> lines) throws IOException
    {
        Files.write(path, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writeStatus(Path incidentDir, String status, String message)
    {
        try
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("timestamp", Instant.now().toString());
            payload.put("status", status);
            payload.put("message", message);
            payload.put("phase", phase.name());
            payload.put("phaseDetail", phaseDetail);
            writeJsonAtomic(incidentDir.resolve("repair-status.json"), payload);
        }
        catch (IOException e) { log.warn("Unable to write KSP v0.2 repair status", e); }
    }

    private ProcessResult runProcess(List<String> command, Path workingDirectory,
                                     Path output, Duration timeout, boolean append) throws Exception
    {
        Files.createDirectories(output.getParent());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(append ? ProcessBuilder.Redirect.appendTo(output.toFile())
                : ProcessBuilder.Redirect.to(output.toFile()));
        Process process = builder.start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished)
        {
            process.destroyForcibly();
            return new ProcessResult(124);
        }
        return new ProcessResult(process.exitValue());
    }

    private void runChecked(List<String> command, Path workingDirectory,
                            Duration timeout, Path logFile) throws Exception
    {
        ProcessResult result = runProcess(command, workingDirectory, logFile, timeout, true);
        if (result.exitCode != 0)
        {
            String output = Files.isRegularFile(logFile)
                    ? truncate(new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8), 3000) : "";
            throw new IllegalStateException("Command failed with exit code " + result.exitCode + ": "
                    + String.join(" ", command) + "\\n" + output);
        }
    }

    private String captureProcess(List<String> command, Path workingDirectory, Duration timeout) throws Exception
    {
        Path temp = Files.createTempFile(getRepairRoot(), "process-", ".log");
        try
        {
            ProcessResult result = runProcess(command, workingDirectory, temp, timeout, false);
            String output = new String(Files.readAllBytes(temp), StandardCharsets.UTF_8);
            if (result.exitCode != 0)
            {
                throw new IllegalStateException("Command failed with exit code " + result.exitCode + ": "
                        + String.join(" ", command) + "\\n" + output);
            }
            return output;
        }
        finally { Files.deleteIfExists(temp); }
    }

    private String readSummary(Path output)
    {
        try
        {
            return Files.isRegularFile(output)
                    ? truncate(new String(Files.readAllBytes(output), StandardCharsets.UTF_8), 4000) : "";
        }
        catch (IOException ignored) { return ""; }
    }

    private boolean isDirectoryEmpty(Path directory) throws IOException
    {
        if (!Files.isDirectory(directory)) return true;
        try (Stream<Path> stream = Files.list(directory)) { return !stream.findFirst().isPresent(); }
    }

    private void deleteRecursively(Path root)
    {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> stream = Files.walk(root))
        {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            for (Path path : paths) Files.deleteIfExists(path);
        }
        catch (IOException e) { log.debug("Unable to remove temporary KSP validation directory {}", root, e); }
    }

    private String sourceArea(String className)
    {
        String marker = ".microbot.";
        int index = className.indexOf(marker);
        if (index < 0) return "";
        String remainder = className.substring(index + marker.length());
        int dot = remainder.indexOf('.');
        return dot < 0 ? "" : remainder.substring(0, dot);
    }

    private Optional<String> extractRevision(Map<String, String> details)
    {
        if (details == null) return Optional.empty();
        for (String value : details.values())
        {
            Matcher matcher = SHA_PATTERN.matcher(safe(value));
            if (matcher.find()) return Optional.of(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return Optional.empty();
    }

    private boolean revisionMatches(String observed, String fullSha)
    {
        if (observed == null || fullSha == null) return false;
        String left = observed.toLowerCase(Locale.ROOT);
        String right = fullSha.toLowerCase(Locale.ROOT);
        return left.startsWith(right) || right.startsWith(left);
    }

    private Map<String, String> reflectedProperties(Object object, String... methodNames)
    {
        Map<String, String> result = new LinkedHashMap<>();
        for (String method : methodNames)
        {
            Object value = reflectValue(object, method);
            if (value != null) result.put(method, truncate(String.valueOf(value), 400));
        }
        return result;
    }

    private String summarizeActorEvent(Object event)
    {
        Object actor = reflectValue(event, "getActor");
        if (actor == null) return String.valueOf(event);
        return reflectedString(actor, "getName") + " animation=" + reflectInt(actor, "getAnimation", -1);
    }

    private void putReflective(Map<String, Object> result, String key, Object object, String method)
    {
        Object value = reflectValue(object, method);
        if (value != null) result.put(key, String.valueOf(value));
    }

    private Object reflectValue(Object object, String methodName)
    {
        if (object == null) return null;
        try
        {
            Method method = object.getClass().getMethod(methodName);
            if (method.getParameterCount() == 0) return method.invoke(object);
        }
        catch (Throwable ignored) { }
        return null;
    }

    private int reflectInt(Object object, String methodName, int fallback)
    {
        Object value = reflectValue(object, methodName);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private Boolean reflectBoolean(Object object, String methodName)
    {
        Object value = reflectValue(object, methodName);
        return value instanceof Boolean ? (Boolean) value : null;
    }

    private String reflectedString(Object object, String methodName)
    {
        Object value = reflectValue(object, methodName);
        return value == null ? "" : String.valueOf(value);
    }

    private String[] reflectStringArray(Object object, String methodName)
    {
        Object value = reflectValue(object, methodName);
        return value instanceof String[] ? (String[]) value : null;
    }

    private List<Object> iterableValues(Object value)
    {
        if (value == null) return Collections.emptyList();
        if (value instanceof Iterable)
        {
            List<Object> result = new ArrayList<>();
            for (Object item : (Iterable<?>) value) result.add(item);
            return result;
        }
        if (value.getClass().isArray())
        {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) result.add(Array.get(value, i));
            return result;
        }
        return Collections.singletonList(value);
    }

    private List<Field> allFields(Class<?> type)
    {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        int depth = 0;
        while (current != null && current != Object.class && depth++ < 6)
        {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    private boolean isSafeScalar(Object value)
    {
        return value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Character || value.getClass().isEnum();
    }

    private boolean looksLikeDialogue(String text)
    {
        String lower = safe(text).toLowerCase(Locale.ROOT);
        return lower.contains("continue") || lower.contains("select an option")
                || lower.contains("what would you like") || lower.contains("please wait");
    }

    private static int distance(int x1, int y1, int x2, int y2)
    {
        return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
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
        int dot = safe(className).lastIndexOf('.');
        return dot < 0 ? safe(className) : className.substring(dot + 1);
    }

    private static String sanitizeFilePart(String value)
    {
        return safe(value).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String truncate(String value, int max)
    {
        String text = safe(value);
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static Map<String, String> detail(String key, String value)
    {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(key, safe(value));
        return result;
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
        final long equipmentHash;
        final long xpHash;
        final List<PluginIdentity> activeKspPlugins;
        final Map<String, String> stateSignatures;
        final String fingerprint;

        RuntimeFrame(long timestampMs, String gameState, int x, int y, int plane, int animation,
                     String interacting, String microbotStatus, long inventoryHash,
                     long equipmentHash, long xpHash, List<PluginIdentity> activeKspPlugins,
                     Map<String, String> stateSignatures, String fingerprint)
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
            this.equipmentHash = equipmentHash;
            this.xpHash = xpHash;
            this.activeKspPlugins = new ArrayList<>(activeKspPlugins);
            this.stateSignatures = new LinkedHashMap<>(stateSignatures);
            this.fingerprint = fingerprint;
        }
    }

    static final class TimelineEvent
    {
        long timestampMs;
        String timestamp;
        String type;
        String pluginClass;
        String summary;
        Map<String, String> details;
    }

    static final class ActionRecord
    {
        String id;
        String pluginClass;
        String action;
        String target;
        String source;
        KspObservation.Expectation expectation;
        long startedAtMs;
        long deadlineMs;
        long completedAtMs;
        long baselineProgress;
        int baselineX;
        int baselineY;
        int baselineAnimation;
        long baselineInventoryHash;
        long baselineEquipmentHash;
        long baselineXpHash;
        long baselineWidgetGeneration;
        long baselineDialogueGeneration;
        long baselineVarbitGeneration;
        long baselineAnimationGeneration;
        String baselineStateSignature;
        String result;
        String failureReason;
        Map<String, String> metadata;
    }

    static final class PluginRuntime
    {
        final String pluginClass;
        String currentStateSignature = "";
        String previousStateSignature = "";
        long stateEnteredAtMs = System.currentTimeMillis();
        long progressAtStateEntry;
        long lastSemanticProgressAtMs;
        long lastTouchedAtMs;
        long intentionalWaitUntilMs;
        String waitReason = "";
        int ticksInState;
        final Deque<String> stateHistory = new ArrayDeque<>();
        PluginRuntime(String pluginClass) { this.pluginClass = pluginClass; }
    }

    static final class SceneEntity
    {
        String key;
        String kind;
        int id;
        String name;
        int x;
        int y;
        int plane;
        List<String> actions;
    }

    static final class Incident
    {
        String incidentId;
        String timestamp;
        String type;
        String failureKind;
        String reason;
        double confidence;
        double autoRepairThreshold;
        String targetPluginName;
        String targetPluginClass;
        String stateSignature;
        String microbotStatus;
        String repositoryUrl;
        String repositoryBranch;
        String lastSeenSourceRevision;
        String sourceLoaderBridge;
        String screenshot;
        RuntimeFrame runtimeFrame;
        List<RuntimeFrame> trace;
        List<String> recentLogs;
        ActionRecord failedAction;
        Map<String, Object> pluginState;
        Map<String, Object> environment;
        List<RepairHistoryRecord> similarRepairs;
    }

    static final class PendingRepair
    {
        String incidentId;
        String incidentType;
        String failureKind;
        String targetPluginClass;
        String stateSignature;
        String beforeSha;
        String commitSha;
        String repositoryPath;
        String agentSummary;
        long pushedAtMs;
        long loaderSeenAtMs;
        long validationStartProgress;
        boolean rollbackScheduled;
    }

    static final class RepairHistoryRecord
    {
        String timestamp;
        String incidentId;
        String pluginClass;
        String failureKind;
        String stateSignature;
        String beforeSha;
        String repairSha;
        String outcome;
        String agentSummary;
        double confidence;
        long validationProgressEvents;
        transient int similarityScore;
    }

    static final class CompilationResult
    {
        final boolean success;
        final String mechanism;
        CompilationResult(boolean success, String mechanism)
        {
            this.success = success;
            this.mechanism = mechanism;
        }
    }

    static final class ProcessResult
    {
        final int exitCode;
        ProcessResult(int exitCode) { this.exitCode = exitCode; }
    }

    static final class InferredExpectation
    {
        final KspObservation.Expectation expectation;
        final long timeoutMs;
        InferredExpectation(KspObservation.Expectation expectation, long timeoutMs)
        {
            this.expectation = expectation;
            this.timeoutMs = timeoutMs;
        }
    }
}
