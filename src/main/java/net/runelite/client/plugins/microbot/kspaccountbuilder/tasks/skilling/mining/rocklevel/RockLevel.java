package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.rocklevel;

import lombok.Getter;

@Getter
public enum RockLevel
{
    COPPER("Copper", 1),
    TIN("Tin", 1),
    CLAY("Clay", 1),
    RUNE_ESSENCE("Rune essence", 1),
    IRON("Iron", 15),
    SILVER("Silver", 20),
    COAL("Coal", 30);

    private final String displayName;
    private final int requiredMiningLevel;

    RockLevel(String displayName, int requiredMiningLevel)
    {
        this.displayName = displayName;
        this.requiredMiningLevel = requiredMiningLevel;
    }
}
