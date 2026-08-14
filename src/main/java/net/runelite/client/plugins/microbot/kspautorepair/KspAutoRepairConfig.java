package net.runelite.client.plugins.microbot.kspautorepair;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("KspAutoRepair")
public interface KspAutoRepairConfig extends Config
{
    @ConfigItem(keyName = "fullAuto", name = "Full auto repair",
            description = "Automatically diagnose, edit, validate, commit, reload and verify KSP repairs", position = 0)
    default boolean fullAuto() { return true; }

    @ConfigItem(keyName = "agentBackend", name = "Agent backend",
            description = "Coding agent used for autonomous repairs", position = 1)
    default KspAgentBackend agentBackend() { return KspAgentBackend.CLAUDE_CODE; }

    @ConfigItem(keyName = "agentExecutable", name = "Agent executable",
            description = "Executable name or path. Claude Code normally uses: claude", position = 2)
    default String agentExecutable() { return "claude"; }

    @ConfigItem(keyName = "claudeModel", name = "Claude model",
            description = "Model passed to Claude Code when that backend is selected", position = 3)
    default String claudeModel() { return "sonnet"; }

    @ConfigItem(keyName = "customAgentCommand", name = "Custom agent command",
            description = "Custom shell command. {prompt} and {repo} are replaced automatically", position = 4)
    default String customAgentCommand() { return ""; }

    @ConfigItem(keyName = "repositoryUrl", name = "KSP source repository",
            description = "Git repository containing the source consumed by KSP Source Loader", position = 5)
    default String repositoryUrl() { return "https://github.com/KSPOG/ksppluginsrelease.git"; }

    @ConfigItem(keyName = "repositoryBranch", name = "Source branch",
            description = "Branch monitored by KSP Source Loader", position = 6)
    default String repositoryBranch() { return "main"; }

    @ConfigItem(keyName = "repositoryPath", name = "Managed repository path",
            description = "Optional local clone path. Blank uses ~/.runelite/ksp-agent-repair/worktree/ksppluginsrelease", position = 7)
    default String repositoryPath() { return ""; }

    @ConfigItem(keyName = "stallSeconds", name = "Global stall threshold (sec)",
            description = "Fallback timeout when no semantic progress can be identified", position = 8)
    @Range(min = 30, max = 900)
    default int stallSeconds() { return 120; }

    @ConfigItem(keyName = "stateStallSeconds", name = "State stall threshold (sec)",
            description = "How long one plugin state may repeat without semantic progress", position = 9)
    @Range(min = 10, max = 600)
    default int stateStallSeconds() { return 45; }

    @ConfigItem(keyName = "loopRepetitions", name = "Loop repetitions",
            description = "Repeated state-sequence count required before a loop incident is raised", position = 10)
    @Range(min = 2, max = 8)
    default int loopRepetitions() { return 3; }

    @ConfigItem(keyName = "incidentCooldownSeconds", name = "Incident cooldown (sec)",
            description = "Minimum interval between autonomous incidents for the same plugin", position = 11)
    @Range(min = 30, max = 3600)
    default int incidentCooldownSeconds() { return 180; }

    @ConfigItem(keyName = "autoRepairConfidencePercent", name = "Auto repair confidence (%)",
            description = "Minimum deterministic diagnostic confidence required before source may be changed", position = 12)
    @Range(min = 50, max = 100)
    default int autoRepairConfidencePercent() { return 85; }

    @ConfigItem(keyName = "validationSeconds", name = "Post-fix validation (sec)",
            description = "Observation period after the repaired revision is loaded", position = 13)
    @Range(min = 15, max = 600)
    default int validationSeconds() { return 60; }

    @ConfigItem(keyName = "minValidationProgressEvents", name = "Required progress events",
            description = "Minimum semantic progress signals required during post-fix validation", position = 14)
    @Range(min = 1, max = 20)
    default int minValidationProgressEvents() { return 2; }

    @ConfigItem(keyName = "maxAgentAttempts", name = "Agent attempts",
            description = "Maximum agent edit/compile attempts for one incident", position = 15)
    @Range(min = 1, max = 8)
    default int maxAgentAttempts() { return 3; }

    @ConfigItem(keyName = "agentTimeoutMinutes", name = "Agent timeout (min)",
            description = "Maximum time allowed for each coding-agent invocation", position = 16)
    @Range(min = 1, max = 60)
    default int agentTimeoutMinutes() { return 15; }

    @ConfigItem(keyName = "sceneRadius", name = "Incident scene radius",
            description = "Radius used when retaining nearby NPC/object evidence", position = 17)
    @Range(min = 5, max = 50)
    default int sceneRadius() { return 20; }

    @ConfigItem(keyName = "maxTimelineEvents", name = "Timeline events",
            description = "Maximum recent event records retained for replay bundles", position = 18)
    @Range(min = 200, max = 5000)
    default int maxTimelineEvents() { return 1200; }

    @ConfigItem(keyName = "captureScreenshots", name = "Capture screenshots",
            description = "Capture the RuneLite canvas when an incident is created", position = 19)
    default boolean captureScreenshots() { return true; }

    @ConfigItem(keyName = "preferSourceLoaderCompiler", name = "Use loader compiler",
            description = "Use KSP Source Loader's exact validation/compiler path when discoverable", position = 20)
    default boolean preferSourceLoaderCompiler() { return true; }

    @ConfigItem(keyName = "requestLoaderRefresh", name = "Refresh loader after repair",
            description = "Ask KSP Source Loader to refresh immediately after a repair or rollback when supported", position = 21)
    default boolean requestLoaderRefresh() { return true; }

    @ConfigItem(keyName = "autoRollback", name = "Automatic rollback",
            description = "Automatically revert a repair rejected by the loader or runtime validation", position = 22)
    default boolean autoRollback() { return true; }

    @ConfigItem(keyName = "autoPush", name = "Push validated fixes",
            description = "Commit and push a staged repair only after validation succeeds", position = 23)
    default boolean autoPush() { return true; }
}
