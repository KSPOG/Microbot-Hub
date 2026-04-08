package net.runelite.client.plugins.microbot.kspaccountbuilder.mining.rocklevel;

public enum RockLevels {
    COPPER(1),
    TIN(1),
    CLAY(1),
    RUNE_ESSENCE(1),
    IRON_ORE(15),
    SILVER(20),
    COAL(30),
    GOLD(40);

    private final int level;

    RockLevels(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
