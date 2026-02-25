package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.script.WoodcuttingScript;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
public class KSPAccountBuilderScript extends Script {
    private static final long LOOP_INITIAL_DELAY_MS = 0L;
    private static final long LOOP_DELAY_MS = 1000L;

    private static volatile String status = "Idle";

    private static volatile String currentTask = "None";

    private static volatile Instant startedAt;

    public static String getStatus() {
        return status;
    }

    public static String getCurrentTask() {
        return currentTask;
    }

    private final WoodcuttingScript woodcuttingScript = new WoodcuttingScript();

    private volatile boolean startupBankingComplete;

    public boolean run(KSPAccountBuilderConfig config) {
        status = "Starting";
        currentTask = "Initializing";
        startedAt = Instant.now();
        startupBankingComplete = false;

        woodcuttingScript.initialize();

        Rs2Antiban.resetAntibanSettings();
        if (config.enableAntiban()) {
            Rs2Antiban.antibanSetupTemplates.applyUniversalAntibanSetup();
            Rs2AntibanSettings.actionCooldownChance = Math.max(0.0, Math.min(1.0, config.actionCooldownChance()));
        }

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

                if (!startupBankingComplete) {
                    currentTask = "Preparation";
                    if (!prepareForTaskStart()) {
                        return;
                    }
                    startupBankingComplete = true;
                }

                if (config.enableAntiban() && Rs2AntibanSettings.actionCooldownActive) {
                    status = "Antiban cooldown";
                    return;
                }

                currentTask = "Woodcutting";
                woodcuttingScript.execute();
                status = woodcuttingScript.getStatus();

                if (config.enableAntiban()) {
                    Rs2Antiban.actionCooldown();
                    Rs2Antiban.takeMicroBreakByChance();
                }
            } catch (Exception ex) {
                status = "Error";
                currentTask = "Error";
                log.error("KSPAccountBuilder encountered an error", ex);
            }
        }, LOOP_INITIAL_DELAY_MS, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean prepareForTaskStart() {
        status = "Banking before task";

        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            status = "Walking to bank";
            return false;
        }

        Rs2Bank.depositAll();
        Rs2Bank.depositEquipment();
        status = "Ready (bank open)";
        return true;
    }

    public static String getRunningTime() {
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
        startupBankingComplete = false;
        woodcuttingScript.shutdown();
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}