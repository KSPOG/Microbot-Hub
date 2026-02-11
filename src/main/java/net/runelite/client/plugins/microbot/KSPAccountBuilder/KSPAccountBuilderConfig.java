package net.runelite.client.plugins.microbot.KSPAccountBuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("KSPAccountBuilder")
public interface KSPAccountBuilderConfig extends Config {
    @ConfigSection(
            name = "Targets",
            description = "Level targets for the account builder",
            position = 0
    )
    String targetsSection = "targets";

    @ConfigSection(
            name = "Flow",
            description = "Order and completion behavior",
            position = 1
    )
    String flowSection = "flow";

    @ConfigItem(
            keyName = "startSkill",
            name = "Start skill",
            description = "Which skill to train first",
            position = 0,
            section = flowSection
    )
    default KSPAccountBuilderStartSkill startSkill() {
        return KSPAccountBuilderStartSkill.MINING;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "targetMiningLevel",
            name = "Target mining level",
            description = "Stop mining once this level is reached",
            position = 1,
            section = targetsSection
    )
    default int targetMiningLevel() {
        return 30;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "targetWoodcuttingLevel",
            name = "Target woodcutting level",
            description = "Stop woodcutting once this level is reached",
            position = 2,
            section = targetsSection
    )
    default int targetWoodcuttingLevel() {
        return 30;
    }

    @ConfigItem(
            keyName = "stopWhenComplete",
            name = "Stop when targets met",
            description = "Disable the builder after both targets are met",
            position = 1,
            section = flowSection
    )
    default boolean stopWhenComplete() {
        return true;
    }

    @ConfigItem(
            keyName = "minSwitchMinutes",
            name = "Min switch minutes",
            description = "Minimum minutes before switching tasks",
            position = 2,
            section = flowSection
    )
    @Range(min = 1, max = 240)
    default int minSwitchMinutes() {
        return 30;
    }

    @ConfigItem(
            keyName = "maxSwitchMinutes",
            name = "Max switch minutes",
            description = "Maximum minutes before switching tasks",
            position = 3,
            section = flowSection
    )
    @Range(min = 1, max = 240)
    default int maxSwitchMinutes() {
        return 60;
    }
}
