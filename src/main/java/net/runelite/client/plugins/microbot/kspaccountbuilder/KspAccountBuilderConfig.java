package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(KspAccountBuilderConfig.CONFIG_GROUP)
public interface KspAccountBuilderConfig extends Config
{
    String CONFIG_GROUP = "kspaccountbuilder";

    @ConfigItem(
        keyName = "enabled",
        name = "Enable script",
        description = "Enable the account builder automation skeleton",
        position = 0
    )
    default boolean enabled()
    {
        return false;
    }
}
