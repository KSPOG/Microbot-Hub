package net.runelite.client.plugins.microbot.kspauburnvaleplankmaker;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.inject.Singleton;

@PluginDescriptor(
        name = "Fast Planker",
        description = "Private Fast Planker for Auburnvale with automatic GE restocking and travel.",
        authors = {"KSP"},
        version = KspAuburnvalePlankMakerPlugin.VERSION,
        minClientVersion = "2.0.13",
        tags = {"ksp", "planks", "sawmill", "auburnvale", "grand exchange"},
        enabledByDefault = false,
        isExternal = true
)
public class KspAuburnvalePlankMakerPlugin extends Plugin {
    private final KspAuburnvaleBreakService breaks = new KspAuburnvaleBreakService();

    public static final String VERSION = "0.4.3";

    @Inject
    private KspAuburnvalePlankMakerConfig config;

    @Inject
    private KspAuburnvalePlankMakerScript script;

    @Inject
    private KspAuburnvalePlankMakerOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Provides
    KspAuburnvalePlankMakerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KspAuburnvalePlankMakerConfig.class);
    }

    @Provides
    @Singleton
    KspAuburnvalePlankMakerScript provideScript(KspAuburnvalePlankMakerConfig config) {
        return new KspAuburnvalePlankMakerScript(config);
    }

    @Override
    protected void startUp() {
        breaks.start(config);
        overlayManager.add(overlay);
        script.run();
    }

    @Override
    protected void shutDown() {
        try {
            script.shutdown();
            overlayManager.remove(overlay);
        } finally {
            breaks.shutdown();
        }
    }

    public void prepareHotUnload() {
        shutDown();
    }
}
