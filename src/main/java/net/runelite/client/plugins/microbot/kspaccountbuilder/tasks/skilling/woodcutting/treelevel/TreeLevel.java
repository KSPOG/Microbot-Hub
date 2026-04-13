package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.treelevel;

import lombok.Getter;

@Getter
public enum TreeLevel
{
    TREE("Tree", 1),
    OAK("Oak", 15),
    WILLOW("Willow", 30),
    YEW("Yew", 60);

    private final String displayName;
    private final int requiredWoodcuttingLevel;

    TreeLevel(String displayName, int requiredWoodcuttingLevel)
    {
        this.displayName = displayName;
        this.requiredWoodcuttingLevel = requiredWoodcuttingLevel;
    }
}