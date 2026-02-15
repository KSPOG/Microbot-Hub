package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.script.WoodcuttingScript;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
public class KSPAccountBuilderScript extends Script {
    private static final long LOOP_INITIAL_DELAY_MS = 0L;
    private static final long LOOP_DELAY_MS = 1000L;

    @Getter
    private String status = "Idle";

    @Getter
    private String currentTask = "None";

    private Instant startedAt;

    private final WoodcuttingScript woodcuttingScript = new WoodcuttingScript();

    public boolean run() {
        status = "Starting";
        currentTask = "Initializing";
        startedAt = Instant.now();

        woodcuttingScript.initialize();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    return;
                }

                if (!Microbot.isLoggedIn()) {
                    status = "Waiting for login";
                    currentTask = "Login";
                    return;
                }

                status = "Running";
                currentTask = "Woodcutting";
                woodcuttingScript.execute();
            } catch (Exception ex) {
                status = "Error";
                currentTask = "Error";
                log.error("KSPAccountBuilder encountered an error", ex);
            }
        }, LOOP_INITIAL_DELAY_MS, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

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
        woodcuttingScript.shutdown();
        super.shutdown();
    }
}
