package net.runelite.client.plugins.microbot.creatorbridge;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;

import javax.inject.Inject;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
        name = PluginConstants.KSP + "Creator Bridge",
        description = "Local telemetry bridge for the creator dashboard",
        tags = {"microbot", "ksp", "creator", "bridge", "telemetry"},
        authors = {"KSP"},
        version = CreatorBridgePlugin.VERSION,
        minClientVersion = "2.0.13",
        enabledByDefault = false,
        isExternal = PluginConstants.IS_EXTERNAL
)
@SuppressWarnings("unused")
public class CreatorBridgePlugin extends Plugin
{
    public static final String VERSION = "1.2.2";

    private static final int DEFAULT_PORT = 8765;
    private static final int MAX_GROUND_ITEMS_SENT = 30;

    private ScheduledExecutorService heartbeatExecutor;
    private ExecutorService httpExecutor;
    private CreatorBridgeClient bridgeClient;

    private long pid;
    private String launchToken;
    private String profileName;

    private final Map<Integer, Integer> lastInventoryCounts = new HashMap<>();
    private final Map<String, String> lastQuestStates = new HashMap<>();
    private final Deque<CreatorBridgePayload.TelemetryEvent> recentItemEvents = new ConcurrentLinkedDeque<>();
    private final Deque<CreatorBridgePayload.TelemetryEvent> recentQuestEvents = new ConcurrentLinkedDeque<>();

    private WorldPoint lastStationaryLocation;
    private long lastMovementMillis;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConfigManager configManager;

    @Inject
    private CreatorBridgeConfig config;

    @Provides
    CreatorBridgeConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(CreatorBridgeConfig.class);
    }

    @Override
    protected void startUp()
    {
        this.pid = getCurrentPid();
        this.launchToken = resolveLaunchToken();
        this.profileName = resolveProfileName();
        this.lastMovementMillis = System.currentTimeMillis();

        this.bridgeClient = new CreatorBridgeClient(resolvePort(), pid, launchToken);

        this.httpExecutor = Executors.newSingleThreadExecutor(r ->
        {
            Thread thread = new Thread(r, "creator-bridge-http");
            thread.setDaemon(true);
            return thread;
        });

        int intervalSeconds = Math.max(1, config.heartbeatIntervalSeconds());
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r ->
        {
            Thread thread = new Thread(r, "creator-bridge-heartbeat");
            thread.setDaemon(true);
            return thread;
        });

        this.heartbeatExecutor.scheduleWithFixedDelay(
                this::safeQueueHeartbeat,
                1,
                intervalSeconds,
                TimeUnit.SECONDS
        );

        log.info("[CreatorBridge] Started. pid={}, profile={}, endpoint={}",
                pid,
                profileName,
                bridgeClient.getHeartbeatUrl());
    }

    @Override
    protected void shutDown()
    {
        safeQueueShutdownHeartbeat();

        if (heartbeatExecutor != null)
        {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }

        if (httpExecutor != null)
        {
            httpExecutor.shutdown();
            httpExecutor = null;
        }

        log.info("[CreatorBridge] Stopped. pid={}, profile={}", pid, profileName);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event == null || event.getMessage() == null)
        {
            return;
        }

        String cleanMessage = stripTags(event.getMessage());
        String lower = cleanMessage.toLowerCase(Locale.ROOT);

        if (config.trackDropMessages() && isLikelyItemMessage(lower))
        {
            addEvent(recentItemEvents, new CreatorBridgePayload.TelemetryEvent(
                    "DROP_MESSAGE",
                    "",
                    -1,
                    0,
                    cleanMessage,
                    System.currentTimeMillis()
            ));
        }

        if (lower.contains("quest complete") || lower.contains("you have completed"))
        {
            addEvent(recentQuestEvents, new CreatorBridgePayload.TelemetryEvent(
                    "QUEST_MESSAGE",
                    "",
                    -1,
                    0,
                    cleanMessage,
                    System.currentTimeMillis()
            ));
        }
    }

    private void safeQueueHeartbeat()
    {
        try
        {
            if (!config.enabled() || bridgeClient == null)
            {
                return;
            }

            clientThread.invokeLater(() ->
            {
                try
                {
                    CreatorBridgePayload payload = buildPayloadOnClientThread(false);
                    sendHeartbeatAsync(payload);
                }
                catch (Exception ex)
                {
                    logHeartbeatError("Heartbeat snapshot failed", ex);
                }
            });
        }
        catch (Exception ex)
        {
            logHeartbeatError("Heartbeat queue failed", ex);
        }
    }

    private void safeQueueShutdownHeartbeat()
    {
        try
        {
            if (bridgeClient == null)
            {
                return;
            }

            clientThread.invokeLater(() ->
            {
                try
                {
                    CreatorBridgePayload payload = buildPayloadOnClientThread(true);
                    sendHeartbeatAsync(payload);
                }
                catch (Exception ex)
                {
                    log.debug("[CreatorBridge] Shutdown snapshot failed: {}", ex.getMessage());
                }
            });
        }
        catch (Exception ex)
        {
            log.debug("[CreatorBridge] Shutdown heartbeat queue failed: {}", ex.getMessage());
        }
    }

    private void sendHeartbeatAsync(CreatorBridgePayload payload)
    {
        ExecutorService executor = httpExecutor;

        if (executor == null || executor.isShutdown())
        {
            return;
        }

        executor.execute(() ->
        {
            CreatorBridgeClient.CreatorBridgeResponse response = bridgeClient.sendHeartbeat(payload);

            if (!response.isSuccess())
            {
                if (config.debugLogging())
                {
                    log.warn("[CreatorBridge] Heartbeat failed: {}", response.getErrorMessage());
                }
                return;
            }

            if (config.debugLogging())
            {
                log.debug("[CreatorBridge] Heartbeat sent. pid={}, status={}", pid, response.getStatusCode());
            }
        });
    }

    private CreatorBridgePayload buildPayloadOnClientThread(boolean shuttingDown)
    {
        CreatorBridgePayload payload = new CreatorBridgePayload();
        long now = System.currentTimeMillis();

        payload.setPid(pid);
        payload.setProfileName(resolveProfileName());
        payload.setPluginVersion(VERSION);
        payload.setShuttingDown(shuttingDown);
        payload.setUpdatedAt(now);

        GameState gameState = client.getGameState();
        Player localPlayer = client.getLocalPlayer();

        payload.setGameState(gameState == null ? "UNKNOWN" : gameState.name());
        payload.setWorld(client.getWorld());
        payload.setLoggedIn(gameState == GameState.LOGGED_IN);

        if (localPlayer != null)
        {
            if (config.sendPlayerName())
            {
                payload.setPlayerName(localPlayer.getName());
            }

            WorldPoint worldPoint = localPlayer.getWorldLocation();

            if (config.sendLocation() && worldPoint != null)
            {
                payload.setX(worldPoint.getX());
                payload.setY(worldPoint.getY());
                payload.setPlane(worldPoint.getPlane());
            }

            updateStationaryState(payload, worldPoint, now);
        }

        String inferredTask = inferCurrentTask(localPlayer, gameState);
        payload.setScriptName("Microbot");
        payload.setCurrentTask(inferredTask);
        payload.setScriptStatus(inferredTask);

        if (config.sendLevels())
        {
            Map<String, Integer> levels = readLevels();
            payload.setLevels(levels);
            payload.setTotalLevel(calculateTotalLevel(levels));
        }

        if (config.sendXp())
        {
            payload.setXp(readXp());
        }

        if (config.sendQuestStates())
        {
            readQuestStates(payload, now);
        }

        if (config.trackInventoryGains())
        {
            detectInventoryGains(now);
        }

        payload.setRecentItemEvents(snapshotDeque(recentItemEvents));
        payload.setRecentQuestEvents(snapshotDeque(recentQuestEvents));

        if (config.trackNearbyGroundItems())
        {
            payload.setNearbyGroundItems(readNearbyGroundItems());
        }

        return payload;
    }

    private Map<String, Integer> readLevels()
    {
        Map<String, Integer> levels = new LinkedHashMap<>();

        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL)
            {
                continue;
            }

            try
            {
                levels.put(skill.name(), client.getRealSkillLevel(skill));
            }
            catch (Exception ignored)
            {
                // Ignore unstable login/logout transitions.
            }
        }

        return levels;
    }

    private Map<String, Integer> readXp()
    {
        Map<String, Integer> xp = new LinkedHashMap<>();

        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL)
            {
                continue;
            }

            try
            {
                xp.put(skill.name(), client.getSkillExperience(skill));
            }
            catch (Exception ignored)
            {
                // Ignore unstable login/logout transitions.
            }
        }

        return xp;
    }

    private int calculateTotalLevel(Map<String, Integer> levels)
    {
        int total = 0;

        if (levels == null)
        {
            return total;
        }

        for (Integer level : levels.values())
        {
            if (level != null && level > 0)
            {
                total += level;
            }
        }

        return total;
    }

    /**
     * Reads quest states by reflection so this plugin compiles on Microbot/RuneLite forks
     * where Quest/QuestState moved between net.runelite.api and net.runelite.api.quests.
     */
    private void readQuestStates(CreatorBridgePayload payload, long now)
    {
        Map<String, String> currentStates = new LinkedHashMap<>();
        int completed = 0;
        int total = 0;

        Object[] quests = resolveQuestConstants();

        if (quests.length == 0)
        {
            payload.setQuestCount(0);
            payload.setCompletedQuestCount(0);
            return;
        }

        for (Object quest : quests)
        {
            try
            {
                Object state = invokeQuestState(quest);
                String questName = invokeQuestName(quest);
                String stateName = enumName(state, "UNKNOWN");

                currentStates.put(questName, stateName);
                total++;

                boolean finished = "FINISHED".equalsIgnoreCase(stateName);

                if (finished)
                {
                    completed++;
                }

                String previous = lastQuestStates.put(questName, stateName);
                if (previous != null && !Objects.equals(previous, stateName) && finished)
                {
                    addEvent(recentQuestEvents, new CreatorBridgePayload.TelemetryEvent(
                            "QUEST_COMPLETED",
                            questName,
                            -1,
                            1,
                            previous + " -> " + stateName,
                            now
                    ));
                }
            }
            catch (Exception ignored)
            {
                // Some quest helpers may fail while the client is not fully logged in.
            }
        }

        payload.setQuestCount(total);
        payload.setCompletedQuestCount(completed);

        if (config.sendFullQuestStateMap())
        {
            payload.setQuestStates(currentStates);
        }
    }

    private Object[] resolveQuestConstants()
    {
        String[] classNames =
                {
                        "net.runelite.api.Quest",
                        "net.runelite.api.quests.Quest"
                };

        for (String className : classNames)
        {
            try
            {
                Class<?> questClass = Class.forName(className);

                if (questClass.isEnum())
                {
                    Object[] constants = questClass.getEnumConstants();
                    return constants == null ? new Object[0] : constants;
                }
            }
            catch (Exception ignored)
            {
                // Try the next known RuneLite/Microbot package location.
            }
        }

        return new Object[0];
    }

    private Object invokeQuestState(Object quest) throws Exception
    {
        Method getState = quest.getClass().getMethod("getState", Client.class);
        return getState.invoke(quest, client);
    }

    private String invokeQuestName(Object quest)
    {
        try
        {
            Method getName = quest.getClass().getMethod("getName");
            Object value = getName.invoke(quest);
            if (value != null)
            {
                return String.valueOf(value);
            }
        }
        catch (Exception ignored)
        {
            // Fallback below.
        }

        return enumName(quest, "UNKNOWN_QUEST");
    }

    private String enumName(Object value, String fallback)
    {
        if (value == null)
        {
            return fallback;
        }

        if (value instanceof Enum<?>)
        {
            return ((Enum<?>) value).name();
        }

        return String.valueOf(value);
    }

    private void detectInventoryGains(long now)
    {
        Map<Integer, Integer> current = readInventoryCounts();

        if (!lastInventoryCounts.isEmpty())
        {
            for (Map.Entry<Integer, Integer> entry : current.entrySet())
            {
                int itemId = entry.getKey();
                int currentQty = entry.getValue();
                int previousQty = lastInventoryCounts.getOrDefault(itemId, 0);

                if (currentQty > previousQty)
                {
                    int gained = currentQty - previousQty;
                    addEvent(recentItemEvents, new CreatorBridgePayload.TelemetryEvent(
                            "INVENTORY_GAIN",
                            getItemName(itemId),
                            itemId,
                            gained,
                            "Inventory count increased from " + previousQty + " to " + currentQty,
                            now
                    ));
                }
            }
        }

        lastInventoryCounts.clear();
        lastInventoryCounts.putAll(current);
    }

    private Map<Integer, Integer> readInventoryCounts()
    {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);

        if (inventory == null || inventory.getItems() == null)
        {
            return Collections.emptyMap();
        }

        Map<Integer, Integer> counts = new HashMap<>();

        for (Item item : inventory.getItems())
        {
            if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
            {
                continue;
            }

            counts.merge(item.getId(), item.getQuantity(), Integer::sum);
        }

        return counts;
    }

    private List<CreatorBridgePayload.GroundItemInfo> readNearbyGroundItems()
    {
        List<CreatorBridgePayload.GroundItemInfo> result = new ArrayList<>();

        try
        {
            int radius = Math.max(1, config.nearbyGroundItemRadius());
            int minValue = Math.max(0, config.groundItemMinGeValue());

            List<Rs2TileItemModel> items = Microbot.getRs2TileItemCache()
                    .query()
                    .within(radius)
                    .where(Rs2TileItemModel::isLootAble)
                    .where(item -> item.getTotalGeValue() >= minValue)
                    .toList();

            if (items == null)
            {
                return result;
            }

            for (Rs2TileItemModel item : items)
            {
                if (item == null)
                {
                    continue;
                }

                CreatorBridgePayload.GroundItemInfo info = new CreatorBridgePayload.GroundItemInfo();
                info.setName(item.getName());
                info.setId(item.getId());
                info.setQuantity(item.getQuantity());
                info.setTotalValue(item.getTotalValue());
                info.setTotalGeValue(item.getTotalGeValue());
                info.setOwned(item.isOwned());
                info.setLootable(item.isLootAble());

                WorldPoint location = item.getWorldLocation();
                if (location != null)
                {
                    info.setX(location.getX());
                    info.setY(location.getY());
                    info.setPlane(location.getPlane());
                }

                result.add(info);

                if (result.size() >= MAX_GROUND_ITEMS_SENT)
                {
                    break;
                }
            }
        }
        catch (Exception ex)
        {
            if (config.debugLogging())
            {
                log.debug("[CreatorBridge] Failed to query nearby ground items", ex);
            }
        }

        return result;
    }

    private void updateStationaryState(CreatorBridgePayload payload, WorldPoint currentLocation, long now)
    {
        if (currentLocation == null)
        {
            return;
        }

        if (lastStationaryLocation == null || !lastStationaryLocation.equals(currentLocation))
        {
            lastStationaryLocation = currentLocation;
            lastMovementMillis = now;
        }

        int stationarySeconds = (int) Math.max(0, (now - lastMovementMillis) / 1000L);
        boolean tooLong = stationarySeconds >= Math.max(5, config.stationaryThresholdSeconds());

        payload.setStationarySeconds(stationarySeconds);
        payload.setStationaryTooLong(tooLong);
        payload.setStationaryX(lastStationaryLocation.getX());
        payload.setStationaryY(lastStationaryLocation.getY());
        payload.setStationaryPlane(lastStationaryLocation.getPlane());
    }

    private String inferCurrentTask(Player localPlayer, GameState gameState)
    {
        if (gameState != GameState.LOGGED_IN)
        {
            return gameState == null ? "Not logged in" : gameState.name();
        }

        if (localPlayer == null)
        {
            return "Waiting for player";
        }

        try
        {
            if (client.getWidget(WidgetInfo.BANK_CONTAINER) != null)
            {
                return "Banking";
            }
        }
        catch (Exception ignored)
        {
        }

        try
        {
            if (client.getWidget(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER) != null)
            {
                return "Using Grand Exchange";
            }
        }
        catch (Exception ignored)
        {
        }

        if (localPlayer.getInteracting() != null)
        {
            return "Interacting with target";
        }

        if (localPlayer.getAnimation() != -1)
        {
            return "Animating";
        }

        if (localPlayer.getPoseAnimation() != localPlayer.getIdlePoseAnimation())
        {
            return "Moving";
        }

        if (lastStationaryLocation != null)
        {
            int stationarySeconds = (int) Math.max(0, (System.currentTimeMillis() - lastMovementMillis) / 1000L);
            if (stationarySeconds >= Math.max(5, config.stationaryThresholdSeconds()))
            {
                return "Stationary too long";
            }
        }

        return "Idle";
    }

    private String resolveProfileName()
    {
        String propertyProfile = System.getProperty("ksp.creator.profile");
        if (propertyProfile != null && !propertyProfile.isBlank())
        {
            return propertyProfile.trim();
        }

        String activeProfile = getActiveRuneLiteProfileName();
        if (activeProfile != null && !activeProfile.isBlank())
        {
            return activeProfile.trim();
        }

        String configuredProfile = config.profileName();
        if (configuredProfile != null && !configuredProfile.isBlank())
        {
            return configuredProfile.trim();
        }

        return "unknown-profile";
    }

    private String getActiveRuneLiteProfileName()
    {
        try
        {
            Object profile = configManager.getProfile();
            if (profile == null)
            {
                return "";
            }

            for (String methodName : new String[]{"getName", "getDisplayName", "getProfileKey"})
            {
                try
                {
                    Method method = profile.getClass().getMethod(methodName);
                    Object value = method.invoke(profile);
                    if (value != null && !String.valueOf(value).isBlank())
                    {
                        return String.valueOf(value).trim();
                    }
                }
                catch (NoSuchMethodException ignored)
                {
                    // Try next known profile method name.
                }
            }

            return String.valueOf(profile);
        }
        catch (Exception ex)
        {
            if (config != null && config.debugLogging())
            {
                log.debug("[CreatorBridge] Could not read active RuneLite profile", ex);
            }

            return "";
        }
    }

    private int resolvePort()
    {
        String propertyPort = System.getProperty("ksp.creator.port");
        if (propertyPort != null && !propertyPort.isBlank())
        {
            Integer parsed = parsePort(propertyPort);
            if (parsed != null)
            {
                return parsed;
            }
        }

        Integer configured = parsePort(config.creatorPort());
        return configured == null ? DEFAULT_PORT : configured;
    }

    private Integer parsePort(String rawPort)
    {
        if (rawPort == null || rawPort.isBlank())
        {
            return null;
        }

        try
        {
            int parsed = Integer.parseInt(rawPort.trim().replace(",", ""));
            if (parsed > 0 && parsed <= 65535)
            {
                return parsed;
            }
        }
        catch (NumberFormatException ignored)
        {
        }

        return null;
    }

    private String resolveLaunchToken()
    {
        String propertyToken = System.getProperty("ksp.creator.launchToken");
        if (propertyToken != null && !propertyToken.isBlank())
        {
            return propertyToken.trim();
        }

        String configuredToken = config.launchToken();
        if (configuredToken != null && !configuredToken.isBlank())
        {
            return configuredToken.trim();
        }

        return "";
    }

    private long getCurrentPid()
    {
        try
        {
            return ProcessHandle.current().pid();
        }
        catch (Exception ignored)
        {
            return getCurrentPidFallback();
        }
    }

    private long getCurrentPidFallback()
    {
        try
        {
            String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
            if (runtimeName != null && runtimeName.contains("@"))
            {
                return Long.parseLong(runtimeName.substring(0, runtimeName.indexOf('@')));
            }
        }
        catch (Exception ignored)
        {
        }

        return -1L;
    }

    private String getItemName(int itemId)
    {
        try
        {
            String name = client.getItemDefinition(itemId).getName();
            return name == null ? "Item " + itemId : stripTags(name);
        }
        catch (Exception ignored)
        {
            return "Item " + itemId;
        }
    }

    private boolean isLikelyItemMessage(String lowerMessage)
    {
        return lowerMessage.contains("you receive")
                || lowerMessage.contains("you get")
                || lowerMessage.contains("you pick up")
                || lowerMessage.contains("you obtain")
                || lowerMessage.contains("you find")
                || lowerMessage.contains("your loot")
                || lowerMessage.contains("drop:")
                || lowerMessage.contains("rare drop")
                || lowerMessage.contains("valuable drop");
    }

    private String stripTags(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replaceAll("<[^>]*>", "").replace('\u00A0', ' ').trim();
    }

    private void addEvent(Deque<CreatorBridgePayload.TelemetryEvent> deque, CreatorBridgePayload.TelemetryEvent event)
    {
        if (deque == null || event == null)
        {
            return;
        }

        deque.addFirst(event);
        trimDeque(deque);
    }

    private void trimDeque(Deque<CreatorBridgePayload.TelemetryEvent> deque)
    {
        int max = Math.max(1, config == null ? 25 : config.maxRecentEvents());
        while (deque.size() > max)
        {
            deque.pollLast();
        }
    }

    private List<CreatorBridgePayload.TelemetryEvent> snapshotDeque(Deque<CreatorBridgePayload.TelemetryEvent> deque)
    {
        if (deque == null || deque.isEmpty())
        {
            return Collections.emptyList();
        }

        trimDeque(deque);
        return new ArrayList<>(deque);
    }

    private void logHeartbeatError(String message, Exception ex)
    {
        if (config != null && config.debugLogging())
        {
            log.warn("[CreatorBridge] {}", message, ex);
        }
        else
        {
            log.warn("[CreatorBridge] {}: {}", message, ex.getMessage());
        }
    }
}
