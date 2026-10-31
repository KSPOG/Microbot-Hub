package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.events.ActorDeath;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Loader-safe bootstrap: only the standard ConfigManager is injected eagerly.
 * UI, overlay and script classes are resolved only when the user enables this plugin.
 * This makes loader failures and startup errors separable in client.log.
 */
@PluginDescriptor(
    name = PluginConstants.KSP + "Runite Miner Killer",
    description = "Magic Wilderness hunter: saved gear, manual Start/Stop, world hopping, live scene scans",
    tags = {"ksp", "runite", "wilderness", "pvp", "magic", "gear", "world hopping"},
    authors = {"KSP"},
    version = KspRuniteMinerKillerPlugin.VERSION,
    minClientVersion = "2.6.19",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
public class KspRuniteMinerKillerPlugin extends Plugin {
    public static final String VERSION = "0.1.32";

    @Inject private ConfigManager configManager;

    private KspRuniteMinerKillerScript script;
    private KspRuniteMinerKillerOverlay overlay;
    private OverlayManager overlayManager;
    private ClientToolbar toolbar;
    private KspRuniteMinerKillerGearStore gear;
    private KspRuniteMinerKillerPanel panel;
    private NavigationButton navigation;
    private volatile boolean running;

    public KspRuniteMinerKillerPlugin() { }

    // Must compile against ConfigManager.getConfig(Class<T extends Config>).
    // Its JVM return descriptor is Lnet/runelite/client/config/Config;
    // and NEVER Ljava/lang/Object; (mock-stub erasure error in older JARs).
    @Provides
    KspRuniteMinerKillerConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(KspRuniteMinerKillerConfig.class);
    }

    @Override
    protected synchronized void startUp() {
        running = false;
        System.out.println("[KSP Runite Miner Killer] v" + VERSION + " bootstrap: plugin enabled; initializing side panel");
        try {
            // Avoid eager Guice construction of custom classes during plugin-list discovery.
            overlayManager = Microbot.getInjector().getInstance(OverlayManager.class);
            toolbar = Microbot.getInjector().getInstance(ClientToolbar.class);
            ItemManager itemManager = Microbot.getInjector().getInstance(ItemManager.class);
            script = new KspRuniteMinerKillerScript();
            overlay = new KspRuniteMinerKillerOverlay(this);
            gear = new KspRuniteMinerKillerGearStore(configManager);
            panel = new KspRuniteMinerKillerPanel(gear, itemManager,
                    this::startAutomation, this::stopAutomation, this::isRunning);
            navigation = NavigationButton.builder()
                    .tooltip("KSP Runite Miner Killer")
                    .priority(9)
                    .icon(icon())
                    .panel(panel)
                    .build();
            toolbar.addNavigation(navigation);
            Microbot.status = "KSP Runite Miner Killer: ready - press Start in side panel";
            System.out.println("[KSP Runite Miner Killer] v" + VERSION + " sidebar registered");
        } catch (Throwable error) {
            System.err.println("[KSP Runite Miner Killer] Startup failed (plugin should remain discoverable)");
            error.printStackTrace(System.err);
            Microbot.status = "KSP Runite Miner Killer: startup error - see client.log";
            // Do not rethrow: allow the user to see and disable the plugin to collect diagnostics.
        }
    }

    private synchronized void startAutomation() {
        if (script == null || gear == null || overlayManager == null || overlay == null) {
            Microbot.status = "KSP Runite Miner Killer: initialization incomplete (see client.log)";
            return;
        }
        if (running && script.status().startsWith("STOPPED:")) stopAutomation();
        if (running) return;
        if (!Microbot.isLoggedIn()) {
            Microbot.status = "KSP Runite Miner Killer: log in first";
            return;
        }
        try {
            KspRuniteMinerKillerWorldHop.reset(gear);
            KspRuniteMinerKillerFeroxPrep.reset(gear);
            KspRuniteMinerKillerConfig config = configManager.getConfig(KspRuniteMinerKillerConfig.class);
            if (!script.start(config)) return;
            overlayManager.add(overlay);
            running = true;
            Microbot.status = "KSP Runite Miner Killer: running";
        } catch (Throwable error) {
            System.err.println("[KSP Runite Miner Killer] Start failed");
            error.printStackTrace(System.err);
            try { script.shutdown(); } catch (Throwable ignored) { }
            try { overlayManager.remove(overlay); } catch (Throwable ignored) { }
            running = false;
            Microbot.status = "KSP Runite Miner Killer: start failed - see client.log";
        }
    }

    private synchronized void stopAutomation() {
        try { if (script != null) script.shutdown(); }
        catch (Throwable error) { System.err.println("[KSP Runite Miner Killer] Stop error: " + error); }
        try { if (running && overlayManager != null && overlay != null) overlayManager.remove(overlay); }
        catch (Throwable error) { System.err.println("[KSP Runite Miner Killer] Overlay cleanup error: " + error); }
        running = false;
        Microbot.status = "KSP Runite Miner Killer: stopped";
    }

    private boolean isRunning() {
        return running && script != null && !script.status().startsWith("STOPPED:");
    }

    @Override
    protected synchronized void shutDown() {
        stopAutomation();
        if (navigation != null && toolbar != null) {
            try { toolbar.removeNavigation(navigation); }
            catch (Throwable error) { System.err.println("[KSP Runite Miner Killer] Sidebar cleanup error: " + error); }
        }
        navigation = null;
        panel = null;
        gear = null;
        overlay = null;
        script = null;
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        if (running && script != null && event != null) script.onActorDeath(event.getActor());
    }

    KspRuniteMinerKillerScript getScript() { return script; }

    public void prepareHotUnload() { shutDown(); }

    private static BufferedImage icon() {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(new Color(173, 132, 222)); g.fillOval(2, 1, 13, 13);
            g.setColor(new Color(255, 229, 166)); g.fillRect(7, 7, 4, 10);
            g.setColor(new Color(80, 60, 116)); g.drawOval(2, 1, 13, 13);
        } finally { g.dispose(); }
        return img;
    }
}
