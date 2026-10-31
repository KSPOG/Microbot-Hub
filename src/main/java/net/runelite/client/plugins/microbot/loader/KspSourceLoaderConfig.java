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
    String BRANCH_KEY = "sourceBranch";

    enum SourceBranch
    {
        MAIN,
        DEV
    }

    @ConfigItem(
            keyName = BRANCH_KEY,
            name = "Repository Branch",
            description = "Select which KSPOG/ksppluginsrelease branch the source loader follows",
            position = 0
    )
    default SourceBranch sourceBranch()
    {
        return SourceBranch.MAIN;
    }

    @ConfigItem(
            keyName = REFRESH_KEY,
            name = "Refresh From Repo",
            description = "Check GitHub now; only changed plugin units are refreshed",
            position = 1
    )
    default ConfigButton refreshFromRepo()
    {
        return new ConfigButton();
    }
}
