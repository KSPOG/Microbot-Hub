package net.runelite.client.plugins.microbot.KSPAccountBuilder.skills;

import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;

import net.runelite.client.plugins.microbot.KSPAutoMiner.KSPAutoMinerMode;
import net.runelite.client.plugins.microbot.KSPAutoMiner.KSPAutoMinerRock;

import net.runelite.client.plugins.microbot.Microbot;

public final class MiningSkillPlanner {
    private MiningSkillPlanner() {
    }


    public static String configure(ConfigManager configManager) {
        configManager.setConfiguration("KSPAutoMiner", "mode", "MINE_BANK");

    public static KSPAutoMinerRock configure(ConfigManager configManager) {
        configManager.setConfiguration("KSPAutoMiner", "mode", KSPAutoMinerMode.MINE_BANK);

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

        KSPAutoMinerRock selected;
        if (miningLevel < 15) {
            selected = KSPAutoMinerRock.COPPER_TIN;
        } else if (miningLevel < 30) {
            selected = KSPAutoMinerRock.IRON;
        } else if (miningLevel < 40) {
            selected = KSPAutoMinerRock.COAL;
        } else if (miningLevel < 55) {
            selected = combatLevel < 64 ? KSPAutoMinerRock.COAL : KSPAutoMinerRock.GOLD;
        } else if (miningLevel < 70) {
            selected = KSPAutoMinerRock.MITHRIL;
        } else {
            selected = KSPAutoMinerRock.ADAMANT;

        }

        configManager.setConfiguration("KSPAutoMiner", "rock", selected);
        return selected;
    }

}


}

}


