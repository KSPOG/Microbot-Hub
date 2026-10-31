package net.runelite.client.plugins.microbot.f2pbossfarmer.enums;

public enum BossTarget {
    OBOR("Obor", "Hill Giant", "Giant key"),
    BRYOPHYTA("Bryophyta", "Moss giant", "Mossy key");

    private final String bossName;
    private final String giantName;
    private final String keyName;

    BossTarget(String bossName, String giantName, String keyName) {
        this.bossName = bossName;
        this.giantName = giantName;
        this.keyName = keyName;
    }

    public String getBossName() {
        return bossName;
    }

    public String getGiantName() {
        return giantName;
    }

    public String getKeyName() {
        return keyName;
    }

    public BossTarget opposite() {
        return this == OBOR ? BRYOPHYTA : OBOR;
    }

    @Override
    public String toString() {
        return bossName;
    }
}
