package net.runelite.client.plugins.microbot.ksplamps;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(ExperienceLampConfig.configGroup)
public interface ExperienceLampConfig extends Config {
    String configGroup = "lampent";

    @ConfigSection(
            name = "General",
            description = "General settings",
            position = 0
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = "targetSkill",
            name = "Lamp skill",
            description = "Choose the skill to receive lamp experience.",
            position = 0,
            section = generalSection
    )
    default LampSkillOption targetSkill() {
        return LampSkillOption.LOWEST;
    }
}