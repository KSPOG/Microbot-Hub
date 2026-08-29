package net.runelite.client.plugins.microbot.flippilot.data;

public class ItemDef
{
    public final int id;
    public final String name;
    public final boolean members;
    public final int limit; // GE 4h limit if known, else 0

    public ItemDef(int id, String name, boolean members, int limit)
    {
        this.id = id;
        this.name = name;
        this.members = members;
        this.limit = limit;
    }
}
