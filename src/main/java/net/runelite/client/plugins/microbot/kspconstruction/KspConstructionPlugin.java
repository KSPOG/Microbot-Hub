package net.runelite.client.plugins.microbot.kspconstruction;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = "KSP Construction",
        description = "Trains Construction using oak larders or mahogany tables with servant or self-banking resupply.",
        tags = {"construction", "oak", "mahogany", "servant", "bank", "microbot"},
        authors = {"KSP"},
        version = KspConstructionPlugin.VERSION,
        minClientVersion = "2.6.16",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspConstructionPlugin extends Plugin
{
    public static final String VERSION = "1.0.0";

    @Inject
    private KspConstructionConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KspConstructionOverlay overlay;

    private final KspConstructionScript script = new KspConstructionScript();

    @Provides
    KspConstructionConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspConstructionConfig.class);
    }

    @Override
    protected void startUp()
    {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    public KspConstructionConfig getConfig()
    {
        return config;
    }

    public KspConstructionScript getScript()
    {
        return script;
    }
}
