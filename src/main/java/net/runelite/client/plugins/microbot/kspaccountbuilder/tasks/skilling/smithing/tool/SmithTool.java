package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.tool;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SmithTool
{
    HAMMER("Hammer", 2347);

    private final String displayName;
    private final int itemId;
}
