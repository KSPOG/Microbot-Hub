package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.loot;

import net.runelite.api.ItemID;

public final class Loot {
    private Loot() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final int[] DEFAULT_LOOT = {
            ItemID.COWHIDE,
            ItemID.GOBLIN_MAIL,
            ItemID.CHEFS_HAT,
            ItemID.AIR_TALISMAN,
            ItemID.CLUE_SCROLL_BEGINNER,
            ItemID.EARTH_TALISMAN,
            ItemID.GIANT_KEY,
            ItemID.STEEL_SCIMITAR,
            ItemID.STEEL_LONGSWORD,
            ItemID.IRON_ARROW,
            ItemID.STEEL_ARROW,
            ItemID.BRONZE_ARROW,
            ItemID.MIND_RUNE,
            ItemID.EARTH_RUNE,
            ItemID.BODY_RUNE,
            ItemID.CHAOS_RUNE,
            ItemID.NATURE_RUNE,
            ItemID.WATER_RUNE,
            ItemID.LAW_RUNE,
            ItemID.COSMIC_RUNE,
            ItemID.DEATH_RUNE,
            ItemID.LIMPWURT_ROOT,
            ItemID.BODY_TALISMAN,
            ItemID.UNCUT_SAPPHIRE,
            ItemID.UNCUT_EMERALD,
            ItemID.UNCUT_RUBY,
            ItemID.UNCUT_DIAMOND
    };
}
