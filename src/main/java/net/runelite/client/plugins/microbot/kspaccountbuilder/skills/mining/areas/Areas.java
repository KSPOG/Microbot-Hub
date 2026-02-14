package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.mining.areas;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * Skeleton mining areas used by KSPAccountBuilder.
 */
public final class Areas {

    private Areas() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Varrock mine section with Copper, Tin, and Iron rocks.
     * Bounds: (3281,3370) to (3290,3359), plane 0.
     */
    public static final WorldArea COPPER_TIN_IRON =
            new WorldArea(new WorldPoint(3281, 3359, 0), 10, 12);

    /**
     * Coal mining section bounds: (3075,3424) to (3085,3416), plane 0.
     */
    public static final WorldArea COAL =
            new WorldArea(new WorldPoint(3075, 3416, 0), 11, 9);

    /**
     * Silver mining section bounds: (3169,3372) to (3183,3363), plane 0.
     */
    public static final WorldArea SILVER =
            new WorldArea(new WorldPoint(3169, 3363, 0), 15, 10);
}
