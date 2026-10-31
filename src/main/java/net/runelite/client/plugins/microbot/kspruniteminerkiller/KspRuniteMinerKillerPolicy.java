package net.runelite.client.plugins.microbot.kspruniteminerkiller;

/** Pure functions for loot ownership-by-observation and deposit bounds. */
public final class KspRuniteMinerKillerPolicy
{
    private KspRuniteMinerKillerPolicy() {}

    /** A ground stack must have appeared or grown after pre-attack snapshot. */
    public static boolean isNewLootStack(int beforeQuantity, int currentQuantity)
    {
        return currentQuantity > 0 && currentQuantity > Math.max(0, beforeQuantity);
    }

    /** Never request a bank deposit beyond the number confirmed picked up. */
    public static int quantityToDeposit(int pickedUp, int currentlyCarried)
    {
        return Math.min(Math.max(0, pickedUp), Math.max(0, currentlyCarried));
    }

    /** Count an inventory delta only when the stack actually increases. */
    public static int confirmedPickup(int before, int after)
    {
        return Math.max(0, after - before);
    }
}
