package net.runelite.client.plugins.microbot.kspautorepair;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("KspAutoRepair")
public interface KspAutoRepairConfig extends Config
{
    @ConfigItem(
            keyName = "fullAuto",
            name = "Full auto repair",
            description = "Automatically diagnose, edit, validate, commit and push KSP source repairs",
            position = 0
    )
    default boolean fullAuto()
    {
        return true;
    }

    @ConfigItem(
            keyName = "agentBackend",
            name = "Agent backend",
            description = "Coding agent used for autonomous repairs",
            position = 1
    )
    default KspAgentBackend agentBackend()
    {
        return KspAgentBackend.CLAUDE_CODE;
    }

    @ConfigItem(
            keyName = "agentExecutable",
            name = "Agent executable",
            description = "Executable name or path. Claude Code normally uses: claude",
            position = 2
    )
    default String agentExecutable()
    {
        return "claude";
    }

    @ConfigItem(
            keyName = "claudeModel",
            name = "Claude model",
            description = "Model passed to Claude Code when that backend is selected",
            position = 3
    )
    default String claudeModel()
    {
        return "sonnet";
    }

    @ConfigItem(
            keyName = "customAgentCommand",
            name = "Custom agent command",
            description = "Custom shell command. {prompt} and {repo} are replaced automatically",
            position = 4
    )
    default String customAgentCommand()
    {
        return "";
    }

    @ConfigItem(
            keyName = "repositoryUrl",
            name = "KSP source repository",
            description = "Git repository containing the source consumed by KSP Source Loader",
            position = 5
    )
    default String repositoryUrl()
    {
        return "https://github.com/KSPOG/ksppluginsrelease.git";
    }

    @ConfigItem(
            keyName = "repositoryBranch",
            name = "Source branch",
            description = "Branch monitored by KSP Source Loader",
            position = 6
    )
    default String repositoryBranch()
    {
        return "main";
    }

    @ConfigItem(
            keyName = "repositoryPath",
            name = "Managed repository path",
            description = "Optional local clone path. Blank uses ~/.runelite/ksp-agent-repair/worktree/ksppluginsrelease",
            position = 7
    )
    default String repositoryPath()
    {
        return "";
    }

    @ConfigItem(
            keyName = "stallSeconds",
            name = "Stall threshold (sec)",
            description = "How long meaningful runtime state may remain unchanged before a stall incident is created",
            position = 8
    )
    @Range(min = 30, max = 900)
    default int stallSeconds()
    {
        return 120;
    }

    @ConfigItem(
            keyName = "incidentCooldownSeconds",
            name = "Incident cooldown (sec)",
            description = "Minimum interval between autonomous incidents for the same plugin",
            position = 9
    )
    @Range(min = 30, max = 3600)
    default int incidentCooldownSeconds()
    {
        return 180;
    }

    @ConfigItem(
            keyName = "validationSeconds",
            name = "Post-fix validation (sec)",
            description = "Required observation period after the source loader sees a repaired revision",
            position = 10
    )
    @Range(min = 15, max = 600)
    default int validationSeconds()
    {
        return 60;
    }

    @ConfigItem(
            keyName = "maxAgentAttempts",
            name = "Agent attempts",
            description = "Maximum agent edit/compile attempts for one incident",
            position = 11
    )
    @Range(min = 1, max = 8)
    default int maxAgentAttempts()
    {
        return 3;
    }

    @ConfigItem(
            keyName = "agentTimeoutMinutes",
            name = "Agent timeout (min)",
            description = "Maximum time allowed for each coding-agent invocation",
            position = 12
    )
    @Range(min = 1, max = 60)
    default int agentTimeoutMinutes()
    {
        return 15;
    }

    @ConfigItem(
            keyName = "captureScreenshots",
            name = "Capture screenshots",
            description = "Capture the RuneLite canvas when an incident is created",
            position = 13
    )
    default boolean captureScreenshots()
    {
        return true;
    }

    @ConfigItem(
            keyName = "autoPush",
            name = "Push validated fixes",
            description = "Commit and push a repair only after local Java compilation succeeds",
            position = 14
    )
    default boolean autoPush()
    {
        return true;
    }
}
