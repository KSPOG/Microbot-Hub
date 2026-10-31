package net.runelite.client.plugins.microbot.f2pbossfarmer;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.KSP + "F2P Boss Farmer",
        description = "Farms Giant/Mossy keys and automatically fights Obor and Bryophyta.",
        tags = {"microbot", "f2p", "boss", "obor", "bryophyta", "giant key", "mossy key"},
        version = F2PBossFarmerPlugin.version,
        minClientVersion = "2.6.19",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class F2PBossFarmerPlugin extends Plugin {

    public static final String version = "0.0.23";

    @Inject
    private F2PBossFarmerConfig config;

    @Inject
    private F2PBossFarmerScript script;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private F2PBossFarmerOverlay overlay;

    @Provides
    F2PBossFarmerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(F2PBossFarmerConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
