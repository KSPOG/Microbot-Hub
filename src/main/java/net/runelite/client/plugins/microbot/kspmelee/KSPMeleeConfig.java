package net.runelite.client.plugins.microbot.kspmelee;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("kspmelee")
public interface KSPMeleeConfig extends Config {

    @ConfigItem(
            keyName = "instructions",
            name = "Instructions",
            description = "How to use this plugin",
            position = 0
    )
    default String instructions() {
        return "1. Start the plugin from anywhere in F2P.\n"
                + "2. If Attack is below 5, it buys a Bronze/Iron scimitar at the GE.\n"
                + "3. If Defence is below 5, it buys Bronze/Iron platebody, full helm, platelegs, and kiteshield.\n"
                + "4. If you do not have an Amulet of power in bank/inventory, it buys one.";
    }
}
