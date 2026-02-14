package net.runelite.client.plugins.microbot.KSPAccountBuilder.skills;

import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;

public final class WoodcuttingSkillPlanner {
    private WoodcuttingSkillPlanner() {
    }

    public static String configure(ConfigManager configManager) {
        configManager.setConfiguration("KSPAutoWoodcutter", "mode", "CHOP_BANK");
        int woodcuttingLevel = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);

        String selected;
        if (woodcuttingLevel >= 60) {
            selected = "YEW";
        } else if (woodcuttingLevel >= 30) {
            selected = "WILLOW";
        } else if (woodcuttingLevel >= 15) {
            selected = "OAK";
        } else {
            selected = "TREE";
        }

        configManager.setConfiguration("KSPAutoWoodcutter", "tree", selected);
        return selected;
    }
}
