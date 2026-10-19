package net.runelite.client.plugins.microbot.f2pfishing;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("f2pfishing")
public interface F2pFishingConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General settings",
            position = 0
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = "fish",
            name = "Fish",
            description = "Select which F2P fish to catch",
            position = 0,
            section = generalSection
    )
    default F2pFishingFish fish() {
        return F2pFishingFish.SHRIMP_AND_ANCHOVIES;
    }

    @ConfigItem(
            keyName = "mode",
            name = "Mode",
            description = "Choose whether to bank or drop fish",
            position = 1,
            section = generalSection
    )
    default F2pFishingMode mode() {
        return F2pFishingMode.FISH_DROP;
    }

    @ConfigItem(
            keyName = "enableAntiban",
            name = "Enable Antiban",
            description = "Use fishing antiban settings",
            position = 3,
            section = generalSection
    )
    default boolean enableAntiban() {
        return true;
    }
}
