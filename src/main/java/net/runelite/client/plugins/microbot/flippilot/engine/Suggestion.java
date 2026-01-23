package net.runelite.client.plugins.microbot.flippilot.engine;

public class Suggestion
{
    public final int itemId;
    public final String name;

    public final int high;
    public final int low;

    public final int margin;
    public final double roiPct;

    public final int limit;
    public final int vol5m;
    public final double risk;   // 0..1 approx
    public final double score;

    public Suggestion(int itemId, String name, int high, int low, int margin, double roiPct,
                      int limit, int vol5m, double risk, double score)
    {
        this.itemId = itemId;
        this.name = name;
        this.high = high;
        this.low = low;
        this.margin = margin;
        this.roiPct = roiPct;
        this.limit = limit;
        this.vol5m = vol5m;
        this.risk = risk;
        this.score = score;
    }
}
