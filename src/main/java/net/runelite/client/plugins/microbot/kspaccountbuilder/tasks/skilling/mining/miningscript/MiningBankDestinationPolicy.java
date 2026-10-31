package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.miningscript;

import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas.Areas;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

/** Resolves explicitly verified nearby banks; null preserves default selection. */
final class MiningBankDestinationPolicy
{
    private MiningBankDestinationPolicy() {}

    static BankLocation preferredBankForArea(Areas area)
    {
        if (area == Areas.TIN_COPPER_VARROCK_EAST || area == Areas.IRON_VARROCK_EAST)
        {
            return BankLocation.VARROCK_EAST;
        }
        return null;
    }
}
