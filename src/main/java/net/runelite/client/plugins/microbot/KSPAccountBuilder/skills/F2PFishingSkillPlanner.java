package net.runelite.client.plugins.microbot.KSPAccountBuilder.skills;

import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.KSPAccountBuilder.F2PFishOption;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public final class F2PFishingSkillPlanner {
    private F2PFishingSkillPlanner() {
    }

    public static F2PFishOption configure(ConfigManager configManager, Random random) {
        configManager.setConfiguration("AutoFishing", "useBank", true);
        int fishingLevel = Microbot.getClient().getRealSkillLevel(Skill.FISHING);
        List<F2PFishOption> availableFish = Arrays.stream(F2PFishOption.values())
                .filter(option -> fishingLevel >= option.getRequiredLevel())
                .collect(Collectors.toList());

        F2PFishOption selected = availableFish.isEmpty()
                ? F2PFishOption.SHRIMP
                : availableFish.get(random.nextInt(availableFish.size()));

        configManager.setConfiguration("AutoFishing", "fishToCatch", selected.getFish());
        return selected;
    }
}
