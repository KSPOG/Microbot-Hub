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
}
