/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sell;

public enum SellList {
    LOGS("Logs"),
    OAK_LOGS("Oak logs"),
    YEW_LOGS("Yew logs"),
    COWHIDE("Cowhide"),
    LEATHER_GLOVES("Leather gloves"),
    SMALL_FISHING_NET("Small fishing net"),
    FISHING_ROD("Fishing rod"),
    FISHING_BAIT("Fishing bait"),
    SILVER_BAR("Silver bar"),
    BRONZE_DAGGER("Bronze dagger"),
    BRONZE_SCIMITAR("Bronze scimitar"),
    BRONZE_WARHAMMER("Bronze warhammer"),
    BRONZE_PLATEBODY("Bronze platebody"),
    IRON_SCIMITAR("Iron scimitar"),
    IRON_WARHAMMER("Iron warhammer"),
    IRON_PLATEBODY("Iron platebody"),
    STEEL_SCIMITAR("Steel scimitar"),
    STEEL_LONGSWORD("Steel longsword"),
    STEEL_WARHAMMER("Steel warhammer"),
    STEEL_PLATEBODY("Steel platebody"),
    LIMPWURT_ROOT("Limpwurt root"),
    EARTH_TALISMAN("Earth talisman"),
    BODY_TALISMAN("Body talisman"),
    MITHRIL_SWORD("Mithril sword"),
    STEEL_KITESHIELD("Steel kiteshield"),
    BLACK_SQ_SHIELD("Black sq shield"),
    SPINACH_ROLL("Spinach roll");

    private final String displayName;

    public String getDisplayName() {
        return this.displayName;
    }

    private SellList(String displayName) {
        this.displayName = displayName;
    }
}
