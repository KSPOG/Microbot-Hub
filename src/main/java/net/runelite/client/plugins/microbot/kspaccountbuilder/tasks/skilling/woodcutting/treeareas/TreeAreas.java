package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.treeareas;

import lombok.Getter;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * Woodcutting tree-area registry for KSP Account Builder.
 */
@Getter
public enum TreeAreas
{
    REGULAR_TREE_VARROCK_WEST(
        "Regular Tree (Varrock West)",
        new WorldPoint(3148, 3449, 0),
        new WorldPoint(3176, 3464, 0)
    ),
    OAK_TREE_DRAYNOR(
        "Oak Tree (Draynor)",
        new WorldPoint(3098, 3239, 0),
        new WorldPoint(3103, 3247, 0)
    ),
    WILLOW_TREES_DRAYNOR(
        "Willows (Draynor)",
        new WorldPoint(3081, 3226, 0),
        new WorldPoint(3091, 3238, 0)
    ),
    YEW_TREE_VARROCK_PALACE(
        "Yew Tree (Varrock Palace)",
        new WorldPoint(3085, 3468, 0),
        new WorldPoint(3088, 3482, 0)
    );

    private final String displayName;
    private final WorldPoint southWest;
    private final WorldPoint northEast;

    TreeAreas(String displayName, WorldPoint firstCorner, WorldPoint secondCorner)
    {
        this.displayName = displayName;

        int minX = Math.min(firstCorner.getX(), secondCorner.getX());
        int minY = Math.min(firstCorner.getY(), secondCorner.getY());
        int maxX = Math.max(firstCorner.getX(), secondCorner.getX());
        int maxY = Math.max(firstCorner.getY(), secondCorner.getY());

        this.southWest = new WorldPoint(minX, minY, firstCorner.getPlane());
        this.northEast = new WorldPoint(maxX, maxY, firstCorner.getPlane());
    }

    public WorldArea toWorldArea()
    {
        int width = (northEast.getX() - southWest.getX()) + 1;
        int height = (northEast.getY() - southWest.getY()) + 1;
        return new WorldArea(southWest.getX(), southWest.getY(), width, height, southWest.getPlane());
    }
}
