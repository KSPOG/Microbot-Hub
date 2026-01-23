package net.runelite.client.plugins.microbot.flippilot.data;

public class LivePrice
{
    public final int high; // high traded price
    public final int low;  // low traded price

    public LivePrice(int high, int low)
    {
        this.high = high;
        this.low = low;
    }
}
