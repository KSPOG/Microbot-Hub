package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(KSPAccountBuilderConfig.configGroup)
public interface KSPAccountBuilderConfig extends Config {
    String configGroup = "KSPAccountBuilder";

    @ConfigSection(
            name = "Antiban",
            description = "Antiban behavior settings",
            position = 0
    )
    String antibanSection = "antiban";

    @ConfigSection(
            name = "Looting",
            description = "Looting and burying behavior settings",
            position = 1
    )
    String lootingSection = "looting";

    @ConfigItem(
            keyName = "enableAntiban",
            name = "Enable antiban",
            description = "Use universal antiban settings while account builder is running",
            position = 0,
            section = antibanSection
    )
    default boolean enableAntiban() {
        return true;
    }

    @ConfigItem(
            keyName = "actionCooldownChance",
            name = "Action cooldown chance",
            description = "Chance (0.0 - 1.0) to trigger action cooldown",
            position = 1,
            section = antibanSection
    )
    default double actionCooldownChance() {
        return 0.2;
    }

    @ConfigItem(
            keyName = "enableBoneBury",
            name = "Enable bone bury",
            description = "Bury bones while running supported tasks",
            position = 0,
            section = lootingSection
    )
    default boolean enableBoneBury() {
        return true;
    }

    @ConfigItem(
            keyName = "enableCoinLooting",
            name = "Enable coin looting",
            description = "Loot ground coins while running supported tasks",
            position = 1,
            section = lootingSection
    )
    default boolean enableCoinLooting() {
        return true;
    }
}
