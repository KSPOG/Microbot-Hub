package net.runelite.client.plugins.microbot.KSPAccountBuilder.skills;

import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.KSPAccountBuilder.F2PFishOption;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.Arrays;

public final class F2PFishingSkillPlanner {
    private F2PFishingSkillPlanner() {
    }

    public static F2PFishOption configure(ConfigManager configManager) {
        configManager.setConfiguration("AutoFishing", "useBank", true);
        int fishingLevel = Microbot.getClient().getRealSkillLevel(Skill.FISHING);
        F2PFishOption selected = Arrays.stream(F2PFishOption.values())
                .filter(option -> fishingLevel >= option.getRequiredLevel())
                .reduce((first, second) -> second)
                .orElse(F2PFishOption.SHRIMP);

        configManager.setConfiguration("AutoFishing", "fishToCatch", selected.getFishConfigKey());
        return selected;
    }
}
