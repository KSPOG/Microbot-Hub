package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.equiplevels;

import lombok.Getter;

@Getter
public enum AxeEquip
{
    BRONZE("Bronze axe", 1),
    IRON("Iron axe", 1),
    STEEL("Steel axe", 5),
    BLACK("Black axe", 10),
    MITHRIL("Mithril axe", 20),
    ADAMANT("Adamant axe", 30),
    RUNE("Rune axe", 40);

    private final String displayName;
    private final int requiredAttackLevel;

    AxeEquip(String displayName, int requiredAttackLevel)
    {
        this.displayName = displayName;
        this.requiredAttackLevel = requiredAttackLevel;
    }

    public static AxeEquip bestForAttackLevel(int attackLevel)
    {
        AxeEquip best = BRONZE;
        for (AxeEquip axe : values())
        {
            if (attackLevel >= axe.requiredAttackLevel)
            {
                best = axe;
            }
        }
        return best;
    }
}