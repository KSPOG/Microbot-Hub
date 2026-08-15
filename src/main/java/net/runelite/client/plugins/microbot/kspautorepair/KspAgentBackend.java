package net.runelite.client.plugins.microbot.kspautorepair;

public enum KspAgentBackend
{
    DISABLED("Disabled"),
    CODEX_CHATGPT("Codex / ChatGPT"),
    CLAUDE_CODE("Claude Code"),
    CUSTOM("Custom command");

    private final String displayName;

    KspAgentBackend(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
