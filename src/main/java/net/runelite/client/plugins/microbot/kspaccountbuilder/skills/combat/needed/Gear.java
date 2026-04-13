package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.needed;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.gear.armour.Armour;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.gear.swords.Swords;

public final class Gear {
    private Gear() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final int MIN_TROUT_REQUIRED = 5;

    public static final int AMULET_OF_POWER = ItemID.AMULET_OF_POWER;

    public static final int[] FOOD_REQUIREMENTS = {
            ItemID.TROUT
    };

    /**
     * Any team cape is accepted, these are the standard wilderness team capes.
     */
    public static final String[] TEAM_CAPE_NAME_MATCHERS = {
            "Team-",
            "Team cape"
    };

    public static final String DEFAULT_TEAM_CAPE_NAME = "Team-1 cape";

    public static final int[] BEST_MELEE_WEAPONS_BY_LEVEL = {
            Swords.RUNE_SCIMITAR,
            Swords.ADAMANT_SCIMITAR,
            Swords.MITHRIL_SCIMITAR,
            Swords.BLACK_SCIMITAR,
            Swords.STEEL_SCIMITAR,
            Swords.IRON_SCIMITAR,
            Swords.BRONZE_SCIMITAR
    };

    public static final int[] BEST_MELEE_ARMOUR_BY_LEVEL = {
            Armour.RUNE_FULL_HELM,
            Armour.RUNE_PLATEBODY,
            Armour.RUNE_PLATELEGS,
            Armour.RUNE_KITESHIELD,
            Armour.ADAMANT_FULL_HELM,
            Armour.ADAMANT_PLATEBODY,
            Armour.ADAMANT_PLATELEGS,
            Armour.ADAMANT_KITESHIELD,
            Armour.MITHRIL_FULL_HELM,
            Armour.MITHRIL_PLATEBODY,
            Armour.MITHRIL_PLATELEGS,
            Armour.MITHRIL_KITESHIELD,
            Armour.BLACK_FULL_HELM,
            Armour.BLACK_PLATEBODY,
            Armour.BLACK_PLATELEGS,
            Armour.BLACK_KITESHIELD,
            Armour.IRON_FULL_HELM,
            Armour.IRON_PLATEBODY,
            Armour.IRON_PLATELEGS,
            Armour.IRON_KITESHIELD,
            Armour.BRONZE_FULL_HELM,
            Armour.BRONZE_PLATEBODY,
            Armour.BRONZE_PLATELEGS,
            Armour.BRONZE_KITESHIELD
    };
}
