package net.runelite.client.plugins.microbot.KSPAccountBuilder;

import net.runelite.client.plugins.microbot.autofishing.enums.Fish;

import java.util.Arrays;
import java.util.List;

enum F2PFishOption {
    SHRIMP(Fish.SHRIMP_AND_ANCHOVIES, 1, false),
    SARDINE(Fish.SARDINE, 5, false, new SupplyOrder("Fishing bait")),
    HERRING(Fish.HERRING, 10, false, new SupplyOrder("Fishing bait")),
    TROUT_SALMON(Fish.TROUT_AND_SALMON, 20, false, new SupplyOrder("Feather")),
    PIKE(Fish.PIKE, 25, false, new SupplyOrder("Fishing bait")),
    TUNA_SWORDFISH(Fish.TUNA_AND_SWORDFISH, 35, true),
    LOBSTER(Fish.LOBSTER, 40, true);

    final Fish fish;
    final int requiredLevel;
    final boolean requiresKaramja;
    private final List<SupplyOrder> supplyOrders;
    private final String displayName;

    F2PFishOption(Fish fish, int requiredLevel, boolean requiresKaramja, SupplyOrder... supplyOrders) {
        this.fish = fish;
        this.requiredLevel = requiredLevel;
        this.requiresKaramja = requiresKaramja;
        this.supplyOrders = Arrays.asList(supplyOrders);
        this.displayName = buildDisplayName(name());
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
