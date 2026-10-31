package net.runelite.client.plugins.microbot.ksphighalch;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(KspHighAlchConfig.GROUP)
@ConfigInformation(
        "High-alchs configured inventory items. " +
        "Shift-right-click an inventory item and choose <b>Set High Alch Item</b> to add it to the list."
)
public interface KspHighAlchConfig extends Config
{
    String GROUP = "kspHighAlch";

    @ConfigItem(
            keyName = "alchItems",
            name = "High Alch Items",
            description = "Comma-separated exact item names to high alch. Use Shift <Set High Alch Item> on inventory items to add them.",
            position = 0
    )
    default String alchItems()
    {
        return "";
    }

    @ConfigItem(
            keyName = "bankWhenOut",
            name = "Bank when out of items",
            description = "Walk/open the nearest bank and restock runes/staff/alch items when no configured item is in inventory.",
            position = 1
    )
    default boolean bankWhenOut()
    {
        return true;
    }

    @ConfigItem(
            keyName = "withdrawNatureRunes",
            name = "Nature runes to withdraw",
            description = "Amount of Nature runes to keep in inventory. Set to 0 to withdraw all.",
            position = 2
    )
    @Range(min = 0, max = 100000)
    default int withdrawNatureRunes()
    {
        return 1000;
    }

    @ConfigItem(
            keyName = "withdrawFireRunes",
            name = "Fire runes to withdraw",
            description = "Amount of Fire runes to keep in inventory when no equipped staff supplies Fire runes. Set to 0 to withdraw all.",
            position = 3
    )
    @Range(min = 0, max = 100000)
    default int withdrawFireRunes()
    {
        return 1000;
    }

    @ConfigItem(
            keyName = "delayMin",
            name = "Minimum alch delay",
            description = "Minimum delay passed to Rs2Magic.alch.",
            position = 4
    )
    @Range(min = 100, max = 5000)
    default int delayMin()
    {
        return 300;
    }

    @ConfigItem(
            keyName = "delayMax",
            name = "Maximum alch delay",
            description = "Maximum delay passed to Rs2Magic.alch.",
            position = 5
    )
    @Range(min = 100, max = 5000)
    default int delayMax()
    {
        return 600;
    }

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug logging",
            description = "Print detailed state messages to the Microbot log.",
            position = 6
    )
    default boolean debugLogging()
    {
        return false;
    }
}
