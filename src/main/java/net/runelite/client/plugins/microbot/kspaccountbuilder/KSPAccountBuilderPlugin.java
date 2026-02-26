package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.extern.slf4j.Slf4j;
import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
        name = PluginConstants.KSP + "Account Builder",
        description = "KSP account progression plugin scaffold.",
        tags = {"ksp", "account", "builder"},
        authors = {"KSP"},
        version = KSPAccountBuilderPlugin.VERSION,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KSPAccountBuilderPlugin extends Plugin {





    public static final String VERSION = "0.0.65";










    @Inject
    private KSPAccountBuilderScript script;

    @Inject
    private KSPAccountBuilderConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KSPAccountBuilderOverlay overlay;


    @Provides
    KSPAccountBuilderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KSPAccountBuilderConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        log.info("Starting KSP Account Builder plugin.");
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        log.info("Stopping KSP Account Builder plugin.");
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
