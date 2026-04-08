package net.runelite.client.plugins.microbot.kspaccountbuilder;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.kspaccountbuilder.mining.script.MScript;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
        name = "<html>[<font color=#b8f704>KSP</font>] Account Builder",
        description = "Automates early account-building mining tasks.",
        tags = {"mining", "microbot", "ksp", "account", "builder"},
        version = KSPAccountBuilderPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KSPAccountBuilderPlugin extends Plugin {
    public static final String version = "1.0.0";

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KSPAccountBuilderOverlay overlay;

    @Inject
    private MScript miningScript;

    @Inject
    private KSPAccountBuilderConfig config;

    @Provides
    KSPAccountBuilderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KSPAccountBuilderConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(overlay);
        miningScript.run();
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        miningScript.shutdown();
    }
}
