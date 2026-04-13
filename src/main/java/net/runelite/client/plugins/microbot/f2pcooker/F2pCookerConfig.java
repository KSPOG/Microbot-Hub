package net.runelite.client.plugins.microbot.f2pcooker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("f2pcooker")
public interface F2pCookerConfig extends Config {
    @ConfigSection(
            name = "Cooking",
            description = "F2P cooker settings",
            position = 0
    )
    String cookingSection = "cooking";

    @ConfigItem(
            keyName = "useBestFoodForLevel",
            name = "Use best food for level",
            description = "Automatically picks the highest F2P food your Cooking level can cook and that exists in bank",
            position = 0,
            section = cookingSection
    )
    default boolean useBestFoodForLevel() {
        return true;
    }

    @ConfigItem(
            keyName = "fallbackFood",
            name = "Fallback food",
            description = "Food used when auto mode is disabled",
            position = 1,
            section = cookingSection
    )
    default F2pCookerFood fallbackFood() {
        return F2pCookerFood.SHRIMP;
    }

    @ConfigItem(
            keyName = "enableAntiban",
            name = "Enable Antiban",
            description = "Use cooking antiban settings",
            position = 2,
            section = cookingSection
    )
    default boolean enableAntiban() {
        return true;
    }
}
