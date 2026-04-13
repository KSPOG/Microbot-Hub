package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.loglevels;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LogsLvl
{
    LOGS("Logs", 1),
    OAK_LOGS("Oak logs", 15),
    WILLOW_LOGS("Willow logs", 30);

    private final String displayName;
    private final int requiredLevel;
}
