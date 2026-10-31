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

<<<<<<< Updated upstream
    enum SourceBranch
    {
        MAIN,
        DEV
=======
    /** Only allow known repository refs to be selected in the RuneLite dropdown. */
    enum SourceBranch
    {
        MAIN("main"),
        DEV("dev");

        private final String gitRef;

        SourceBranch(String gitRef)
        {
            this.gitRef = gitRef;
        }

        public String gitRef()
        {
            return gitRef;
        }

        @Override
        public String toString()
        {
            return this == MAIN ? "Main" : "Dev";
        }
>>>>>>> Stashed changes
    }

    @ConfigItem(
            keyName = BRANCH_KEY,
<<<<<<< Updated upstream
            name = "Repository Branch",
            description = "Select which KSPOG/ksppluginsrelease branch the source loader follows",
=======
            name = "Source Branch",
            description = "Choose the GitHub branch used to load KSP plugins. Switching branches reloads the plugins.",
>>>>>>> Stashed changes
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
