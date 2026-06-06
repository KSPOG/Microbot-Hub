package net.runelite.client.plugins.microbot.sellerksp;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.Objects;

@PluginDescriptor(
        name = PluginConstants.KSP + "Bank Seller",
        description = "Grand Exchange selling helper",
        tags = {"microbot", "seller", "ge", "ksp"},
        authors = {"KSP"},
        version = "1.0.0",
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class SellerKspPlugin extends Plugin
{
    @Inject
    private SellerKspScript script;

    @Inject
    private SellerKspConfig config;

    @Inject
    private SellerKspOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Provides
    SellerKspConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(SellerKspConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        script.run(this);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"sellerksp".equals(event.getGroup()))
        {
            return;
        }

        if ("clearPersistentBlacklist".equals(event.getKey())
                && event.getNewValue() != null
                && !Objects.equals(event.getNewValue(), event.getOldValue()))
        {
            script.clearPersistentDeniedItems();
        }
    }
}
