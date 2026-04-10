package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltarea;

import lombok.Getter;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * Smelting area registry for KSP Account Builder.
 */
@Getter
public enum SmeltArea
{
    SMELT_AREA_EDGEVILLE_FURNACE(
        "Edgeville Furnace",
        new WorldPoint(3105, 3501, 0),
        new WorldPoint(3110, 3496, 0)
    );

    private final String displayName;
    private final WorldPoint southWest;
    private final WorldPoint northEast;

    SmeltArea(String displayName, WorldPoint firstCorner, WorldPoint secondCorner)
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
