package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(KspAccountBuilderConfig.CONFIG_GROUP)
@ConfigInformation(
        "<b>Before starting the plugin:</b><br />" +
                "Make sure you either have about 50K GP In inventory or Requiered<br />" +
                "Tools i.e Pickaxe, Axe, Hammer in bank<br />" +
                "start next to a bank and let it do its thing."
)
public interface KspAccountBuilderConfig extends Config
{
    String CONFIG_GROUP = "kspaccountbuilder";

    @ConfigSection(
            name = "Antiban",
            description = "General antiban controls",
            position = 0
    )
    String antibanSection = "antiban";

    @ConfigSection(
            name = "Break Handler",
            description = "Randomized logout break scheduling",
            position = 1
    )
    String breakHandlerSection = "breakHandler";

    @ConfigSection(
            name = "Activity Switch",
            description = "Randomized activity switch timing",
            position = 2
    )
    String activitySwitchSection = "activitySwitch";

    @ConfigSection(
            name = "Developer Testing",
            description = "Developer-only task forcing and target selection",
            position = 3
    )
    String developerSection = "developer";

    @ConfigItem(
            keyName = "useAntiban",
            name = "Use Antiban",
            description = "Enable antiban behavior during account builder actions",
            position = 0,
            section = antibanSection
    )
    default boolean useAntiban()
    {
        return true;
    }


    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug Logging",
            description = "Show debug messages in chat/logs",
            position = 1,
            section = antibanSection
    )
    default boolean debugLogging()
    {
        return false;
    }

    @ConfigItem(
            keyName = "doBreaks",
            name = "Do Breaks",
            description = "If enabled, the script will perform randomized logout breaks",
            position = 0,
            section = breakHandlerSection
    )
    default boolean doBreaks()
    {
        return true;
    }

    @Range(min = 5, max = 300)
    @ConfigItem(
            keyName = "breakAfterMinMinutes",
            name = "Break After Min (min)",
            description = "Minimum minutes of runtime before scheduling a break",
            position = 1,
            section = breakHandlerSection
    )
    default int breakAfterMinMinutes()
    {
        return 45;
    }

    @Range(min = 5, max = 300)
    @ConfigItem(
            keyName = "breakAfterMaxMinutes",
            name = "Break After Max (min)",
            description = "Maximum minutes of runtime before scheduling a break",
            position = 2,
            section = breakHandlerSection
    )
    default int breakAfterMaxMinutes()
    {
        return 90;
    }

    @Range(min = 1, max = 180)
    @ConfigItem(
            keyName = "breakDurationMinMinutes",
            name = "Break Duration Min (min)",
            description = "Minimum break duration in minutes",
            position = 3,
            section = breakHandlerSection
    )
    default int breakDurationMinMinutes()
    {
        return 5;
    }

    @Range(min = 1, max = 180)
    @ConfigItem(
            keyName = "breakDurationMaxMinutes",
            name = "Break Duration Max (min)",
            description = "Maximum break duration in minutes",
            position = 4,
            section = breakHandlerSection
    )
    default int breakDurationMaxMinutes()
    {
        return 15;
    }

    @ConfigItem(
            keyName = "enableActivitySwitchRandomization",
            name = "Randomize Activity Switch",
            description = "Enable randomized timing before switching activities",
            position = 0,
            section = activitySwitchSection
    )
    default boolean enableActivitySwitchRandomization()
    {
        return true;
    }

    @Range(min = 5, max = 240)
    @ConfigItem(
            keyName = "activitySwitchMinMinutes",
            name = "Switch Activity Min (min)",
            description = "Minimum minutes before switching activity",
            position = 1,
            section = activitySwitchSection
    )
    default int activitySwitchMinMinutes()
    {
        return 20;
    }

    @Range(min = 5, max = 240)
    @ConfigItem(
            keyName = "activitySwitchMaxMinutes",
            name = "Switch Activity Max (min)",
            description = "Maximum minutes before switching activity",
            position = 2,
            section = activitySwitchSection
    )
    default int activitySwitchMaxMinutes()
    {
        return 45;
    }

    @ConfigItem(
            keyName = "developerPassword",
            name = "Developer Password",
            description = "Enter the developer password to enable test selectors",
            position = 0,
            section = developerSection,
            secret = true
    )
    default String developerPassword()
    {
        return "";
    }

    @ConfigItem(
            keyName = "developerTask",
            name = "Developer Task",
            description = "Developer task override. Only used when the password matches.",
            position = 1,
            section = developerSection
    )
    default KspDeveloperTask developerTask()
    {
        return KspDeveloperTask.DISABLED;
    }

    @ConfigItem(
            keyName = "developerMiningTarget",
            name = "Mining Target",
            description = "Developer mining area override. Only used when the password matches and Mining is selected.",
            position = 2,
            section = developerSection
    )
    default KspDeveloperMiningTarget developerMiningTarget()
    {
        return KspDeveloperMiningTarget.DEFAULT_PROGRESS;
    }

    @ConfigItem(
            keyName = "developerWoodcuttingTarget",
            name = "Woodcutting Target",
            description = "Developer woodcutting area override. Only used when the password matches and Woodcutting is selected.",
            position = 3,
            section = developerSection
    )
    default KspDeveloperWoodcuttingTarget developerWoodcuttingTarget()
    {
        return KspDeveloperWoodcuttingTarget.DEFAULT_PROGRESS;
    }

    @ConfigItem(
            keyName = "developerSmeltingTarget",
            name = "Smelting Target",
            description = "Developer smelting bar override. Only used when the password matches and Smelting is selected.",
            position = 4,
            section = developerSection
    )
    default KspDeveloperSmeltingTarget developerSmeltingTarget()
    {
        return KspDeveloperSmeltingTarget.DEFAULT_PROGRESS;
    }
}
