package net.runelite.client.plugins.microbot.loader;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(KspSourceLoaderConfig.GROUP)
public interface KspSourceLoaderConfig extends Config
{
    String GROUP = "kspSourceLoader";
    String REFRESH_KEY = "refreshFromRepo";

    @ConfigItem(
            keyName = REFRESH_KEY,
            name = "Refresh From Repo",
            description = "Check GitHub now; only changed plugin units are refreshed",
            position = 0
    )
    default ConfigButton refreshFromRepo()
    {
        return new ConfigButton();
    }
}
