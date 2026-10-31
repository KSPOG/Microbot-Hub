package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.miningscript;

import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas.Areas;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MiningBankDestinationPolicyTest
{
    @Test
    public void varrockEastTinCopperUsesLocalBank()
    {
        assertEquals(BankLocation.VARROCK_EAST, MiningBankDestinationPolicy.preferredBankForArea(Areas.TIN_COPPER_VARROCK_EAST));
    }

    @Test
    public void varrockEastIronUsesLocalBank()
    {
        assertEquals(BankLocation.VARROCK_EAST, MiningBankDestinationPolicy.preferredBankForArea(Areas.IRON_VARROCK_EAST));
    }

    @Test
    public void otherAreasRetainExistingBankSelection()
    {
        assertNull(MiningBankDestinationPolicy.preferredBankForArea(Areas.IRON_VARROCK_WEST));
        assertNull(MiningBankDestinationPolicy.preferredBankForArea(Areas.TIN_COPPER_RIMMINGTON));
        assertNull(MiningBankDestinationPolicy.preferredBankForArea(Areas.IRON_DWARVEN_EAST));
        assertNull(MiningBankDestinationPolicy.preferredBankForArea(null));
    }
}
