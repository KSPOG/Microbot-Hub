package net.runelite.client.plugins.microbot.kspaccountbuilder;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;

@PluginDescriptor(
    name = PluginConstants.KSP + "Account Builder",
    description = "Skeleton plugin for KSP account builder automation",
    tags = {"microbot", "ksp", "account", "builder"},
    authors = {"KSP"},
    version = KspAccountBuilderPlugin.VERSION,
    minClientVersion = "2.0.13",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KspAccountBuilderPlugin extends Plugin
{
    public static final String VERSION = "0.0.1";

    @Inject
    private KspAccountBuilderScript script;

    @Inject
    private KspAccountBuilderConfig config;

    @Provides
    KspAccountBuilderConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspAccountBuilderConfig.class);
    }

    @Override
    protected void startUp()
    {
        log.info("Starting KSP Account Builder plugin");
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        log.info("Stopping KSP Account Builder plugin");
        script.shutdown();
    }
}
