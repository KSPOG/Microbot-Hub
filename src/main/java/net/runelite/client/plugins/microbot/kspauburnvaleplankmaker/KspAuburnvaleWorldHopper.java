package net.runelite.client.plugins.microbot.kspauburnvaleplankmaker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
final class KspAuburnvaleWorldHopper {
    private static final int MAX_WORLD_POPULATION = 1_950;
    private static final long RETRY_DELAY_MILLIS = 10_000L;
    private static final long NO_WORLD_RETRY_MILLIS = 60_000L;
    private static final Pattern TOTAL_LEVEL_PATTERN = Pattern.compile("^(\\d+)\\s+.*", Pattern.CASE_INSENSITIVE);

    private final KspAuburnvalePlankMakerConfig config;

    private volatile long nextHopAtMillis = -1L;
    private volatile int lastSelectedWorld = -1;
    private volatile int pendingTargetWorld = -1;
    private volatile int pendingHopAttempts;

    KspAuburnvaleWorldHopper(KspAuburnvalePlankMakerConfig config) {
        this.config = config;
    }

    void start() {
        if (config.enableWorldHopper()) {
            scheduleNextHop();
        } else {
            nextHopAtMillis = -1L;
        }
    }

    /**
     * @return true when the hopper consumed this script pass (closing an interface,
     * attempting a hop, or waiting for a hop already in progress).
     */
    boolean tick() {
        if (!config.enableWorldHopper()) {
            nextHopAtMillis = -1L;
            return false;
        }
        if (nextHopAtMillis <= 0L) {
            scheduleNextHop();
            return false;
        }
        if (!Microbot.isLoggedIn() || KspAuburnvaleBreakService.shouldPause()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now < nextHopAtMillis) {
            return false;
        }
        int currentWorld = Microbot.getClient().getWorld();
        if (pendingTargetWorld > 0 && currentWorld == pendingTargetWorld) {
            int arrived = pendingTargetWorld;
            pendingTargetWorld = -1;
            pendingHopAttempts = 0;
            scheduleNextHop();
            Microbot.status = "World hopper: arrived on " + arrived;
            return true;
        }
        if (Microbot.isHopping()) {
            Microbot.status = "World hopper: switching worlds";
            return true;
        }

        // Let active travel/production/dialogue actions complete naturally. Blocking
        // those states here would strand their widgets because the main state machine
        // would no longer get a chance to finish them.
        if (Rs2Dialogue.isInDialogue() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            return false;
        }

        // Banking/GE are safe to close explicitly. Once closed, the next scheduler
        // pass performs the hop before the normal Fast Planker phase resumes.
        if (Rs2Bank.isOpen()) {
            Microbot.status = "World hopper: closing bank";
            Rs2Bank.closeBank();
            return true;
        }
        if (Rs2GrandExchange.isOpen() || Rs2GrandExchange.isOfferScreenOpen()) {
            Microbot.status = "World hopper: closing Grand Exchange";
            Rs2GrandExchange.closeExchange();
            return true;
        }

        if (!isMemberAccount()) {
            Microbot.status = "World hopper: members account required";
            nextHopAtMillis = now + NO_WORLD_RETRY_MILLIS;
            return true;
        }

        int target = pendingTargetWorld > 0 ? pendingTargetWorld : selectRandomEligibleMembersWorld();
        if (target <= 0) {
            Microbot.status = "World hopper: no eligible members world found";
            nextHopAtMillis = now + NO_WORLD_RETRY_MILLIS;
            return true;
        }

        int current = Microbot.getClient().getWorld();
        lastSelectedWorld = target;
        pendingTargetWorld = target;
        pendingHopAttempts++;
        Microbot.status = "World hopper: " + current + " -> " + target;
        log.info("Fast Planker world hop | from={} to={} totalLevel={} attempt={}",
                current, target, totalLevel(), pendingHopAttempts);

        boolean hopped = Microbot.hopToWorld(target);
        if (hopped && Microbot.getClient().getWorld() == target) {
            pendingTargetWorld = -1;
            pendingHopAttempts = 0;
            scheduleNextHop();
            Microbot.status = "World hopper: arrived on " + target;
            return true;
        }

        if (hopped || Microbot.isHopping()) {
            nextHopAtMillis = now + RETRY_DELAY_MILLIS;
            return true;
        }

        log.warn("Fast Planker world hop failed | from={} target={} attempt={}",
                current, target, pendingHopAttempts);
        if (pendingHopAttempts >= 3) {
            pendingTargetWorld = -1;
            pendingHopAttempts = 0;
            nextHopAtMillis = now + NO_WORLD_RETRY_MILLIS;
        } else {
            nextHopAtMillis = now + RETRY_DELAY_MILLIS;
        }
        return true;
    }

    long getTimeUntilNextHopSeconds() {
        if (!config.enableWorldHopper() || nextHopAtMillis <= 0L) return -1L;
        return Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(nextHopAtMillis - System.currentTimeMillis()));
    }

    int getLastSelectedWorld() {
        return lastSelectedWorld;
    }

    void shutdown() {
        nextHopAtMillis = -1L;
        lastSelectedWorld = -1;
        pendingTargetWorld = -1;
        pendingHopAttempts = 0;
    }

    private void scheduleNextHop() {
        int min = Math.max(5, Math.min(300, config.worldHopMinMinutes()));
        int max = Math.max(5, Math.min(300, config.worldHopMaxMinutes()));
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        int delay = ThreadLocalRandom.current().nextInt(low, high + 1);
        nextHopAtMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delay);
        log.info("Fast Planker next world hop scheduled | minutes={} range={}..{}", delay, low, high);
    }

    private int selectRandomEligibleMembersWorld() {
        WorldResult result = Microbot.getWorldService().getWorlds();
        if (result == null || result.getWorlds() == null) {
            return -1;
        }

        int currentWorld = Microbot.getClient().getWorld();
        int totalLevel = totalLevel();
        List<World> eligible = new ArrayList<>();

        for (World world : result.getWorlds()) {
            if (world == null || world.getId() == currentWorld) continue;
            if (!world.getTypes().contains(WorldType.MEMBERS)) continue;
            if (world.getPlayers() < 0 || world.getPlayers() >= MAX_WORLD_POPULATION) continue;
            if (isPvPOrDangerWorld(world) || isUnsupportedSpecialWorld(world)) continue;
            if (!meetsTotalLevelRequirement(world, totalLevel)) continue;
            eligible.add(world);
        }

        if (eligible.isEmpty()) {
            return -1;
        }
        return eligible.get(ThreadLocalRandom.current().nextInt(eligible.size())).getId();
    }

    private boolean isPvPOrDangerWorld(World world) {
        return world.getTypes().contains(WorldType.PVP)
                || world.getTypes().contains(WorldType.HIGH_RISK)
                || world.getTypes().contains(WorldType.BOUNTY)
                || world.getTypes().contains(WorldType.LAST_MAN_STANDING);
    }


    private boolean isUnsupportedSpecialWorld(World world) {
        return world.getTypes().contains(WorldType.QUEST_SPEEDRUNNING)
                || world.getTypes().contains(WorldType.BETA_WORLD)
                || world.getTypes().contains(WorldType.DEADMAN)
                || world.getTypes().contains(WorldType.PVP_ARENA)
                || world.getTypes().contains(WorldType.TOURNAMENT)
                || world.getTypes().contains(WorldType.NOSAVE_MODE)
                || world.getTypes().contains(WorldType.LEGACY_ONLY)
                || world.getTypes().contains(WorldType.EOC_ONLY)
                || world.getTypes().contains(WorldType.FRESH_START_WORLD);
    }

    private boolean meetsTotalLevelRequirement(World world, int totalLevel) {
        if (!world.getTypes().contains(WorldType.SKILL_TOTAL)) {
            return true;
        }

        String activity = world.getActivity();
        if (activity == null) {
            return false;
        }
        Matcher matcher = TOTAL_LEVEL_PATTERN.matcher(activity.trim());
        if (!matcher.matches()) {
            log.warn("Fast Planker skipped total-level world {}: cannot parse activity '{}'",
                    world.getId(), activity);
            return false;
        }

        try {
            int requirement = Integer.parseInt(matcher.group(1));
            return totalLevel >= requirement;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private int totalLevel() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getTotalLevel()).orElse(0);
    }

    private boolean isMemberAccount() {
        try {
            return Rs2Player.isMember();
        } catch (Exception ex) {
            // This plugin's route is members-only, but never guess membership if the
            // client cannot confirm it. In that case, skip the hop and retry later.
            return false;
        }
    }
}
