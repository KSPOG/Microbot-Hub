package net.runelite.client.plugins.microbot.flippilot.microbot;

public class FlipEvent
{
    public final long timeMs;
    public final int itemId;
    public final String itemName;
    public final int qty;
    public final long profit;

    public FlipEvent(long timeMs, int itemId, String itemName, int qty, long profit)
    {
        this.timeMs = timeMs;
        this.itemId = itemId;
        this.itemName = itemName;
        this.qty = qty;
        this.profit = profit;
    }
}
