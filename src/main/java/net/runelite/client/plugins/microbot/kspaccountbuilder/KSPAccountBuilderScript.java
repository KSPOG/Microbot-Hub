package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
public class KSPAccountBuilderScript extends Script {
    private static String status = "Idle";
    private static String currentTask = "Waiting";
    private static long startTimeMs;

    public boolean run(KSPAccountBuilderConfig config) {
        startTimeMs = System.currentTimeMillis();
        status = "Starting";
        currentTask = "Initializing";
        applyAntibanSettings(config.enableAntiban());

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    return;
                }

                if (!Microbot.isLoggedIn()) {
                    status = "Waiting for login";
                    currentTask = "Idle";
                    return;
                }

                applyAntibanSettings(config.enableAntiban());
                currentTask = "Account setup";
                status = "Ready";
            } catch (Exception ex) {
                status = "Error";
                log.error("KSP Account Builder error", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        status = "Stopped";
        currentTask = "Idle";
        applyAntibanSettings(false);
    }

    private void applyAntibanSettings(boolean enabled) {
        Rs2AntibanSettings.antibanEnabled = enabled;
        Rs2AntibanSettings.naturalMouse = enabled;
    }

    public static String getStatus() {
        return status;
    }

    public static String getCurrentTask() {
        return currentTask;
    }

    public static Duration getRuntime() {
        if (startTimeMs == 0) {
            return Duration.ZERO;
        }

        return Duration.ofMillis(System.currentTimeMillis() - startTimeMs);
    }
}
