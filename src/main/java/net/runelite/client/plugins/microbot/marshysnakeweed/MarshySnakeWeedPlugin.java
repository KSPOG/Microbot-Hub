package net.runelite.client.plugins.microbot.marshysnakeweed;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
        name = PluginConstants.DEFAULT_PREFIX + "Marshy Snake Weed",
        description = "Searches marshy jungle vines for grimy snake weed and banks it.",
        tags = {"microbot", "snake", "weed", "herblore", "banking", "jungle potion"},
        authors = {"KSP"},
        version = MarshySnakeWeedPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class MarshySnakeWeedPlugin extends Plugin {
    public static final String version = "1.0.0";

    @Inject
    private MarshySnakeWeedConfig config;

    @Inject
    private MarshySnakeWeedScript script;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private MarshySnakeWeedOverlay overlay;

    @Provides
    MarshySnakeWeedConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MarshySnakeWeedConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }

        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();

        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
    }
}
