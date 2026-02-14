package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.mining.MiningSetup;

import java.util.concurrent.TimeUnit;

@Slf4j
public class KSPAccountBuilderScript extends Script {

    @Getter
    private String status = "Idle";

    private final MiningSetup miningSetup = new MiningSetup();

    public boolean run(KSPAccountBuilderConfig config) {
        status = "Starting";
        miningSetup.initialize();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    status = "Waiting for login";
                    return;
                }

                if (!super.run()) {
                    return;
                }

                status = "Skeleton active";
                // TODO: Wire mining setup into account progression workflow.
                if (!miningSetup.hasRequiredTools()) {
                    return;
                }

                miningSetup.execute();
            } catch (Exception ex) {
                status = "Error";
                log.error("KSPAccountBuilder encountered an error", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        status = "Stopped";
        miningSetup.shutdown();
        super.shutdown();
    }
}
