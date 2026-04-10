package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.levelreqwc;

import lombok.Getter;

@Getter
public enum WoodCuttingReq
{
    BRONZE("Bronze axe", 1),
    IRON("Iron axe", 1),
    STEEL("Steel axe", 6),
    BLACK("Black axe", 11),
    MITHRIL("Mithril axe", 21),
    ADAMANT("Adamant axe", 31),
    RUNE("Rune axe", 41);

    private final String displayName;
    private final int requiredWoodcuttingLevel;

    WoodCuttingReq(String displayName, int requiredWoodcuttingLevel)
    {
        this.displayName = displayName;
        this.requiredWoodcuttingLevel = requiredWoodcuttingLevel;
    }

    public static WoodCuttingReq bestForWoodcuttingLevel(int woodcuttingLevel)
    {
        WoodCuttingReq best = BRONZE;
        for (WoodCuttingReq axe : values())
        {
            if (woodcuttingLevel >= axe.requiredWoodcuttingLevel)
            {
                best = axe;
            }
        }
        return best;
    }
}
