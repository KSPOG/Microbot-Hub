package net.runelite.client.plugins.microbot.kspbattlestaffbuyer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(KspBattlestaffBuyerConfig.GROUP)
public interface KspBattlestaffBuyerConfig extends Config
{
    String GROUP = "kspbattlestaffbuyer";

    @ConfigSection(
            name = "Grand Exchange",
            description = "Controls Battlestaff selling behavior.",
            position = 0
    )
    String geSection = "grandExchange";

    @ConfigItem(
            keyName = "useCustomSellPrice",
            name = "Use Custom Sell Price",
            description = "When enabled, sell Battlestaves for the configured custom price instead of automatic profit-protected pricing.",
            position = 0,
            section = geSection
    )
    default boolean useCustomSellPrice()
    {
        return false;
    }

    @Range(min = 1, max = 2_000_000_000)
    @ConfigItem(
            keyName = "customSellPrice",
            name = "Custom Sell Price",
            description = "Exact Grand Exchange price per Battlestaff used when Use Custom Sell Price is enabled.",
            position = 1,
            section = geSection
    )
    default int customSellPrice()
    {
        return 8_300;
    }
}
