package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.needed;

import net.runelite.api.ItemID;

/**
 * Inventory requirements checked before travelling to woodcutting areas.
 */
public final class ItemReqs {

    private ItemReqs() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Minimum number of usable axes required in inventory/equipment.
     */
    public static final int MIN_AXE_COUNT = 1;

    /**
     * A usable axe must be present before travelling to chop trees.
     */
    public static final int[] ACCEPTED_AXE_IDS = {
            ItemID.BRONZE_AXE,
            ItemID.STEEL_AXE,
            ItemID.BLACK_AXE,
            ItemID.MITHRIL_AXE,
            ItemID.ADAMANT_AXE,
            ItemID.RUNE_AXE
    };
}
