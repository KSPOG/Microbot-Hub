package net.runelite.client.plugins.microbot.kspmelee;

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
        name = PluginConstants.KSP + "Melee",
        description = "Basic melee combat framework plugin.",
        tags = {"melee", "combat", "ksp"},
        authors = {"KSP"},
        version = KSPMeleePlugin.version,
        minClientVersion = "1.9.8",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KSPMeleePlugin extends Plugin {

    static final String version = "1.0.5";

    @Inject
    private KSPMeleeConfig config;

    @Inject
    private KSPMeleeScript script;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KSPMeleeOverlay overlay;

    @Provides
    KSPMeleeConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KSPMeleeConfig.class);
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
        overlayManager.remove(overlay);
    }
}
