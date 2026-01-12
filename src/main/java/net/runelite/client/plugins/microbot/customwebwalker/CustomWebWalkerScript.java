package net.runelite.client.plugins.microbot.customwebwalker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

public final class CustomWebWalker {

    private CustomWebWalker() {
    }

    public static WalkerState walkTo(WorldPoint destination, int reachedDistance, long timeoutMs) {
        return net.runelite.client.plugins.microbot.util.walker.CustomWebWalker.walkTo(
                destination,
                reachedDistance,
                timeoutMs
        );
    }

    public static WalkerState walkTo(WorldPoint destination, int reachedDistance) {
        return net.runelite.client.plugins.microbot.util.walker.CustomWebWalker.walkTo(destination, reachedDistance);
    }

    public static WalkerState walkTo(WorldPoint destination) {
        return net.runelite.client.plugins.microbot.util.walker.CustomWebWalker.walkTo(destination);
    }
}