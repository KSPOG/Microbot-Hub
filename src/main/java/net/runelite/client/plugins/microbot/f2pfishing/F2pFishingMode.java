package net.runelite.client.plugins.microbot.f2pfishing;

public enum F2pFishingMode {
    FISH_DROP("Fish + Drop"),
    FISH_BANK("Fish + Bank");

    private final String label;

    F2pFishingMode(String label) {
        this.label = label;
    }

    public boolean isBankingMode() {
        return this == F2pFishingMode.FISH_BANK;
    }

    @Override
    public String toString() {
        return label;
    }
}
