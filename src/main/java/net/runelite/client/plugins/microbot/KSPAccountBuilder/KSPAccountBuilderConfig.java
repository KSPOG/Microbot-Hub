package net.runelite.client.plugins.microbot.KSPAccountBuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("KSPAccountBuilder")
public interface KSPAccountBuilderConfig extends Config {
    @ConfigSection(
            name = "Flow",
            description = "Order and timing behavior",
            position = 0
    )
    String flowSection = "flow";

    @ConfigSection(
            name = "Break handler",
            description = "Custom break timing settings",
            position = 1
    )
    String breakSection = "breaks";

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

    @ConfigItem(
            keyName = "minSwitchMinutes",
            name = "Min switch minutes",
            description = "Minimum minutes before switching tasks",
            position = 1,
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
            position = 2,
            section = flowSection
    )
    @Range(min = 1, max = 240)
    default int maxSwitchMinutes() {
        return 60;
    }

    @ConfigItem(
            keyName = "meleeSkill",
            name = "Melee skill",
            description = "Melee skill to train during MELEE stage",
            position = 3,
            section = flowSection
    )
    default KSPAccountBuilderMeleeSkill meleeSkill() {
        return KSPAccountBuilderMeleeSkill.ANY;
    }

    @ConfigItem(
            keyName = "attackTargetLevel",
            name = "Attack target level",
            description = "Target level for Attack (0 = any, 1 = skip)",
            position = 4,
            section = flowSection
    )
    @Range(min = 0, max = 99)
    default int attackTargetLevel() {
        return 0;
    }

    @ConfigItem(
            keyName = "strengthTargetLevel",
            name = "Strength target level",
            description = "Target level for Strength (0 = any, 1 = skip)",
            position = 5,
            section = flowSection
    )
    @Range(min = 0, max = 99)
    default int strengthTargetLevel() {
        return 0;
    }

    @ConfigItem(
            keyName = "defenceTargetLevel",
            name = "Defence target level",
            description = "Target level for Defence (0 = any, 1 = skip)",
            position = 6,
            section = flowSection
    )
    @Range(min = 0, max = 99)
    default int defenceTargetLevel() {
        return 0;
    }

    @ConfigItem(
            keyName = "buryBones",
            name = "Bury bones",
            description = "Automatically bury bones during melee training",
            position = 7,
            section = flowSection
    )
    default boolean buryBones() {
        return true;
    }

    @ConfigItem(
            keyName = "debugLockTaskSwitching",
            name = "[Debug] Lock current task",
            description = "If enabled, stage/task switching is disabled for testing the selected task",
            position = 8,
            section = flowSection
    )
    default boolean debugLockTaskSwitching() {
        return false;
    }
    @ConfigItem(
            keyName = "enableCustomBreaks",
            name = "Enable custom breaks",
            description = "Enable randomized custom breaks",
            position = 0,
            section = breakSection
    )
    default boolean enableCustomBreaks() {
        return true;
    }

    @ConfigItem(
            keyName = "minBreakIntervalMinutes",
            name = "Min break interval (minutes)",
            description = "Minimum minutes between breaks",
            position = 1,
            section = breakSection
    )
    @Range(min = 1, max = 240)
    default int minBreakIntervalMinutes() {
        return 6;
    }

    @ConfigItem(
            keyName = "maxBreakIntervalMinutes",
            name = "Max break interval (minutes)",
            description = "Maximum minutes between breaks",
            position = 2,
            section = breakSection
    )
    @Range(min = 1, max = 240)
    default int maxBreakIntervalMinutes() {
        return 12;
    }

    @ConfigItem(
            keyName = "minBreakDurationMinutes",
            name = "Min break duration (minutes)",
            description = "Minimum break duration in minutes",
            position = 3,
            section = breakSection
    )
    @Range(min = 1, max = 240)
    default int minBreakDurationMinutes() {
        return 1;
    }

    @ConfigItem(
            keyName = "maxBreakDurationMinutes",
            name = "Max break duration (minutes)",
            description = "Maximum break duration in minutes",
            position = 4,
            section = breakSection
    )
    @Range(min = 1, max = 240)
    default int maxBreakDurationMinutes() {
        return 3;
    }


}

}

