package net.runelite.client.plugins.microbot.kspaccountbuilder;

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
        name = PluginConstants.KSP + "Account Builder",
        description = "KSP account builder base plugin.",
        tags = {"ksp", "account", "builder", "microbot"},
        authors = {"KSP"},
        version = "1.0.0",
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
    private KSPAccountBuilderScript script;

    @Inject
    private KSPAccountBuilderConfig config;

    @Provides
    @SuppressWarnings("unused")
    KSPAccountBuilderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KSPAccountBuilderConfig.class);
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
