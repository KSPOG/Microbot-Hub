package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.areas;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * Skeleton woodcutting areas used by KSPAccountBuilder.
 */
public final class Areas {

    private Areas() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Tree area bounds: (3145,3465) to (3175,3449), plane 0.
     */
    public static final WorldArea TREES =
            new WorldArea(new WorldPoint(3145, 3449, 0), 31, 17);

    /**
     * Oak Varrock tree area bounds: (3188,3464) to (3197,3456), plane 0.
     */
    public static final WorldArea OAK_VARROCK =
            new WorldArea(new WorldPoint(3188, 3456, 0), 10, 9);

    /**
     * Oak Draynor tree area bounds: (3097,3248) to (3112,3236), plane 0.
     */
    public static final WorldArea OAK_DRAYNOR =
            new WorldArea(new WorldPoint(3097, 3236, 0), 16, 13);

    /**
     * Willow tree area bounds: (3080,3239) to (3091,3224), plane 0.
     */
    public static final WorldArea WILLOW =
            new WorldArea(new WorldPoint(3080, 3224, 0), 12, 16);

    /**
     * Yew tree area bounds: (3089,3482) to (3085,3468), plane 0.
     */
    public static final WorldArea YEW =
            new WorldArea(new WorldPoint(3085, 3468, 0), 5, 15);
}