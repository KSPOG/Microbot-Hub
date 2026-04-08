package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("KSPAccountBuilder")
@ConfigInformation("Basic KSP account builder plugin. Start near a bank and enable the plugin.")
public interface KSPAccountBuilderConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General plugin settings",
            position = 0
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = "enableAntiban",
            name = "Enable Antiban",
            description = "Enable antiban behavior.",
            section = generalSection,
            position = 0
    )
    default boolean enableAntiban() {
        return true;
    }
}