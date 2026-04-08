package net.runelite.client.plugins.microbot.kspaccountbuilder.mining.script;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.mining.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.mining.rocklevels.RockLevels;

public final class MiningScript {
    private MiningScript() {
    }

    public static WorldArea getRecommendedArea() {
        int miningLevel = Microbot.getClientThread().invoke(() -> Microbot.getClient().getRealSkillLevel(Skill.MINING));
        if (miningLevel < RockLevels.IRON_ORE_LEVEL) {
            return Areas.COPPER_TIN_AREA;
        }
        if (miningLevel < RockLevels.COAL_LEVEL) {
            return Areas.IRON_AREA;
        }
        if (miningLevel < RockLevels.GOLD_LEVEL) {
            return Areas.COAL_AREA;
        }
        return Areas.GOLD_ORE_AREA;
    }

    public static String getBalancedStarterOre(int copperCount, int tinCount) {
        return copperCount <= tinCount ? "Copper ore" : "Tin ore";
    }

    public static boolean shouldStayOnStarterOres() {
        int miningLevel = Microbot.getClientThread().invoke(() -> Microbot.getClient().getRealSkillLevel(Skill.MINING));
        return miningLevel < RockLevels.IRON_ORE_LEVEL;
    }
}
