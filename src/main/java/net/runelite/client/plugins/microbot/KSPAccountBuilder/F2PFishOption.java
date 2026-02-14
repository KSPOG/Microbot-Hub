package net.runelite.client.plugins.microbot.KSPAccountBuilder;

import java.util.Arrays;
import java.util.List;

public enum F2PFishOption {
    SHRIMP("SHRIMP_AND_ANCHOVIES", 1, false),
    SARDINE("SARDINE", 5, false, new SupplyOrder("Fishing bait")),
    HERRING("HERRING", 10, false, new SupplyOrder("Fishing bait")),
    TROUT_SALMON("TROUT_AND_SALMON", 20, false, new SupplyOrder("Feather")),
    PIKE("PIKE", 25, false, new SupplyOrder("Fishing bait")),
    TUNA_SWORDFISH("TUNA_AND_SWORDFISH", 35, true),
    LOBSTER("LOBSTER", 40, true);

    final String fishConfigKey;
    final int requiredLevel;
    final boolean requiresKaramja;
    private final List<SupplyOrder> supplyOrders;
    private final String displayName;

    F2PFishOption(String fishConfigKey, int requiredLevel, boolean requiresKaramja, SupplyOrder... supplyOrders) {
        this.fishConfigKey = fishConfigKey;
        this.requiredLevel = requiredLevel;
        this.requiresKaramja = requiresKaramja;
        this.supplyOrders = Arrays.asList(supplyOrders);
        this.displayName = buildDisplayName(name());
    }

    public String getFishConfigKey() {
        return fishConfigKey;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public boolean isRequiresKaramja() {
        return requiresKaramja;
    }

    List<SupplyOrder> getSupplyOrders() {
        return supplyOrders;
    }

    String getDisplayName() {
        return displayName;
    }

    private static String buildDisplayName(String enumName) {
        String[] words = enumName.toLowerCase().split("_");
        StringBuilder value = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty() || "f2p".equals(word)) {
                continue;
            }
            if (value.length() > 0) {
                value.append(" ");
            }
            value.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return value.length() == 0 ? enumName : value.toString();
    }
}
