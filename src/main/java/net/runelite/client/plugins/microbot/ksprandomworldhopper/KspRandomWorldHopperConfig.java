package net.runelite.client.plugins.microbot.ksprandomworldhopper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("KspRandomWorldHopper")
@ConfigInformation(
        "<h2>KSP Random World Hopper</h2>" +
        "<h3>Version: " + KspRandomWorldHopperPlugin.version + "</h3>" +
        "<p>After every successful hop, a new random delay is selected between Minutes X and Minutes Y.</p>" +
        "<p>Enable only one world-type filter. If both filters match, the plugin uses the account's current membership type.</p>"
)
public interface KspRandomWorldHopperConfig extends Config
{
    @ConfigSection(
            name = "World Hopping",
            description = "Random world hopping settings",
            position = 0
    )
    String hoppingSection = "hoppingSection";

    @Range(min = 1, max = 1440)
    @ConfigItem(
            keyName = "minutesX",
            name = "Minutes X",
            description = "Minimum number of minutes before a world hop",
            position = 0,
            section = hoppingSection
    )
    default int minutesX()
    {
        return 15;
    }

    @Range(min = 1, max = 1440)
    @ConfigItem(
            keyName = "minutesY",
            name = "Minutes Y",
            description = "Maximum number of minutes before a world hop",
            position = 1,
            section = hoppingSection
    )
    default int minutesY()
    {
        return 30;
    }

    @ConfigItem(
            keyName = "useF2POnly",
            name = "Use F2P Only",
            description = "Only hop to free-to-play worlds",
            position = 2,
            section = hoppingSection
    )
    default boolean useF2POnly()
    {
        return false;
    }

    @ConfigItem(
            keyName = "useP2POnly",
            name = "Use P2P Only",
            description = "Only hop to members worlds",
            position = 3,
            section = hoppingSection
    )
    default boolean useP2POnly()
    {
        return false;
    }
}
