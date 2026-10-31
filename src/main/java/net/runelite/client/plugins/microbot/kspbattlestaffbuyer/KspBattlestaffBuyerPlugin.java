package net.runelite.client.plugins.microbot.kspbattlestaffbuyer;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = "<html>[<font color=#b8f704>KSP</font>] Battlestaff Buyer",
        description = "Buys Battlestaves from Zaff, banks them, world hops on empty stock, and sells accumulated stock at the GE.",
        tags = {"ksp", "battlestaff", "zaff", "varrock", "shop", "world hop", "grand exchange", "money making"},
        authors = {"KSP"},
        version = KspBattlestaffBuyerPlugin.VERSION,
        minClientVersion = "2.6.19",
        enabledByDefault = false,
        isExternal = true
)
public class KspBattlestaffBuyerPlugin extends Plugin
{
    public static final String VERSION = "0.0.10";

    @Inject
    private KspBattlestaffBuyerScript script;

    @Inject
    private KspBattlestaffBuyerOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KspBattlestaffBuyerConfig config;

    @Provides
    KspBattlestaffBuyerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspBattlestaffBuyerConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    KspBattlestaffBuyerScript getScript()
    {
        return script;
    }
}
