package net.runelite.client.plugins.microbot.sellerksp;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.Range;

@ConfigGroup("sellerksp")
public interface SellerKspConfig extends Config
{
    @ConfigItem(
            keyName = "itemBlacklist",
            name = "Item blacklist",
            description = "Items that should never be sold. Separate names with commas, semicolons, or new lines.",
            position = 0
    )
    default String itemBlacklist()
    {
        return "";
    }

    @Range(
            min = 1,
            max = 120
    )
    @ConfigItem(
            keyName = "changePriceTimeMinutes",
            name = "Change price time (min)",
            description = "How many minutes to wait before lowering an unsold offer price.",
            position = 1
    )
    default int changePriceTimeMinutes()
    {
        return 2;
    }

    @ConfigItem(
            keyName = "logoutWhenOutOfItems",
            name = "Logout when out of items",
            description = "If enabled, logs out when there are no more sellable items in inventory or bank.",
            position = 2
    )
    default boolean logoutWhenOutOfItems()
    {
        return true;
    }

    @ConfigItem(
            keyName = "clearPersistentBlacklist",
            name = "Clean blacklist",
            description = "Clears the persistent JSON blacklist built up by previous sessions.",
            position = 3
    )
    default ConfigButton clearPersistentBlacklist()
    {
        return new ConfigButton();
    }
}
