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

    @ConfigSection(
            name = "Task Rotation",
            description = "Task switching behavior",
            position = 2
    )
    String taskSection = "taskRotation";

    @ConfigSection(
            name = "Break Handler",
            description = "Custom run/break cycle behavior",
            position = 3
    )
    String breakSection = "breakHandler";

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

    @ConfigItem(
            keyName = "taskSwitchMinutes",
            name = "Switch task every (minutes)",
            description = "How often to rotate between available account-builder tasks",
            position = 0,
            section = taskSection
    )
    default int taskSwitchMinutes() {
        return 20;
    }

    @ConfigItem(
            keyName = "enableCustomBreakHandler",
            name = "Enable custom break handler",
            description = "Run for random minutes then logout break for random minutes",
            position = 0,
            section = breakSection
    )
    default boolean enableCustomBreakHandler() {
        return false;
    }

    @ConfigItem(
            keyName = "minRunMinutes",
            name = "Run min (minutes)",
            description = "Minimum run duration before triggering break",
            position = 1,
            section = breakSection
    )
    default int minRunMinutes() {
        return 30;
    }

    @ConfigItem(
            keyName = "maxRunMinutes",
            name = "Run max (minutes)",
            description = "Maximum run duration before triggering break",
            position = 2,
            section = breakSection
    )
    default int maxRunMinutes() {
        return 60;
    }

    @ConfigItem(
            keyName = "minBreakMinutes",
            name = "Break min (minutes)",
            description = "Minimum logout break duration",
            position = 3,
            section = breakSection
    )
    default int minBreakMinutes() {
        return 30;
    }

    @ConfigItem(
            keyName = "maxBreakMinutes",
            name = "Break max (minutes)",
            description = "Maximum logout break duration",
            position = 4,
            section = breakSection
    )
    default int maxBreakMinutes() {
        return 60;
    }
}