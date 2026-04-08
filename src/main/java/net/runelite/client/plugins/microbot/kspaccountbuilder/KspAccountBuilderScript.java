package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class KspAccountBuilderScript extends Script
{
    public boolean run(KspAccountBuilderConfig config)
    {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn() || !config.enabled())
            {
                return;
            }

            // TODO: Implement account-building steps.
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
    }
}
