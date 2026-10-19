package net.runelite.client.plugins.microbot.f2pcooker;

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
        name = PluginConstants.DEFAULT_PREFIX + "F2P Cooker",
        description = "Cooks F2P fish at the Edgeville stove (3078, 3493, 0) using OSRS wiki level progression",
        tags = {"cooking", "f2p", "edgeville", "microbot"},
        version = F2pCookerPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class F2pCookerPlugin extends Plugin {
    public static final String version = "1.0.5";

    @Inject
    private F2pCookerConfig config;

    @Inject
    private F2pCookerScript script;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private F2pCookerOverlay overlay;

    @Provides
    F2pCookerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(F2pCookerConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
