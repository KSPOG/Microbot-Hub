package net.runelite.client.plugins.microbot.kspaccountbuilder;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

import net.runelite.client.plugins.microbot.kspaccountbuilder.mining.script.MScript;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;


import java.awt.AWTException;

@PluginDescriptor(
        name = "<html>[<font color=#b8f704>KSP</font>] Account Builder",
        description = "Automates early account-building mining tasks.",
        tags = {"mining", "microbot", "ksp", "account", "builder"},
        version = KSPAccountBuilderPlugin.version,
        minClientVersion = "2.0.13",



@PluginDescriptor(
        name = PluginConstants.KSP + "Account Builder",
        description = "Automates early account-building mining tasks.",
        tags = {"ksp", "account", "builder", "mining"},
        authors = {"KSP"},
        version = "1.0.0",

        version = KSPAccountBuilderPlugin.version,

        minClientVersion = "1.9.6",

        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KSPAccountBuilderPlugin extends Plugin {
    public static final String version = "1.0.0";

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KSPAccountBuilderOverlay overlay;

    @Inject

    private KSPAccountBuilderScript accountBuilderScript;

    private MScript miningScript;


    @Inject
    private KSPAccountBuilderConfig config;

    @Provides
    KSPAccountBuilderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KSPAccountBuilderConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {

        overlayManager.add(overlay);
        accountBuilderScript.run(config);


    protected void startUp() {

        overlayManager.add(overlay);
        miningScript.run();

    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);

        accountBuilderScript.shutdown();

        miningScript.shutdown();

    }
}
