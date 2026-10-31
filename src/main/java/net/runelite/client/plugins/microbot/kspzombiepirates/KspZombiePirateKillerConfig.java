package net.runelite.client.plugins.microbot.kspzombiepirates;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(KspZombiePirateKillerConfig.GROUP)
@ConfigInformation("Enter your license key here. All other plugin settings are in the KSP Zombie Pirates side panel and remain hidden until the license validates.")
public interface KspZombiePirateKillerConfig extends Config {
    String GROUP = "kspZombiePirateKiller";

    int DEFAULT_MIN_LOOT_VALUE = 1_000;

    String DEFAULT_LOOT_NAMES = String.join("\n",
            "Blood rune",
            "Death rune",
            "Chaos rune",
            "Battlestaff",
            "Adamant platebody",
            "Rune med helm",
            "Rune warhammer",
            "Rune battleaxe",
            "Rune longsword",
            "Rune sword",
            "Rune mace",
            "Dragon dagger",
            "Dragon longsword",
            "Dragon scimitar",
            "Blighted ancient ice sack",
            "Blighted anglerfish",
            "Blighted manta ray",
            "Blighted karambwan",
            "Blighted super restore(4)",
            "Coins",
            "Zombie pirate key",
            "Adamant seeds",
            "Teleport anchoring scroll",
            "Cannonball",
            "Gold ore",
            "Larran's key",
            "Slayer's enchantment");

    @ConfigItem(
            keyName = "licenseKey",
            name = "License key",
            description = "License key validated through your Cloudflare Worker.",
            secret = true,
            position = 0
    )
    default String licenseKey() {
        return "";
    }

    @ConfigItem(
            keyName = "licenseValidationUrl",
            name = "License URL",
            description = "Hidden Cloudflare Worker validation endpoint. The plugin sends POST JSON: {licenseKey, product, deviceId}.",
            hidden = true,
            secret = true,
            position = 1
    )
    default String licenseValidationUrl() {
        return "https://zombiepirates-license.ultraflipss500.workers.dev/";
    }

    @ConfigItem(
            keyName = "deviceId",
            name = "Device ID",
            description = "Hidden stable device identifier generated once and used for one-device license binding.",
            hidden = true,
            secret = true,
            position = 2
    )
    default String deviceId() {
        return "";
    }

    @ConfigItem(
            keyName = "equipmentNames",
            name = "Custom gear",
            description = "Hidden. Edited in side panel. Optional. One equipment item per line. Leave empty to use built-in default gear.",
            hidden = true,
            position = 10
    )
    default String equipmentNames() {
        return "";
    }

    @ConfigItem(
            keyName = "inventoryItems",
            name = "Inventory",
            description = "Hidden. Edited in side panel. Inventory to maintain after banking. Format: item:amount.",
            hidden = true,
            position = 11
    )
    default String inventoryItems() {
        return String.join("\n",
                "Looting bag:1",
                "Blighted karambwan:10",
                "Blighted super restore:2");
    }

    @ConfigItem(
            keyName = "keepProtectMagic",
            name = "Protect mage",
            description = "Hidden. Edited in side panel. Keeps Protect from Magic enabled while at Zombie pirates.",
            hidden = true,
            position = 20
    )
    default boolean keepProtectMagic() {
        return true;
    }

    @ConfigItem(
            keyName = "eatAtHp",
            name = "Eat at HP %",
            description = "Hidden. Edited in side panel.",
            hidden = true,
            position = 21
    )
    @Range(min = 1, max = 99)
    default int eatAtHp() {
        return 55;
    }

    @ConfigItem(
            keyName = "emergencyTeleportHp",
            name = "Emergency tele HP %",
            description = "Hidden. Edited in side panel.",
            hidden = true,
            position = 22
    )
    @Range(min = 1, max = 99)
    default int emergencyTeleportHp() {
        return 30;
    }

    @ConfigItem(
            keyName = "drinkPrayerAt",
            name = "Drink prayer at",
            description = "Hidden. Edited in side panel.",
            hidden = true,
            position = 23
    )
    @Range(min = 1, max = 99)
    default int drinkPrayerAt() {
        return 12;
    }

    @ConfigItem(
            keyName = "bankWhenPrayerBelow",
            name = "Bank if prayer below",
            description = "Hidden. Edited in side panel.",
            hidden = true,
            position = 24
    )
    @Range(min = 0, max = 99)
    default int bankWhenPrayerBelow() {
        return 3;
    }

    @ConfigItem(
            keyName = "prayAtNearestAltar",
            name = "Pray at nearest altar",
            description = "Hidden. Edited in side panel.",
            hidden = true,
            position = 25
    )
    default boolean prayAtNearestAltar() {
        return false;
    }


    @ConfigItem(
            keyName = "activePrayerNames",
            name = "Active prayers",
            description = "Hidden. Edited in side panel. Optional extra prayers to activate in the Zombie Pirate area. One prayer per line. Accepts display names or enum names.",
            hidden = true,
            position = 26
    )
    default String activePrayerNames() {
        return "";
    }

    @ConfigItem(
            keyName = "lootWhitelistNames",
            name = "Loot whitelist",
            description = "Hidden. Edited in side panel. Only these Zombie pirate drops are looted.",
            hidden = true,
            position = 30
    )
    default String lootNames() {
        return DEFAULT_LOOT_NAMES;
    }

    @ConfigItem(
            keyName = "minLootValue",
            name = "Min loot value",
            description = "Hidden. Edited in side panel. Only ground stacks with GE value at or above this amount are looted.",
            hidden = true,
            position = 31
    )
    @Range(min = 0, max = 100000000)
    default int minLootValue() {
        return DEFAULT_MIN_LOOT_VALUE;
    }

    @ConfigItem(
            keyName = "lootRange",
            name = "Loot range",
            description = "Hidden. Edited in side panel.",
            hidden = true,
            position = 32
    )
    @Range(min = 1, max = 15)
    default int lootRange() {
        return 8;
    }

    @ConfigItem(
            keyName = "attackRange",
            name = "Attack range",
            description = "Hidden. Edited in side panel.",
            hidden = true,
            position = 33
    )
    @Range(min = 1, max = 20)
    default int attackRange() {
        return 12;
    }

    @ConfigItem(
            keyName = "emptyContainerWidgetId",
            name = "Empty container widget",
            description = "Hidden. Edited in side panel. Your dump shows 786477.",
            hidden = true,
            position = 40
    )
    default int emptyContainerWidgetId() {
        return 786477;
    }

    @ConfigItem(
            keyName = "useEmptyContainersButton",
            name = "Use Empty containers",
            description = "Hidden. Edited in side panel.",
            hidden = true,
            position = 41
    )
    default boolean useEmptyContainersButton() {
        return true;
    }
}
