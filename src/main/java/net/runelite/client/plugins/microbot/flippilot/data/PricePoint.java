package net.runelite.client.plugins.microbot.flippilot.data;

public class PricePoint
{
    public final long t;
    public final int high;
    public final int low;

    public PricePoint(long t, int high, int low)
    {
        this.t = t;
        this.high = high;
        this.low = low;
    }
}
