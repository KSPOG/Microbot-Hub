package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("kspaccountbuilder")
public interface KSPAccountBuilderConfig extends Config {

    @ConfigItem(
            keyName = "instructions",
            name = "Instructions",
            description = "Will progress account as if it where a main",
            position = 0
    )
    default String instructions() {
        return "Provide at least 20K to have this account progress to main build.";
    }
}
