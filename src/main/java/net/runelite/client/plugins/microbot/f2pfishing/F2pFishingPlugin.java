package net.runelite.client.plugins.microbot.f2pfishing;

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
        name = PluginConstants.DEFAULT_PREFIX + "F2P Fishing",
        description = "F2P fishing with banking, dropping, and GE restocking",
        tags = {"fishing", "f2p", "microbot"},
        version = F2pFishingPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class F2pFishingPlugin extends Plugin {
    public static final String version = "1.1.3";

    @Inject
    private F2pFishingConfig config;

    @Provides
    F2pFishingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(F2pFishingConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private F2pFishingOverlay overlay;

    @Inject
    private F2pFishingScript script;

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
