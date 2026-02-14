package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("kspaccountbuilder")
public interface KSPAccountBuilderConfig extends Config {

    @ConfigItem(
            keyName = "instructions",
            name = "Instructions",
            description = "High-level usage instructions for the plugin skeleton",
            position = 0
    )
    default String instructions() {
        return "KSP Account Builder skeleton is active. Add tasks and progression logic in KSPAccountBuilderScript.";
    }
}
