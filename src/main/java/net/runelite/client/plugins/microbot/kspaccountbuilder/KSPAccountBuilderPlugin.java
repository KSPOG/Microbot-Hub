package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
        name = PluginConstants.KSP + "Account Builder",
        description = "KSP account progression plugin scaffold.",
        tags = {"ksp", "account", "builder"},
        authors = {"KSP"},
        version = KSPAccountBuilderPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KSPAccountBuilderPlugin extends Plugin {
    static final String version = "0.0.6";

    @Inject
    private KSPAccountBuilderScript script;

    @Override
    protected void startUp() throws AWTException {
        log.info("Starting KSP Account Builder plugin.");
        script.run();
    }

    @Override
    protected void shutDown() {
        log.info("Stopping KSP Account Builder plugin.");
        script.shutdown();
    }
}
