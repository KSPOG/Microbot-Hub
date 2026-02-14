package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.mining.script.MiningSetup;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.script.WoodcuttingScript;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
public class KSPAccountBuilderScript extends Script {

    @Getter
    private String status = "Idle";


    @Getter
    private String currentTask = "None";

    private Instant startedAt;


    private final MiningSetup miningSetup = new MiningSetup();
    private final WoodcuttingScript woodcuttingScript = new WoodcuttingScript();

    public boolean run(KSPAccountBuilderConfig config) {
        status = "Starting";

        currentTask = "Initializing";
        startedAt = Instant.now();

        miningSetup.initialize();
        woodcuttingScript.initialize();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    status = "Waiting for login";
                    currentTask = "Login";
                    return;
                }

                if (!super.run()) {
                    return;
                }

                status = "KSP Account Builder active";

                // TODO: Wire mining setup into account progression workflow.
                if (miningSetup.hasRequiredTools()) {

                    currentTask = "Mining";
                    miningSetup.execute();
                    return;

                    miningSetup.execute();

                }

                // TODO: Wire woodcutting setup into account progression workflow.
                if (woodcuttingScript.hasRequiredTools()) {

                    currentTask = "Woodcutting";
                    woodcuttingScript.execute();
                    return;
                }

                currentTask = "Gathering requirements";

                    woodcuttingScript.execute();
                }

            } catch (Exception ex) {
                status = "Error";
                currentTask = "Error";
                log.error("KSPAccountBuilder encountered an error", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        return true;
    }

    public String getRunningTime() {
        if (startedAt == null) {
            return "00:00:00";
        }

        Duration duration = Duration.between(startedAt, Instant.now());
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public void shutdown() {
        status = "Stopped";
        currentTask = "Stopped";
        miningSetup.shutdown();
        woodcuttingScript.shutdown();
        super.shutdown();
    }
}
