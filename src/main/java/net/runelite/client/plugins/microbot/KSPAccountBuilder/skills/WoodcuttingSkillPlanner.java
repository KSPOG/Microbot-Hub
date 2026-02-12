package net.runelite.client.plugins.microbot.KSPAccountBuilder.skills;

import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.KSPAutoWoodcutter.KSPAutoWoodcutterMode;
import net.runelite.client.plugins.microbot.KSPAutoWoodcutter.KSPAutoWoodcutterTree;
import net.runelite.client.plugins.microbot.Microbot;

public final class WoodcuttingSkillPlanner {
    private WoodcuttingSkillPlanner() {
    }

    public static KSPAutoWoodcutterTree configure(ConfigManager configManager) {
        configManager.setConfiguration("KSPAutoWoodcutter", "mode", KSPAutoWoodcutterMode.CHOP_BANK);
        int woodcuttingLevel = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);

        KSPAutoWoodcutterTree selected;
        if (woodcuttingLevel >= 60) {
            selected = KSPAutoWoodcutterTree.YEW;
        } else if (woodcuttingLevel >= 30) {
            selected = KSPAutoWoodcutterTree.WILLOW;
        } else if (woodcuttingLevel >= 15) {
            selected = KSPAutoWoodcutterTree.OAK;
        } else {
            selected = KSPAutoWoodcutterTree.TREE;
        }

        configManager.setConfiguration("KSPAutoWoodcutter", "tree", selected);
        return selected;
    }
}