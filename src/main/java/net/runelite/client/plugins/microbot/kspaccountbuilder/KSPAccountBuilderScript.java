package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.mining.script.MiningSetup;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.script.WoodcuttingScript;

import java.util.concurrent.TimeUnit;

@Slf4j
public class KSPAccountBuilderScript extends Script {

    @Getter
    private String status = "Idle";

    private final MiningSetup miningSetup = new MiningSetup();
    private final WoodcuttingScript woodcuttingScript = new WoodcuttingScript();

    public boolean run(KSPAccountBuilderConfig config) {
        status = "Starting";
        miningSetup.initialize();
        woodcuttingScript.initialize();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    status = "Waiting for login";
                    return;
                }

                if (!super.run()) {
                    return;
                }

                status = "KSP Account Builder active";

                // TODO: Wire mining setup into account progression workflow.
                if (miningSetup.hasRequiredTools()) {
                    miningSetup.execute();
                }

                // TODO: Wire woodcutting setup into account progression workflow.
                if (woodcuttingScript.hasRequiredTools()) {
                    woodcuttingScript.execute();
                }
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
        woodcuttingScript.shutdown();
        super.shutdown();
    }
}
