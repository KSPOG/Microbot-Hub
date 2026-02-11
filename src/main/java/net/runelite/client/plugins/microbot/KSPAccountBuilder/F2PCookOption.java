package net.runelite.client.plugins.microbot.KSPAccountBuilder;

import net.runelite.client.plugins.microbot.gecooker.enums.CookingItem;

enum F2PCookOption {
    SHRIMP(CookingItem.RAW_SHRIMP, 1),
    HERRING(CookingItem.RAW_HERRING, 5),
    TROUT(CookingItem.RAW_TROUT, 15),
    SALMON(CookingItem.RAW_SALMON, 25),
    TUNA(CookingItem.RAW_TUNA, 30),
    LOBSTER(CookingItem.RAW_LOBSTER, 40),
    SWORDFISH(CookingItem.RAW_SWORDFISH, 45);

    final CookingItem cookingItem;
    final int requiredLevel;

    F2PCookOption(CookingItem cookingItem, int requiredLevel) {
        this.cookingItem = cookingItem;
        this.requiredLevel = requiredLevel;
    }


    int getRequiredLevel() {
        return requiredLevel;
    }

}
