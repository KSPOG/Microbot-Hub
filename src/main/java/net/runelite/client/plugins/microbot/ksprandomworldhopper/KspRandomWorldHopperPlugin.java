package net.runelite.client.plugins.microbot.ksprandomworldhopper;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "KSP Random World Hopper",
        description = "Randomly hops worlds after a configurable random number of minutes",
        tags = {"ksp", "world", "hopper", "random", "microbot"},
        version = KspRandomWorldHopperPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspRandomWorldHopperPlugin extends Plugin
{
    public static final String version = "1.0.0";

    @Inject
    private KspRandomWorldHopperConfig config;

    @Inject
    private KspRandomWorldHopperScript script;

    @Provides
    KspRandomWorldHopperConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspRandomWorldHopperConfig.class);
    }

    @Override
    protected void startUp() throws AWTException
    {
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
    }
}
