package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;

@ConfigGroup("KspRuniteMinerKiller")
public interface KspRuniteMinerKillerConfig extends Config
{
    @ConfigSection(name = "Hunting", description = "Location and miner selection", position = 0)
    String HUNT = "hunt";
    @ConfigSection(name = "Magic combat", description = "Choose your spell and combat thresholds", position = 1)
    String COMBAT = "combat";
    @ConfigSection(name = "Loot & banking", description = "Pickup and Ferox Enclave banking", position = 2)
    String LOOT = "loot";

    enum AttackSpell
    {
        WIND_STRIKE(Rs2CombatSpells.WIND_STRIKE),
        WATER_STRIKE(Rs2CombatSpells.WATER_STRIKE),
        EARTH_STRIKE(Rs2CombatSpells.EARTH_STRIKE),
        FIRE_STRIKE(Rs2CombatSpells.FIRE_STRIKE),
        WIND_BOLT(Rs2CombatSpells.WIND_BOLT), FIRE_BOLT(Rs2CombatSpells.FIRE_BOLT),
        WIND_BLAST(Rs2CombatSpells.WIND_BLAST), FIRE_BLAST(Rs2CombatSpells.FIRE_BLAST),
        WIND_WAVE(Rs2CombatSpells.WIND_WAVE), FIRE_WAVE(Rs2CombatSpells.FIRE_WAVE),
        WIND_SURGE(Rs2CombatSpells.WIND_SURGE), FIRE_SURGE(Rs2CombatSpells.FIRE_SURGE);

        private final Rs2CombatSpells spell;
        AttackSpell(Rs2CombatSpells spell) { this.spell = spell; }
        public Rs2CombatSpells spell() { return spell; }
    }

    @ConfigItem(keyName = "mineX", name = "Mine centre X", description = "Lava Maze runite mine; change only after verifying the tile in-game", position = 0, section = HUNT)
    default int mineX() { return 3059; }

    @ConfigItem(keyName = "mineY", name = "Mine centre Y", description = "Lava Maze runite mine; change only after verifying the tile in-game", position = 1, section = HUNT)
    default int mineY() { return 3885; }

    @Range(min = 2, max = 15)
    @ConfigItem(keyName = "mineRadius", name = "Target radius", description = "Only acquire new player targets within this many tiles of mine centre", position = 2, section = HUNT)
    default int mineRadius() { return 8; }

    @ConfigItem(keyName = "requireMiningEvidence", name = "Require mining evidence", description = "Require a mining animation or an equipped pickaxe before attacking; may miss pickaxes carried in inventory", position = 3, section = HUNT)
    default boolean requireMiningEvidence() { return true; }

    @ConfigItem(keyName = "ignoredNames", name = "Ignore players", description = "Comma-separated player names to never target; friends and clanmates are excluded automatically", position = 4, section = HUNT)
    default String ignoredNames() { return ""; }

    @ConfigItem(keyName = "spell", name = "Combat spell", description = "Uses individual standard-spellbook combat casts, not autocast", position = 0, section = COMBAT)
    default AttackSpell spell() { return AttackSpell.FIRE_BLAST; }

    @Range(min = 15, max = 95)
    @ConfigItem(keyName = "eatAtPercent", name = "Eat HP below (%)", description = "Attempts to eat food whenever HP falls below this threshold", position = 1, section = COMBAT)
    default int eatAtPercent() { return 60; }

    @Range(min = 5, max = 100)
    @ConfigItem(keyName = "fleeAtPercent", name = "Disengage HP below (%)", description = "Disengages and heads for Ferox Enclave; deep Wilderness escape is NOT guaranteed", position = 2, section = COMBAT)
    default int fleeAtPercent() { return 30; }

    @Range(min = 1, max = 50)
    @ConfigItem(keyName = "minimumCasts", name = "Min casts before hunting", description = "If fewer spell casts are available, return to bank", position = 3, section = COMBAT)
    default int minimumCasts() { return 10; }

    @Range(min = 10, max = 500)
    @ConfigItem(keyName = "stockCasts", name = "Rune stock (casts)", description = "Target number of spells worth of runes to take from bank", position = 4, section = COMBAT)
    default int stockCasts() { return 100; }

    @ConfigItem(keyName = "foodName", name = "Food to withdraw", description = "Exact food name at the bank, e.g. Shark, Lobster, or Swordfish", position = 0, section = LOOT)
    default String foodName() { return "Shark"; }

    @Range(min = 1, max = 20)
    @ConfigItem(keyName = "stockFood", name = "Food to carry", description = "Number of food items to carry when leaving the bank", position = 1, section = LOOT)
    default int stockFood() { return 8; }

    @Range(min = 0, max = 10)
    @ConfigItem(keyName = "bankAtFreeSlots", name = "Bank when free slots <=", description = "Return to bank when inventory is low on free slots and contains picked-up loot", position = 2, section = LOOT)
    default int bankAtFreeSlots() { return 4; }

    @Range(min = 0, max = 1000000)
    @ConfigItem(keyName = "minimumLootValue", name = "Minimum loot stack GP", description = "Minimum estimated GE value for a newly appeared ground stack; 0 allows any item", position = 3, section = LOOT)
    default int minimumLootValue() { return 0; }

    @Range(min = 2, max = 8)
    @ConfigItem(keyName = "lootRadius", name = "Death-loot radius", description = "Only loot newly appeared ground items near the last observed death tile", position = 4, section = LOOT)
    default int lootRadius() { return 5; }

    @ConfigItem(keyName = "bankAfterKill", name = "Bank after each kill", description = "Deposit picked-up loot after every confirmed death, otherwise wait until inventory threshold", position = 5, section = LOOT)
    default boolean bankAfterKill() { return true; }

    @ConfigItem(keyName = "returnAfterBank", name = "Return to mine", description = "Automatically walk from Ferox Enclave back to the Lava Maze mine after banking", position = 6, section = LOOT)
    default boolean returnAfterBank() { return true; }
}
