package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(KspAccountBuilderConfig.CONFIG_GROUP)
public interface KspAccountBuilderConfig extends Config
{
    String CONFIG_GROUP = "kspaccountbuilder";

    @ConfigItem(
        keyName = "enabled",
        name = "Enable script",
        description = "Enable the account builder automation",
        position = 0
    )
    default boolean enabled()
    {
        return false;
    }

    @ConfigItem(
        keyName = "mode",
        name = "Build mode",
        description = "Select the account progression mode",
        position = 1
    )
    default KspAccountBuilderMode mode()
    {
        return KspAccountBuilderMode.STARTER;
    }

    @Range(min = 200, max = 5000)
    @ConfigItem(
        keyName = "tickDelayMs",
        name = "Loop delay (ms)",
        description = "Delay between account builder loop iterations",
        position = 2
    )
    default int tickDelayMs()
    {
        return 600;
    }

    @ConfigItem(
        keyName = "verboseLogging",
        name = "Verbose logging",
        description = "Print builder state transitions to the log",
        position = 3
    )
    default boolean verboseLogging()
    {
        return true;
    }
}
