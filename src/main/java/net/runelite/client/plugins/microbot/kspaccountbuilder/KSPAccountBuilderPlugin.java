package net.runelite.client.plugins.microbot.kspaccountbuilder;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
        name = PluginConstants.KSP + "Account Builder",
        description = "Skeleton plugin for automated account progression workflows.",
        tags = {"ksp", "account", "builder"},
        authors = {"KSP"},
        version = KSPAccountBuilderPlugin.version,
        minClientVersion = "1.9.8",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KSPAccountBuilderPlugin extends Plugin {


    static final String version = "0.0.1";

    @Inject
    private KSPAccountBuilderConfig config;

    @Inject
    private KSPAccountBuilderScript script;

    @Provides
    KSPAccountBuilderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KSPAccountBuilderConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        log.info("Starting KSP Account Builder skeleton.");
        script.run(config);
    }

    @Override
    protected void shutDown() {
        log.info("Stopping KSP Account Builder skeleton.");
        script.shutdown();
    }
}
