package net.runelite.client.plugins.microbot.kspautorepair;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.KSP + "Auto Repair Agent",
        description = "Autonomously observes KSP plugins, diagnoses failures and sends validated source fixes back through KSP Source Loader",
        tags = {"ksp", "agent", "repair", "debug", "autonomous", "development"},
        version = KspAutoRepairPlugin.VERSION,
        cardUrl = "",
        iconUrl = "",
        minClientVersion = "2.6.19",
        enabledByDefault = false,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspAutoRepairPlugin extends Plugin
{
    public static final String VERSION = "0.1.0";

    @Inject
    private Client client;

    @Inject
    private PluginManager pluginManager;

    @Inject
    private KspAutoRepairConfig config;

    private KspAutoRepairService service;

    @Provides
    KspAutoRepairConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspAutoRepairConfig.class);
    }

    @Override
    protected void startUp()
    {
        service = new KspAutoRepairService(client, pluginManager, config);
        service.start();
    }

    @Override
    protected void shutDown()
    {
        if (service != null)
        {
            service.stop();
            service = null;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        KspAutoRepairService current = service;
        if (current != null)
        {
            current.onGameTick();
        }
    }
}
