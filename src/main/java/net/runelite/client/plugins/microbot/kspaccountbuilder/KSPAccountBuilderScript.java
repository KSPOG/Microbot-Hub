package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.ClientUI;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
public class KSPAccountBuilderScript extends Script {
    private static final long LOOP_INITIAL_DELAY_MS = 0L;
    private static final long LOOP_DELAY_MS = 1000L;
    private static final long BREAK_PREP_WINDOW_SECONDS = 45L;

    private static volatile String status = "Idle";
    private static volatile String currentTask = "None";
    private static volatile Instant startedAt;

    private final net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.script.CombatScript combatScript =
            new net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.script.CombatScript();

    private volatile boolean startupBankingComplete;
    private volatile boolean breakActive;
    private volatile Instant nextBreakAt;
    private volatile Instant breakEndsAt;
    private volatile String originalTitle;

    public static String getStatus() {
        return status;
    }

    public static String getCurrentTask() {
        return currentTask;
    }

    public boolean run(KSPAccountBuilderConfig config) {
        status = "Starting";
        currentTask = "Combat";
        startedAt = Instant.now();
        startupBankingComplete = false;

        combatScript.initialize();
        initializeBreakScheduling(config);

        Rs2Antiban.resetAntibanSettings();
        if (config.enableAntiban()) {
            Rs2Antiban.antibanSetupTemplates.applyUniversalAntibanSetup();
            Rs2Antiban.setPlayStyle(PlayStyle.EXTREME_AGGRESSIVE);
            Rs2AntibanSettings.actionCooldownChance = Math.max(0.0, Math.min(1.0, config.actionCooldownChance()));
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    return;
                }

                handleCustomBreaks(config);
                updateClientTitle();

                if (breakActive) {
                    status = "On break";
                    currentTask = "Break";
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

                currentTask = "Combat";
                Rs2AntibanSettings.naturalMouse = true;
                combatScript.execute();
                status = combatScript.getStatus();

                if (config.enableAntiban()) {
                    applyAntibanCycle();
                }
            } catch (Exception ex) {
                status = "Error";
                currentTask = "Error";
                log.error("KSPAccountBuilder encountered an error", ex);
            }
        }, LOOP_INITIAL_DELAY_MS, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void applyAntibanCycle() {
        try {
            Rs2Antiban.setPlayStyle(PlayStyle.EXTREME_AGGRESSIVE);
            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
        } catch (NullPointerException ex) {
            log.warn("Antiban runtime state was null; resetting playstyle and continuing", ex);
            Rs2Antiban.setPlayStyle(PlayStyle.EXTREME_AGGRESSIVE);
            Rs2AntibanSettings.actionCooldownActive = false;
        }
    }

    private boolean prepareForTaskStart() {
        if (combatScript.hasCombatSetupReady()) {
            status = "Ready (inventory already prepared)";
            return true;
        }

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

    private void initializeBreakScheduling(KSPAccountBuilderConfig config) {
        breakActive = false;
        breakEndsAt = null;
        originalTitle = safeGetTitle();

        if (!config.enableCustomBreakHandler()) {
            nextBreakAt = null;
            return;
        }

        nextBreakAt = Instant.now().plus(Duration.ofMinutes(randomBetween(config.minRunMinutes(), config.maxRunMinutes())));
    }

    private void handleCustomBreaks(KSPAccountBuilderConfig config) {
        if (!config.enableCustomBreakHandler()) {
            breakActive = false;
            breakEndsAt = null;
            return;
        }

        Instant now = Instant.now();
        if (breakActive) {
            if (breakEndsAt != null && now.isAfter(breakEndsAt)) {
                breakActive = false;
                breakEndsAt = null;
                nextBreakAt = now.plus(Duration.ofMinutes(randomBetween(config.minRunMinutes(), config.maxRunMinutes())));
                restoreTitle();
                return;
            }

            if (Microbot.isLoggedIn()) {
                status = "Logging out for break";
                Rs2Player.logout();
            }
            return;
        }

        if (nextBreakAt == null) {
            nextBreakAt = now.plus(Duration.ofMinutes(randomBetween(config.minRunMinutes(), config.maxRunMinutes())));
            return;
        }

        Duration untilBreak = Duration.between(now, nextBreakAt);
        if (!untilBreak.isNegative() && untilBreak.getSeconds() <= BREAK_PREP_WINDOW_SECONDS) {
            status = "Preparing for break (walking to bank)";
            Rs2Bank.walkToBank();
        }

        if (!now.isBefore(nextBreakAt)) {
            breakActive = true;
            int breakMinutes = randomBetween(config.minBreakMinutes(), config.maxBreakMinutes());
            breakEndsAt = now.plus(Duration.ofMinutes(breakMinutes));
            status = "Starting break";
            Rs2Player.logout();
        }
    }

    private void updateClientTitle() {
        if (!breakActive || breakEndsAt == null) {
            return;
        }

        Duration remaining = Duration.between(Instant.now(), breakEndsAt);
        if (remaining.isNegative()) {
            remaining = Duration.ZERO;
        }

        long minutes = remaining.toMinutes();
        long seconds = remaining.minusMinutes(minutes).getSeconds();
        String title = "Microbot " + KSPAccountBuilderPlugin.VERSION + " | Break Duration: "
                + String.format("%02d:%02d", minutes, seconds);
        safeSetTitle(title);
    }

    private void restoreTitle() {
        if (originalTitle != null) {
            safeSetTitle(originalTitle);
        }
    }

    private String safeGetTitle() {
        try {
            return ClientUI.getFrame().getTitle();
        } catch (Exception ex) {
            return "RuneLite";
        }
    }

    private void safeSetTitle(String title) {
        try {
            ClientUI.getFrame().setTitle(title);
        } catch (Exception ignored) {
            // ignore UI title failures
        }
    }

    private int randomBetween(int min, int max) {
        int sanitizedMin = Math.max(1, Math.min(min, max));
        int sanitizedMax = Math.max(1, Math.max(min, max));
        return ThreadLocalRandom.current().nextInt(sanitizedMin, sanitizedMax + 1);
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
        breakActive = false;
        breakEndsAt = null;
        nextBreakAt = null;
        restoreTitle();
        combatScript.shutdown();
        Rs2AntibanSettings.naturalMouse = false;
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
