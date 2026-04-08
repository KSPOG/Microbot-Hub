package net.runelite.client.plugins.microbot.kspaccountbuilder.mining.pickelevel;

public enum PickaxeELevel {
    BRONZE(1),
    IRON(1),
    STEEL(5),
    BLACK(10),
    MITRHIL(20),
    ADAMANT(30),
    RUNE(40);

    private final int level;

    PickaxeELevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
