package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Choose a bank once when transitioning to TO_BANK, not on every navigation tick.
 * Wilderness returns always prefer Ferox; safe-area resupply uses a nearby bank.
 */
public final class KspRuniteMinerKillerNavigation
{
    private static final Logger log = LoggerFactory.getLogger(KspRuniteMinerKillerNavigation.class);
    private static volatile WorldPoint destination = BankLocation.FEROX_ENCLAVE.getWorldPoint();

    private KspRuniteMinerKillerNavigation() {}

    public static void chooseBank(WorldPoint here)
    {
        BankLocation selected = BankLocation.FEROX_ENCLAVE;
        if (KspRuniteMinerKillerFeroxRoute.atFerox(here))
        {
            destination = BankLocation.FEROX_ENCLAVE.getWorldPoint();
            log.info("[KSP Runite Miner Killer] Ferox bank selected directly after arrival: {}", here);
            return;
        }
        if (here != null && !Rs2Pvp.isInWilderness())
        {
            // Starting beside GE or Edgeville should never cause a long detour to Ferox.
            if (here.distanceTo(BankLocation.GRAND_EXCHANGE.getWorldPoint()) <= 25)
            {
                selected = BankLocation.GRAND_EXCHANGE;
            }
            else if (here.distanceTo(BankLocation.EDGEVILLE.getWorldPoint()) <= 25)
            {
                selected = BankLocation.EDGEVILLE;
            }
            else
            {
                // This can invoke a route search; only do it ONCE per bank trip.
                BankLocation nearest = Rs2Bank.getNearestBank(here);
                if (nearest != null && nearest.getWorldPoint() != null)
                {
                    selected = nearest;
                }
            }
        }
        destination = selected.getWorldPoint();
        log.info("[KSP Runite Miner Killer] bank route selected: {} from {}", selected, here);
    }

    public static WorldPoint bankTarget()
    {
        return destination;
    }
}
