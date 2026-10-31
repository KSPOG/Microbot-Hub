package net.runelite.client.plugins.microbot.dashboardlink;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name = "Dashboard Link", description = "Connect account snapshots and plugin controls to your Account Console",
    tags = {"dashboard", "accounts", "mule"}, enabledByDefault = false, version = "1.0.0",minClientVersion = "2.6.19")
public class DashboardLinkPlugin extends Plugin {
    private final Client client;
    private final ConfigManager configManager;
    private final PluginManager pluginManager;
    private final DashboardLinkConfig config;
    private final OverlayManager overlayManager;
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private ScheduledExecutorService executor;
    private DashboardLinkOverlay overlay;
    private volatile boolean running;
    private volatile long generation;
    private volatile String connectionStatus = "Not connected";
    private volatile int pendingTransfers;
    // Access only on the client thread; never retain live item entities.
    private List<Map<String, Object>> bank = Collections.emptyList();
    private Long bankObservedAt;
    private long bankOwner;
    // Owned by the single network worker. Retained until the server acknowledges its receipt.
    private Map<String, Object> pendingResult;

    @Inject
    public DashboardLinkPlugin(Client client, ConfigManager configManager, PluginManager pluginManager,
        DashboardLinkConfig config, OverlayManager overlayManager) {
        this.client = client; this.configManager = configManager; this.pluginManager = pluginManager;
        this.config = config; this.overlayManager = overlayManager;
    }

    @Provides
    DashboardLinkConfig provideConfig(ConfigManager manager) { return manager.getConfig(DashboardLinkConfig.class); }

    @Override
    protected void startUp() {
        if (running) return;
        running = true;
        long session = ++generation;
        overlay = new DashboardLinkOverlay(this);
        overlayManager.add(overlay);
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "DashboardLink"); thread.setDaemon(true); return thread;
        });
        executor.scheduleWithFixedDelay(() -> sync(session), 0, 5, TimeUnit.SECONDS);
    }

    @Override
    protected void shutDown() {
        running = false;
        ++generation;
        if (executor != null) { executor.shutdownNow(); executor = null; }
        if (overlay != null) { overlayManager.remove(overlay); overlay = null; }
        connectionStatus = "Disconnected";
    }

    @Subscribe(priority = -1)
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() != InventoryID.BANK || event.getItemContainer() == null || client.getGameState() != GameState.LOGGED_IN) return;
        bankOwner = client.getAccountHash();
        bank = Rs2Bank.bankItems().stream().map(DashboardLinkPlugin::item).collect(Collectors.toList());
        bankObservedAt = System.currentTimeMillis();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            bank = Collections.emptyList(); bankObservedAt = null; bankOwner = 0;
        }
    }

    private void sync(long session) {
        if (!running || session != generation) return;
        try {
            URI endpoint = endpoint(config.url());
            String token = config.token().trim();
            if (!token.matches("[a-f0-9]{64}")) { connectionStatus = "Enter a connection key"; return; }
            Map<String, Object> snapshot = Microbot.getClientThread().runOnClientThreadOptional(this::snapshot)
                .orElseThrow(() -> new IllegalStateException("Client is busy"));
            List<Map<String, Object>> plugins = new ArrayList<>();
            SwingUtilities.invokeAndWait(() -> {
                for (Plugin plugin : pluginManager.getPlugins()) {
                    if (!controllable(plugin)) continue;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", plugin.getClass().getName());
                    row.put("name", plugin.getClass().getAnnotation(PluginDescriptor.class).name().replaceAll("<[^>]*>", ""));
                    row.put("active", pluginManager.isActive(plugin));
                    try { row.put("settings", DashboardSettings.describe(pluginManager.getPluginConfigProxy(plugin))); }
                    catch (ReflectiveOperationException | RuntimeException e) { row.put("settings", Collections.emptyList()); }
                    plugins.add(row);
                }
            });
            snapshot.put("plugins", plugins);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("snapshot", snapshot);
            payload.put("result", pendingResult);
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)));
            String siteAccessToken = config.siteAccessToken().trim();
            if (!siteAccessToken.isEmpty()) builder.header("OAI-Sites-Authorization", "Bearer " + siteAccessToken);
            HttpRequest request = builder.build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.body().length() > 65_536) throw new IllegalStateException("Response too large");
            if (response.statusCode() != 200) {
                connectionStatus = response.statusCode() == 401 ? "Connection key rejected" : response.statusCode() == 409 ? "Pair this character with a new key" : "Dashboard unavailable";
                return;
            }
            JsonObject reply = gson.fromJson(response.body(), JsonObject.class);
            if (!running || session != generation || !token.equals(config.token().trim()) || !endpoint.equals(endpoint(config.url()))) return;
            pendingResult = null;
            pendingTransfers = reply.get("pendingTransfers").getAsInt();
            connectionStatus = config.allowControl() ? "Connected · control enabled" : "Connected · read only";
            if (reply.has("command") && !reply.get("command").isJsonNull())
                pendingResult = execute(reply.getAsJsonObject("command"), session);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (Exception e) { connectionStatus = "Connection unavailable · retrying"; }
    }

    static URI endpoint(String address) {
        URI origin = URI.create(address.trim());
        if (!"https".equalsIgnoreCase(origin.getScheme()) || origin.getHost() == null || origin.getUserInfo() != null
            || origin.getQuery() != null || origin.getFragment() != null
            || (origin.getPath() != null && !origin.getPath().isEmpty() && !"/".equals(origin.getPath())))
            throw new IllegalArgumentException("Enter an HTTPS origin");
        return origin.resolve("/api/link");
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean loggedIn = client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null;
        if (loggedIn && bankOwner != client.getAccountHash()) { bank = Collections.emptyList(); bankObservedAt = null; }
        result.put("name", loggedIn ? client.getLocalPlayer().getName() : "");
        result.put("gameState", client.getGameState().name());
        result.put("world", client.getWorld());
        result.put("status", Microbot.status);
        result.put("paused", Microbot.pauseAllScripts.get());
        result.put("controlEnabled", config.allowControl());
        List<Map<String, Object>> skills = new ArrayList<>();
        int total = 0;
        if (loggedIn) for (Skill skill : Skill.values()) {
            if (skill == Skill.OVERALL) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            int level = client.getRealSkillLevel(skill); total += level;
            row.put("name", skill.getName()); row.put("level", level);
            row.put("boostedLevel", client.getBoostedSkillLevel(skill)); row.put("xp", client.getSkillExperience(skill));
            skills.add(row);
        }
        result.put("totalLevel", total); result.put("skills", skills);
        Map<String, Object> bankSnapshot = new LinkedHashMap<>();
        bankSnapshot.put("observedAt", bankObservedAt); bankSnapshot.put("items", new ArrayList<>(bank));
        result.put("bank", bankSnapshot);
        result.put("inventory", loggedIn ? Rs2Inventory.all().stream().map(DashboardLinkPlugin::item).collect(Collectors.toList()) : Collections.emptyList());
        result.put("equipment", loggedIn ? Rs2Equipment.all().map(DashboardLinkPlugin::item).collect(Collectors.toList()) : Collections.emptyList());
        return result;
    }

    private static Map<String, Object> item(Rs2ItemModel item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", item.getId()); result.put("name", item.getName()); result.put("quantity", item.getQuantity());
        return result;
    }

    private boolean controllable(Plugin plugin) {
        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        String name = plugin.getClass().getName();
        return plugin != this && descriptor != null && !descriptor.hidden()
            && name.startsWith("net.runelite.client.plugins.microbot.")
            && !name.contains(".agentserver.") && !name.endsWith(".MicrobotPlugin");
    }

    private Map<String, Object> execute(JsonObject command, long session) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", command.get("id").getAsString());
        result.put("state", "failed"); result.put("message", "Command rejected by client");
        try {
            if (!running || session != generation || !config.allowControl()) throw new IllegalStateException("Enable dashboard control in the client first");
            if (command.get("expiresAt").getAsLong() < System.currentTimeMillis()) throw new IllegalStateException("Command expired");
            String character = Microbot.getClientThread().runOnClientThreadOptional(() ->
                client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null
                    ? client.getLocalPlayer().getName().toLowerCase(Locale.ROOT) : "").orElse("");
            if (!character.equals(command.get("character").getAsString())) throw new IllegalStateException("Character changed or logged out");
            JsonObject payload = command.getAsJsonObject("payload");
            String kind = command.get("kind").getAsString();
            AtomicReference<Exception> failure = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    if (!running || session != generation || !config.allowControl() || command.get("expiresAt").getAsLong() < System.currentTimeMillis())
                        throw new IllegalStateException("Control disabled or command expired");
                    if ("pause".equals(kind)) { Microbot.pauseAllScripts.set(payload.get("paused").getAsBoolean()); return; }
                    Plugin plugin = pluginManager.getPlugins().stream().filter(this::controllable)
                        .filter(p -> p.getClass().getName().equals(payload.get("pluginId").getAsString())).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Plugin unavailable"));
                    if ("plugin".equals(kind)) {
                        boolean enabled = payload.get("enabled").getAsBoolean();
                        pluginManager.setPluginEnabled(plugin, enabled);
                        if (enabled && !pluginManager.isActive(plugin)) pluginManager.startPlugin(plugin);
                        if (!enabled && pluginManager.isActive(plugin)) pluginManager.stopPlugin(plugin);
                        if (pluginManager.isActive(plugin) != enabled) throw new IllegalStateException("Plugin did not reach requested state");
                    } else if ("setting".equals(kind)) {
                        DashboardSettings.update(pluginManager.getPluginConfigProxy(plugin), configManager,
                            payload.get("key").getAsString(), payload.get("value").getAsString());
                    } else throw new IllegalArgumentException("Unsupported command");
                } catch (Exception e) { failure.set(e); }
            });
            if (failure.get() != null) throw failure.get();
            result.put("state", "applied"); result.put("message", "Applied by client");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (Exception e) { result.put("message", "Not applied. Check local control permission, character, plugin, and value."); }
        return result;
    }

    String getConnectionStatus() { return connectionStatus; }
    int getPendingTransfers() { return pendingTransfers; }
}
