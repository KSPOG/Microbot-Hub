package net.runelite.client.plugins.microbot.f2pbossfarmer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.f2pbossfarmer.enums.BossMode;
import net.runelite.client.plugins.microbot.f2pbossfarmer.enums.BossTarget;

@ConfigGroup("f2pbossfarmer")
public interface F2PBossFarmerConfig extends Config {

    @ConfigSection(
            name = "General",
            description = "Boss rotation and key-farming behaviour",
            position = 0
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Supplies",
            description = "Food and Bryophyta utility items",
            position = 1
    )
    String suppliesSection = "supplies";

    @ConfigSection(
            name = "Combat",
            description = "Combat and prayer settings",
            position = 2
    )
    String combatSection = "combat";

    @ConfigSection(
            name = "Advanced",
            description = "Fallback underground navigation anchors",
            position = 3,
            closedByDefault = true
    )
    String advancedSection = "advanced";

    @ConfigItem(
            keyName = "mode",
            name = "Mode",
            description = "Auto rotates between Obor and Bryophyta; forced modes stay on one boss.",
            position = 0,
            section = generalSection
    )
    default BossMode mode() {
        return BossMode.AUTO;
    }

    @ConfigItem(
            keyName = "autoStartBoss",
            name = "Auto start boss",
            description = "Boss family to start farming when Auto mode has no usable keys banked.",
            position = 1,
            section = generalSection
    )
    default BossTarget autoStartBoss() {
        return BossTarget.OBOR;
    }

    @Range(min = 1, max = 10)
    @ConfigItem(
            keyName = "keysBeforeBoss",
            name = "Keys before boss",
            description = "Farm this many matching keys before switching from normal giants to the boss.",
            position = 2,
            section = generalSection
    )
    default int keysBeforeBoss() {
        return 1;
    }

    @ConfigItem(
            keyName = "alternateAfterChest",
            name = "Alternate after chest",
            description = "In Auto mode, switch to the other boss family after opening a chest when no immediately usable matching key remains.",
            position = 3,
            section = generalSection
    )
    default boolean alternateAfterChest() {
        return true;
    }

    @ConfigItem(
            keyName = "foodName",
            name = "Food",
            description = "Exact/partial item name used by Microbot for food withdrawal.",
            position = 0,
            section = suppliesSection
    )
    default String foodName() {
        return "Swordfish";
    }

    @Range(min = 1, max = 26)
    @ConfigItem(
            keyName = "foodAmount",
            name = "Food amount",
            description = "Target amount of food to withdraw at the bank.",
            position = 1,
            section = suppliesSection
    )
    default int foodAmount() {
        return 18;
    }

    @Range(min = 0, max = 10)
    @ConfigItem(
            keyName = "bankAtFood",
            name = "Bank at food",
            description = "Return to a bank when food falls to this amount outside a boss fight.",
            position = 2,
            section = suppliesSection
    )
    default int bankAtFood() {
        return 2;
    }

    @ConfigItem(
            keyName = "growthlingAxe",
            name = "Growthling axe",
            description = "Woodcutting axe to wield while killing Bryophyta's Growthlings.",
            position = 3,
            section = suppliesSection
    )
    default String growthlingAxe() {
        return "Rune axe";
    }

    @ConfigItem(
            keyName = "bringKnife",
            name = "Bring knife",
            description = "Withdraw a Knife for the Varrock Sewers web route to Bryophyta.",
            position = 4,
            section = suppliesSection
    )
    default boolean bringKnife() {
        return true;
    }

    @ConfigItem(
            keyName = "combatWeapon",
            name = "Combat weapon",
            description = "Weapon to restore after Growthlings. Leave blank if the plugin should not switch back automatically.",
            position = 0,
            section = combatSection
    )
    default String combatWeapon() {
        return "Rune scimitar";
    }

    @Range(min = 10, max = 90)
    @ConfigItem(
            keyName = "eatAtPercent",
            name = "Eat below %",
            description = "Uses Microbot's Rs2Player.eatAt threshold.",
            position = 1,
            section = combatSection
    )
    default int eatAtPercent() {
        return 45;
    }

    @ConfigItem(
            keyName = "useProtectionPrayers",
            name = "Use protection prayers",
            description = "Use Protect from Missiles at Obor and Protect from Magic at Bryophyta while prayer points remain. This plugin does not consume members-only prayer potions.",
            position = 2,
            section = combatSection
    )
    default boolean useProtectionPrayers() {
        return false;
    }

    @ConfigItem(
            keyName = "lootBossKeyDrops",
            name = "Loot boss key drops",
            description = "Pick up Giant/Mossy keys dropped directly by Obor/Bryophyta before opening the chest.",
            position = 3,
            section = combatSection
    )
    default boolean lootBossKeyDrops() {
        return true;
    }


    @ConfigItem(
            keyName = "hillAnchorX",
            name = "Hill giant X",
            description = "Fallback underground Hill Giant farming anchor in Edgeville Dungeon.",
            position = 0,
            section = advancedSection
    )
    default int hillAnchorX() {
        return 3110;
    }

    @ConfigItem(
            keyName = "hillAnchorY",
            name = "Hill giant Y",
            description = "Fallback underground Hill Giant farming anchor in Edgeville Dungeon.",
            position = 1,
            section = advancedSection
    )
    default int hillAnchorY() {
        return 9838;
    }

    @ConfigItem(
            keyName = "mossAnchorX",
            name = "Moss giant X",
            description = "Fallback underground Moss Giant farming anchor in Varrock Sewers.",
            position = 2,
            section = advancedSection
    )
    default int mossAnchorX() {
        return 3160;
    }

    @ConfigItem(
            keyName = "mossAnchorY",
            name = "Moss giant Y",
            description = "Fallback underground Moss Giant farming anchor in Varrock Sewers.",
            position = 3,
            section = advancedSection
    )
    default int mossAnchorY() {
        return 9903;
    }
}
