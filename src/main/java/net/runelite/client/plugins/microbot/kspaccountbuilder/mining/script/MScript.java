package net.runelite.client.plugins.microbot.kspaccountbuilder.mining.script;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KSPAccountBuilderConfig;

import java.util.concurrent.TimeUnit;

@Slf4j
public class MScript extends Script {
    private KSPAccountBuilderConfig config;

    public boolean run(KSPAccountBuilderConfig config) {
        this.config = config;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                // Placeholder main loop kept intentionally minimal to avoid startup/runtime blocking.
                if (this.config != null && this.config.enableAntiban()) {
                    // antiban hook placeholder
                }
            } catch (Exception ex) {
                log.trace("Exception in KSPAccountBuilder MScript loop", ex);
            }
        }, 0, 800, TimeUnit.MILLISECONDS);

        return true;
    }
}
