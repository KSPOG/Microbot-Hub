package net.runelite.client.plugins.microbot.kspaccountbuilder.mining.pickulevel;

public enum PickaxeMLevel {
    BRONZE(1),
    IRON(1),
    STEEL(6),
    BLACK(11),
    MITHRIL(21),
    ADAMANT(31),
    RUNE(41);

    private final int level;

    PickaxeMLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
