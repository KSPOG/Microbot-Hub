package net.runelite.client.plugins.microbot.devloader;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(KspDevLoaderConfig.GROUP)
public interface KspDevLoaderConfig extends Config
{
    String GROUP = "kspDevLoader";
    String REFRESH_KEY = "refreshFromRepo";

    @ConfigItem(
            keyName = REFRESH_KEY,
            name = "Refresh Dev Branch",
            description = "Check the GitHub dev branch; only changed plugin units are refreshed",
            position = 0
    )
    default ConfigButton refreshFromRepo()
    {
        return new ConfigButton();
    }
}
