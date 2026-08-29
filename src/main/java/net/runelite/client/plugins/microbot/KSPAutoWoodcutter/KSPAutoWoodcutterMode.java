package net.runelite.client.plugins.microbot.KSPAutoWoodcutter;

public enum KSPAutoWoodcutterMode {
    CHOP_DROP("Chop & Drop"),
    CHOP_BURN("Chop & Burn"),
    CHOP_BANK("Chop & Bank"),
    PROGRESSIVE_BANK("Progressive Mode & Bank"),
    PROGRESSIVE_DROP("Progressive Mode & Drop");

    private final String displayName;

    KSPAutoWoodcutterMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public boolean isBankingMode() {
        return this == CHOP_BANK || this == PROGRESSIVE_BANK;
    }

    public boolean isProgressiveMode() {
        return this == PROGRESSIVE_BANK || this == PROGRESSIVE_DROP;
    }
}
