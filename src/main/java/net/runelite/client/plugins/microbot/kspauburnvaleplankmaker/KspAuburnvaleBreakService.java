package net.runelite.client.plugins.microbot.kspauburnvaleplankmaker;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Fast-Planker-owned break scheduler.
 *
 * <p>This mirrors the KSP Account Builder/shared break semantics without depending
 * on the optional {@code net.runelite.client.plugins.microbot.kspsupport} package.
 * The service acquires the global pause only when it is free and releases only a
 * pause it acquired itself.</p>
 */
@Slf4j
final class KspAuburnvaleBreakService {
    private static final AtomicInteger ACTIVE_BREAKS = new AtomicInteger();
    private static final String AUTO_LOGIN_PLUGIN =
            "net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin";
    private static final String BREAK_HANDLER_PLUGIN =
            "net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin";

    private static final long COMBAT_GRACE_MILLIS = 11_000L;
    private static final long LOGOUT_RETRY_MILLIS = 3_000L;
    private static final long LOGIN_RETRY_MILLIS = 10_000L;

    private volatile ScheduledExecutorService timer;
    private volatile KspAuburnvalePlankMakerConfig config;
    private volatile long generation;

    private long nextBreakAtMillis = -1L;
    private long lastTickMillis;
    private long lastCombatAtMillis;
    private long breakEndsAtMillis;
    private long breakDurationMillis;
    private long lastActionAtMillis;
    private int returnWorld;
    private boolean breakEnabled;
    private boolean pauseOwned;
    private boolean logoutObserved;

    synchronized void start(KspAuburnvalePlankMakerConfig settings) {
        shutdown();
        config = settings;
        breakEnabled = settings != null && settings.doBreaks();
        lastTickMillis = System.currentTimeMillis();
        lastCombatAtMillis = 0L;
        breakEndsAtMillis = 0L;
        lastActionAtMillis = 0L;
        logoutObserved = false;

        if (breakEnabled) {
            stopPlugin(BREAK_HANDLER_PLUGIN);
            scheduleNextBreak(lastTickMillis);
        }

        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "Fast Planker break timer");
            thread.setDaemon(true);
            return thread;
        });
        timer.scheduleWithFixedDelay(this::tickSafely, 500L, 500L, TimeUnit.MILLISECONDS);
    }

    private void tickSafely() {
        if (timer == null) return;
        try {
            tick(System.currentTimeMillis());
        } catch (Exception ex) {
            log.warn("Fast Planker break timer failed; retrying", ex);
        }
    }

    private void tick(long now) {
        KspAuburnvalePlankMakerConfig current = config;
        long run = generation;
        if (current == null) return;

        long elapsed = Math.max(0L, now - lastTickMillis);
        lastTickMillis = now;

        boolean configuredEnabled = current.doBreaks();
        if (configuredEnabled != breakEnabled) {
            breakEnabled = configuredEnabled;
            if (breakEnabled) {
                stopPlugin(BREAK_HANDLER_PLUGIN);
                scheduleNextBreak(now);
            } else if (pauseOwned) {
                // Do not abandon a logged-out account. End the wait and recover login first.
                breakEndsAtMillis = now;
            } else {
                nextBreakAtMillis = -1L;
            }
        }

        if (Microbot.isLoggedIn() && Rs2Combat.inCombat()) {
            lastCombatAtMillis = now;
        }

        if (!pauseOwned) {
            if (!breakEnabled) return;

            // Time spent logged out or paused by another owner does not consume this
            // plugin's runtime-to-break countdown.
            if (!Microbot.isLoggedIn() || shouldPause()) {
                if (nextBreakAtMillis > 0L) nextBreakAtMillis += elapsed;
                return;
            }

            if (now < nextBreakAtMillis || tradeScreenOpen()) return;

            synchronized (this) {
                if (generation != run || config != current
                        || !Microbot.pauseAllScripts.compareAndSet(false, true)) {
                    return;
                }
                pauseOwned = true;
                ACTIVE_BREAKS.incrementAndGet();
            }

            returnWorld = Microbot.getClient().getWorld();
            breakDurationMillis = randomMinutes(
                    current.breakDurationMinMinutes(),
                    current.breakDurationMaxMinutes(),
                    1, 180);
            breakEndsAtMillis = 0L;
            logoutObserved = false;
            lastActionAtMillis = 0L;
            if (generation != run) return;
            Rs2Walker.setTarget(null);
            log.info("Fast Planker break starting | returnWorld={} durationMinutes={}",
                    returnWorld, TimeUnit.MILLISECONDS.toMinutes(breakDurationMillis));
        }

        if (generation != run || !pauseOwned) return;

        // Prevent a separately-enabled AutoLogin plugin from shortening the break.
        stopPlugin(AUTO_LOGIN_PLUGIN);

        if (!logoutObserved) {
            if (!breakEnabled && Microbot.isLoggedIn()) {
                finishBreak(now, run);
                return;
            }

            if (Microbot.isLoggedIn()) {
                Microbot.status = "Break: waiting to log out safely";
                if (Rs2Combat.inCombat() || now - lastCombatAtMillis < COMBAT_GRACE_MILLIS) return;
                if (now - lastActionAtMillis >= LOGOUT_RETRY_MILLIS) {
                    lastActionAtMillis = now;
                    if (generation == run) Rs2Player.logout();
                }
                return;
            }

            logoutObserved = true;
            breakEndsAtMillis = breakEnabled ? now + breakDurationMillis : now;
            lastActionAtMillis = 0L;
        }

        if (breakEnabled && now < breakEndsAtMillis) {
            long remainingSeconds = Math.max(0L, (breakEndsAtMillis - now + 999L) / 1000L);
            Microbot.status = "Break: " + remainingSeconds + " seconds remaining";

            // If another helper logs us in during the scheduled break, put the account
            // back offline rather than silently shortening the break.
            if (Microbot.isLoggedIn()
                    && now - lastActionAtMillis >= LOGOUT_RETRY_MILLIS
                    && !Rs2Combat.inCombat()
                    && now - lastCombatAtMillis >= COMBAT_GRACE_MILLIS) {
                lastActionAtMillis = now;
                if (generation == run) Rs2Player.logout();
            }
            return;
        }

        Microbot.status = "Break finished: waiting to log in";

        if (Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY)) {
            if (now - lastActionAtMillis >= LOGOUT_RETRY_MILLIS) {
                lastActionAtMillis = now;
                if (generation == run) Rs2Widget.clickWidget(InterfaceID.WelcomeScreen.PLAY);
            }
            return;
        }

        if (Microbot.isLoggedIn()
                && Microbot.getClient().getGameState() == GameState.LOGGED_IN
                && Microbot.getClient().getLocalPlayer() != null) {
            finishBreak(now, run);
            return;
        }

        if (Microbot.getClient().getGameState() != GameState.LOGIN_SCREEN
                || now - lastActionAtMillis < LOGIN_RETRY_MILLIS) {
            return;
        }

        int loginIndex = Microbot.getClient().getLoginIndex();
        if (loginIndex == 4 || loginIndex == 14 || loginIndex == 34) return;
        if (LoginManager.getActiveProfile() == null || LoginManager.isLoginAttemptActive()) return;

        lastActionAtMillis = now;
        if (generation == run) {
            LoginManager.login(returnWorld);
        }
    }

    private boolean tradeScreenOpen() {
        try {
            return Rs2Widget.isWidgetVisible(InterfaceID.Trademain.ACCEPT)
                    || Rs2Widget.isWidgetVisible(InterfaceID.Tradeconfirm.TRADE2ACCEPT);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void scheduleNextBreak(long now) {
        KspAuburnvalePlankMakerConfig current = config;
        if (current == null || !current.doBreaks()) {
            nextBreakAtMillis = -1L;
            return;
        }
        nextBreakAtMillis = now + randomMinutes(
                current.breakAfterMinMinutes(),
                current.breakAfterMaxMinutes(),
                5, 300);
        log.info("Fast Planker next break scheduled | minutes={}",
                TimeUnit.MILLISECONDS.toMinutes(Math.max(0L, nextBreakAtMillis - now)));
    }

    private static long randomMinutes(int first, int second, int minimum, int maximum) {
        int low = Math.max(minimum, Math.min(maximum, Math.min(first, second)));
        int high = Math.max(low, Math.max(minimum, Math.min(maximum, Math.max(first, second))));
        return TimeUnit.MINUTES.toMillis(ThreadLocalRandom.current().nextInt(low, high + 1));
    }

    private synchronized void finishBreak(long now, long run) {
        if (generation != run) return;
        releaseOwnedPause();
        logoutObserved = false;
        breakEndsAtMillis = 0L;
        lastActionAtMillis = 0L;
        scheduleNextBreak(now);
        Microbot.status = "Break finished";
        log.info("Fast Planker break finished | world={}",
                Microbot.getClient() == null ? -1 : Microbot.getClient().getWorld());
    }

    private synchronized void releaseOwnedPause() {
        if (!pauseOwned) return;
        Microbot.pauseAllScripts.compareAndSet(true, false);
        pauseOwned = false;
        ACTIVE_BREAKS.decrementAndGet();
    }

    synchronized void shutdown() {
        generation++;
        config = null;
        if (timer != null) {
            timer.shutdownNow();
            timer = null;
        }
        releaseOwnedPause();
        nextBreakAtMillis = -1L;
        breakEndsAtMillis = 0L;
        logoutObserved = false;
    }

    static boolean isBreakActive() {
        return ACTIVE_BREAKS.get() > 0;
    }

    static boolean shouldPause() {
        try {
            return isBreakActive()
                    || Microbot.pauseAllScripts.get()
                    || BreakHandlerScript.isBreakActive()
                    || BreakHandlerScript.isMicroBreakActive();
        } catch (Exception ex) {
            return isBreakActive() || Microbot.pauseAllScripts.get();
        }
    }

    private static void stopPlugin(String className) {
        try {
            Plugin plugin = Microbot.getPlugin(className);
            if (plugin != null && Microbot.isPluginEnabled(plugin)) {
                Microbot.stopPlugin(plugin);
            }
        } catch (Exception ex) {
            log.debug("Fast Planker could not stop plugin {}: {}", className, ex.getMessage());
        }
    }
}
