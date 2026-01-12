package net.runelite.client.plugins.microbot.ksplamps;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

@PluginDescriptor(
        name = "<html>[<font color=#b8f704>KSP</font>] Lampent",
        description = "Uses experience lamps",
        tags = {"exp", "microbot", "ksp"},
        version = KSPLampsPlugin.version,
        minClientVersion = "2.0.13",
        //enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KSPLampsPlugin extends Plugin {
    static final String version = "1.0.6";

    @Inject
    private ExperienceLampConfig config;

    @Inject
    private ExperienceLampScript script;

    @Provides
    ExperienceLampConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ExperienceLampConfig.class);
    }

    @Override
    protected void startUp() {
        script.run();
    }

    @Override
    protected void shutDown() {
        script.shutdown();
    }
}