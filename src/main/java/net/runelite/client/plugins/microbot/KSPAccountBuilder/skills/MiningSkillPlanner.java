package net.runelite.client.plugins.microbot.KSPAccountBuilder.skills;

import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;

public final class MiningSkillPlanner {
    private MiningSkillPlanner() {
    }

    public static String configure(ConfigManager configManager) {
        configManager.setConfiguration("KSPAutoMiner", "mode", "MINE_BANK");
        int miningLevel = Microbot.getClient().getRealSkillLevel(Skill.MINING);
        int combatLevel = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getCombatLevel()
                : 3;

        String selected;
        if (miningLevel < 15) {
            selected = "COPPER_TIN";
        } else if (miningLevel < 30) {
            selected = "IRON";
        } else if (miningLevel < 40) {
            selected = "COAL";
        } else if (miningLevel < 55) {
            selected = combatLevel < 64 ? "COAL" : "GOLD";
        } else if (miningLevel < 70) {
            selected = "MITHRIL";
        } else {
            selected = "ADAMANT";
        }

        configManager.setConfiguration("KSPAutoMiner", "rock", selected);
        return selected;
    }
}
