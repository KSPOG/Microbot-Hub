package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.needed;

import net.runelite.api.ItemID;

/**
 * Inventory requirements checked before travelling to woodcutting areas.
 */
public final class ItemReqs {

    private ItemReqs() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final int BRONZE_AXE = ItemID.BRONZE_AXE;
    public static final int STEEL_AXE = ItemID.STEEL_AXE;
    public static final int BLACK_AXE = ItemID.BLACK_AXE;
    public static final int MITHRIL_AXE = ItemID.MITHRIL_AXE;
    public static final int ADAMANT_AXE = ItemID.ADAMANT_AXE;
    public static final int RUNE_AXE = ItemID.RUNE_AXE;

    /**
     * Minimum number of usable axes required in inventory/equipment.
     */
    public static final int MIN_AXE_COUNT = 1;

    /**
     * A usable axe must be present before travelling to chop trees.
     */
    public static final int[] ACCEPTED_AXE_IDS = {
            BRONZE_AXE,
            STEEL_AXE,
            BLACK_AXE,
            MITHRIL_AXE,
            ADAMANT_AXE,
            RUNE_AXE
    };
}