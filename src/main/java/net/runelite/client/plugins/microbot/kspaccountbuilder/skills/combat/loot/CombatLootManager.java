package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.loot;

import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.List;

public class CombatLootManager {

    private final long reclickDelayMs;
    private long nextLootAttemptAt;

    public CombatLootManager(long reclickDelayMs) {
        this.reclickDelayMs = reclickDelayMs;
        this.nextLootAttemptAt = 0L;
    }

    public void reset() {
        this.nextLootAttemptAt = 0L;
    }

    public boolean tryLoot(int areaLootRadius, List<String> names) {
        long now = System.currentTimeMillis();
        if (now < nextLootAttemptAt) {
            return false;
        }

        if (Rs2Inventory.isFull()) {
            return false;
        }

        if (Loot.lootCoins(areaLootRadius)) {
            nextLootAttemptAt = now + reclickDelayMs;
            return true;
        }

        if (names.isEmpty()) {
            return false;
        }

        boolean looted = Rs2GroundItem.lootItemsBasedOnNames(new LootingParameters(
                areaLootRadius,
                1,
                1,
                0,
                true,
                true,
                names.toArray(new String[0])
        ));

        if (looted) {
            nextLootAttemptAt = now + reclickDelayMs;
        }

        return looted;
    }
}
