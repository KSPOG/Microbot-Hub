package net.runelite.client.plugins.microbot.KSPAccountBuilder;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.KSPAutoMiner.KSPAutoMinerConfig;
import net.runelite.client.plugins.microbot.KSPAutoWoodcutter.KSPAutoWoodcutterConfig;
import net.runelite.client.plugins.microbot.autofishing.AutoFishingConfig;
import net.runelite.client.plugins.microbot.gecooker.GECookerConfig;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
        name = PluginConstants.KSP + "Account Builder",
        description = "Builds accounts by training Mining and Woodcutting",
        tags = {"account", "mining", "woodcutting", "ksp"},
        version = KSPAccountBuilderPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KSPAccountBuilderPlugin extends Plugin {
    public static final String version = "0.3.3";

    @Inject
    private KSPAccountBuilderConfig config;

    @Inject
    private KSPAutoMinerConfig minerConfig;

    @Inject
    private KSPAutoWoodcutterConfig woodcutterConfig;

    @Inject
    private AutoFishingConfig fishingConfig;

    @Inject
    private GECookerConfig cookerConfig;

    @Provides
    KSPAccountBuilderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KSPAccountBuilderConfig.class);
    }

    @Provides
    KSPAutoMinerConfig provideMinerConfig(ConfigManager configManager) {
        return configManager.getConfig(KSPAutoMinerConfig.class);
    }

    @Provides
    KSPAutoWoodcutterConfig provideWoodcutterConfig(ConfigManager configManager) {
        return configManager.getConfig(KSPAutoWoodcutterConfig.class);
    }

    @Provides
    AutoFishingConfig provideFishingConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoFishingConfig.class);
    }

    @Provides
    GECookerConfig provideCookerConfig(ConfigManager configManager) {
        return configManager.getConfig(GECookerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KSPAccountBuilderOverlay overlay;

    @Inject
    private KSPAccountBuilderScript script;

    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(overlay);
        script.run(config, minerConfig, woodcutterConfig, fishingConfig, cookerConfig);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
