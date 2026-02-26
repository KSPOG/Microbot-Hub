package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.firemaking.needed;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.firemaking.levels.LogLevels;

public final class Needed {
    private Needed() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final int TINDERBOX = ItemID.TINDERBOX;
    public static final int TINDERBOX_COUNT = 1;
    public static final int LOG_COUNT = 27;

    public static final int LOGS = ItemID.LOGS;
    public static final int OAK_LOGS = ItemID.OAK_LOGS;
    public static final int WILLOW_LOGS = ItemID.WILLOW_LOGS;

    public static int getBestLogsForLevel(int firemakingLevel) {
        if (firemakingLevel >= LogLevels.WILLOW_LOGS) {
            return WILLOW_LOGS;
        }

        if (firemakingLevel >= LogLevels.OAK_LOGS) {
            return OAK_LOGS;
        }

        return LOGS;
    }
}