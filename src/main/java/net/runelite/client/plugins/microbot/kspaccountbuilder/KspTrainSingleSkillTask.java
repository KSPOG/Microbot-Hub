package net.runelite.client.plugins.microbot.kspaccountbuilder;

public enum KspTrainSingleSkillTask
{
    TUTORIAL_ISLAND("Tutorial Island"),
    COOKS_ASSISTANT("Cook's Assistant"),
    GOBLIN_DIPLOMACY("Goblin Diplomacy"),
    MINING("Mining"),
    WOODCUTTING("Woodcutting"),
    FIREMAKING("Firemaking"),
    FISHING("Fishing"),
    MELEE("Melee"),
    GE_SELL("GE Sell"),
    GE_BUY("GE Buy"),
    SMITHING("Smithing"),
    SMELTING("Smelting");

    private final String displayName;

    KspTrainSingleSkillTask(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
