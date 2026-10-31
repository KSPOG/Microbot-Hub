package net.runelite.client.plugins.microbot.ksprandomworldhopper;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;

import javax.inject.Singleton;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Singleton
public class KspRandomWorldHopperScript extends Script
{
    private volatile long nextHopAt;
    private volatile int nextDelayMinutes;

    public boolean run(KspRandomWorldHopperConfig config)
    {
        scheduleNextHop(config);

        mainScheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn())
            {
                return;
            }

            if (Microbot.isHopping() || System.currentTimeMillis() < nextHopAt)
            {
                return;
            }

            hop(config);
        }, 0, 1, TimeUnit.SECONDS);

        return true;
    }

    private void hop(KspRandomWorldHopperConfig config)
    {
        boolean membersWorld = resolveMembersWorld(config);

        if (membersWorld && !Rs2Player.isMember())
        {
            Microbot.status = "P2P Only enabled, but account is F2P";
            scheduleNextHop(config);
            return;
        }

        int currentWorld = Rs2Player.getWorld();
        int world = Login.getRandomWorld(membersWorld);

        if (world <= 0)
        {
            Microbot.status = "No valid " + (membersWorld ? "P2P" : "F2P") + " world found";
            retrySoon();
            return;
        }

        // Avoid wasting the timer if the random selector returns the current world.
        if (world == currentWorld)
        {
            retrySoon();
            return;
        }

        Microbot.status = "Hopping to world " + world;

        if (Microbot.hopToWorld(world))
        {
            Microbot.status = "Hopped to world " + world;
            scheduleNextHop(config);
        }
        else
        {
            Microbot.status = "World hop failed";
            retrySoon();
        }
    }

    private boolean resolveMembersWorld(KspRandomWorldHopperConfig config)
    {
        if (config.useF2POnly() && !config.useP2POnly())
        {
            return false;
        }

        if (config.useP2POnly() && !config.useF2POnly())
        {
            return true;
        }

        // Both enabled or both disabled: stay compatible with the account type.
        return Rs2Player.isMember();
    }

    private void scheduleNextHop(KspRandomWorldHopperConfig config)
    {
        int min = Math.max(1, Math.min(config.minutesX(), config.minutesY()));
        int max = Math.max(min, Math.max(config.minutesX(), config.minutesY()));

        nextDelayMinutes = ThreadLocalRandom.current().nextInt(min, max + 1);
        nextHopAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(nextDelayMinutes);

        Microbot.status = "Next world hop in " + nextDelayMinutes + " minute"
                + (nextDelayMinutes == 1 ? "" : "s");
    }

    private void retrySoon()
    {
        nextHopAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
    }

    public int getNextDelayMinutes()
    {
        return nextDelayMinutes;
    }

    public long getMillisUntilNextHop()
    {
        return Math.max(0L, nextHopAt - System.currentTimeMillis());
    }
}
