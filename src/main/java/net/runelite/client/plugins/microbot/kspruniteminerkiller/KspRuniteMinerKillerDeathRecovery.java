package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import net.runelite.api.Actor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Own-player PvP death interrupt. ActorDeath is delivered on the RuneLite client
 * thread; recovery is polled on the normal script executor. No bank or walk
 * actions are issued while death/respawn is being resolved. Do not return to
 * the Wilderness death tile to search for a grave: tradeable PvP drops are lost.
 */
final class KspRuniteMinerKillerDeathRecovery {
    private static final Logger log = LoggerFactory.getLogger(KspRuniteMinerKillerDeathRecovery.class);
    private static volatile boolean recovering;
    private static volatile long diedAt;
    private static volatile WorldPoint deathTile;
    private static volatile int playerDeaths;
    private static volatile boolean respawnLogged;

    private KspRuniteMinerKillerDeathRecovery() { }

    static synchronized void reset() {
        recovering = false;
        diedAt = 0;
        deathTile = null;
        playerDeaths = 0;
        respawnLogged = false;
    }

    static boolean active() { return recovering; }
    static int playerDeaths() { return playerDeaths; }

    /** Distinguish the local death from a target death using identity, not name. */
    static synchronized boolean recordDeath(Actor actor) {
        if (actor == null || Microbot.getClient() == null
                || actor != Microbot.getClient().getLocalPlayer()) return false;
        if (recovering) return true; // duplicate event must not restart the journey
        recovering = true;
        diedAt = System.currentTimeMillis();
        deathTile = actor.getWorldLocation();
        playerDeaths++;
        respawnLogged = false;
        KspRuniteMinerKillerFeroxRoute.reset();
        KspRuniteMinerKillerFeroxPrep.resetAfterDeath();
        try {
            Rs2Walker.clearWalkingRoute("KSP Runite Miner Killer: local death / clear old bank route");
        } catch (RuntimeException ex) {
            log.warn("Could not clear old walking route after death", ex);
        }
        log.warn("[KSP Runite Miner Killer] OWN DEATH detected at {} (count={}). "
                + "Waiting for respawn, then Castle Wars -> Ferox resupply and saved-gear restoration.",
                deathTile, playerDeaths);
        return true;
    }

    /**
     * A non-null status means the recovery owns this script tick. Null means
     * we reached Ferox: the caller MUST enter TO_BANK before returning to mine.
     */
    static synchronized String advance(WorldPoint here, long now) {
        if (!recovering) return "Death recovery inactive";
        if (here == null || now - diedAt < 1500L)
            return "Death detected - waiting for respawn";
        // Do not issue any walk command while still in the death area.
        if (Rs2Pvp.isInWilderness() && !KspRuniteMinerKillerFeroxRoute.atFerox(here))
            return "Death animation/respawn transition - waiting";
        if (!respawnLogged) {
            respawnLogged = true;
            log.info("[KSP Runite Miner Killer] respawn confirmed at {}; rerouting via Castle Wars", here);
        }
        if (KspRuniteMinerKillerFeroxRoute.atFerox(here)) {
            recovering = false;
            log.info("[KSP Runite Miner Killer] Ferox reached after death; now replenish runes/food then equip saved gear");
            return null;
        }
        String travel = KspRuniteMinerKillerFeroxRoute.advance(here, now);
        if (travel == null) return "Death recovery: travelling to Ferox";
        if (travel.startsWith("FAILED: ")) return travel;
        return "Death recovery: " + travel;
    }
}
