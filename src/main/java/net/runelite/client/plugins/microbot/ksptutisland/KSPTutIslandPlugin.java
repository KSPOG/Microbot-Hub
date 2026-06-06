package net.runelite.client.plugins.microbot.ksptutisland;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = "<html>[<font color=#b8f704>KSP</font>] Tutorial Island",
        description = "Handles KSP Tutorial Island automation",
        tags = {"microbot", "ksp", "tutorial", "island"},
        authors = {"KSP"},
        version = KSPTutIslandPlugin.VERSION,
        minClientVersion = "2.0.13",
        enabledByDefault = false,
        isExternal = true
)
@Slf4j
public class KSPTutIslandPlugin extends Plugin
{
    public static final String VERSION = "0.0.1";

    @Inject
    private KSPTutIslandScript script;

    @Inject
    private KSPTutIslandConfig config;

    @Inject
    private KSPTutIslandOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ConfigManager configManager;

    @Provides
    KSPTutIslandConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KSPTutIslandConfig.class);
    }

    @Override
    protected void startUp()
    {
        log.info("Starting KSP Tutorial Island plugin");
        configureRequiredMicrobotOptions();
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        log.info("Stopping KSP Tutorial Island plugin");
        script.shutdown();
        overlayManager.remove(overlay);
    }

    private void configureRequiredMicrobotOptions()
    {
        configManager.setConfiguration("microbot", "disableLevelUpInterface", Boolean.FALSE);
        configManager.setConfiguration("microbot", "disableWorldSwitcherConfirmation", Boolean.FALSE);
    }
}
