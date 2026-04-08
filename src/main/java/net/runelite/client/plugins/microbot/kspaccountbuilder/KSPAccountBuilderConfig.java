package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("kspaccountbuilder")
@ConfigInformation("Start near a bank with pickaxes available. The script will mine based on level and bank when inventory is full.")
public interface KSPAccountBuilderConfig extends Config {
    @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "Quick setup steps for KSP Account Builder.",
            position = 0
    )
    default String guide() {
        return "1) Start near a bank. 2) Ensure pickaxes are in bank. 3) Enable plugin and let it handle mining + banking.";
    }

    @ConfigItem(
            keyName = "antiban",
            name = "Enable Antiban",
            description = "Toggle antiban behavior during mining.",
            position = 1
    )
    default boolean antiban() {
        return true;
    }
}
