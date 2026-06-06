package net.runelite.client.plugins.microbot.creatorbridge;

/**
 * Shared task/status holder for Microbot scripts that want to report progress
 * to the local JagexAccountCreator dashboard.
 */
public final class CreatorTaskState
{
    private static volatile String scriptName = "Unknown";
    private static volatile String currentTask = "Idle";
    private static volatile String scriptStatus = "Waiting";

    private CreatorTaskState()
    {
    }

    public static void update(String script, String task, String status)
    {
        scriptName = clean(script, "Unknown");
        currentTask = clean(task, "Idle");
        scriptStatus = clean(status, "Waiting");
    }

    public static void clear()
    {
        scriptName = "Unknown";
        currentTask = "Idle";
        scriptStatus = "Waiting";
    }

    public static String getScriptName()
    {
        return scriptName;
    }

    public static String getCurrentTask()
    {
        return currentTask;
    }

    public static String getScriptStatus()
    {
        return scriptStatus;
    }

    private static String clean(String value, String fallback)
    {
        if (value == null || value.isBlank())
        {
            return fallback;
        }

        return value.replace('\u00A0', ' ').trim();
    }
}
