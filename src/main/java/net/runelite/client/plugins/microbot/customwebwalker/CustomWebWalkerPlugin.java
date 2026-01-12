package net.runelite.client.plugins.microbot.customwebwalker;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

@PluginDescriptor(
        name = PluginConstants.DEFAULT_PREFIX + "Custom WebWalker",
        description = "Example plugin that uses CustomWebWalker to navigate to a target tile.",
        tags = {"walking", "navigation"},
        authors = {"KSP"},
        version = CustomWebWalkerPlugin.version,
        minClientVersion = "1.9.8",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class CustomWebWalkerPlugin extends Plugin {

    static final String version = "1.0.0";

    @Inject
    private CustomWebWalkerScript script;

    @Provides
    CustomWebWalkerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(CustomWebWalkerConfig.class);
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