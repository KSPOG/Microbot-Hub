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
            keyName = "minBreakDurationSeconds",
            name = "Min break duration (seconds)",
            description = "Minimum break duration in seconds",
            position = 3,
            section = breakSection
    )
    @Range(min = 5, max = 600)
    default int minBreakDurationSeconds() {
        return 20;
    }

    @ConfigItem(
            keyName = "maxBreakDurationSeconds",
            name = "Max break duration (seconds)",
            description = "Maximum break duration in seconds",
            position = 4,
            section = breakSection
    )
    @Range(min = 5, max = 600)
    default int maxBreakDurationSeconds() {
        return 60;
    }

}
