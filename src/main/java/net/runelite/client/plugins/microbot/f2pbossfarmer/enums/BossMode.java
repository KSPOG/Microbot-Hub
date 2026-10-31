package net.runelite.client.plugins.microbot.f2pbossfarmer.enums;

public enum BossMode {
    AUTO("Auto - Obor & Bryophyta"),
    OBOR_ONLY("Obor only"),
    BRYOPHYTA_ONLY("Bryophyta only");

    private final String displayName;

    BossMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
