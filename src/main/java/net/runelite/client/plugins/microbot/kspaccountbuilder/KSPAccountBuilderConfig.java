package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("kspaccountbuilder")
@ConfigInformation("<h2>KSP Account Builder</h2>" +
        "Start the plugin on the account you want to progress.<br />" +
        "This rebuild restores the base plugin shell and mining support files.<br />" +
        "Enable antiban if you want natural mouse and antiban settings applied.")
public interface KSPAccountBuilderConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General account builder settings",
            position = 0
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = "enableAntiban",
            name = "Enable Antiban/Natural Mouse",
            description = "Applies antiban and natural mouse settings while the builder is running",
            position = 0,
            section = generalSection
    )
    default boolean enableAntiban() {
        return true;
    }
}
