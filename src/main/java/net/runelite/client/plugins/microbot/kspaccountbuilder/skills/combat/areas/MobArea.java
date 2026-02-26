package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.areas;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public final class MobArea {
    private MobArea() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Goblins area bounds: (3245,3227) to (3257,3241), plane 0.
     */
    public static final WorldArea GOBLINS =
            new WorldArea(new WorldPoint(3245, 3227, 0), 13, 15);

    /**
     * Cows area bounds: (3253,3255) to (3265,3296), plane 0.
     */
    public static final WorldArea COWS =
            new WorldArea(new WorldPoint(3253, 3255, 0), 13, 42);

    /**
     * Al-Kharid Guards area bounds: (3287,3167) to (3298,3177), plane 0.
     */
    public static final WorldArea AL_KHARID_GUARDS =
            new WorldArea(new WorldPoint(3287, 3167, 0), 12, 11);

}