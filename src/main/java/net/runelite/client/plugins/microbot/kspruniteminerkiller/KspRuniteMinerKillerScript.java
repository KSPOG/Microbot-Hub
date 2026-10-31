package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.AnimationID;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.models.RS2Item;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Local-test-first Wilderness magic PK script.
 *
 * Design rules:
 * - Acquire only near configured Lava Maze mine, and only legal Wilderness targets.
 * - Never steal an existing ground stack: loot only after a matched ActorDeath.
 * - Bank tracked pickups, not the player's pre-existing gear, food, or runes.
 * - Walk to Ferox without relying on prohibited deep-Wilderness teleports.
 * - Do not use a randomized break/logout handler during dangerous PvP.
 */
@Slf4j
public final class KspRuniteMinerKillerScript extends Script
{
    private enum State { STARTING, PATROLLING, FIGHTING, LOOTING, TO_BANK, BANKING, RETURNING, STOPPED }

    private static final WorldPoint FEROX = BankLocation.FEROX_ENCLAVE.getWorldPoint();
    private static final long LOOP_MS = 275L;
    private static final long CAST_INTERVAL_MS = 2600L;
    private static final long ACTION_INTERVAL_MS = 550L;
    private static final long LOOT_WINDOW_MS = 14_000L;
    private static final long TARGET_LOST_MS = 5_000L;
    private static final long ROUTE_STALL_MS = 90_000L;

    // Normal Mining pickaxe animations (not Motherlode, stars, or other skilling).
    private static final Set<Integer> MINING_ANIMATIONS = new HashSet<>(Arrays.asList(
            AnimationID.MINING_BRONZE_PICKAXE,
            AnimationID.MINING_IRON_PICKAXE,
            AnimationID.MINING_STEEL_PICKAXE,
            AnimationID.MINING_BLACK_PICKAXE,
            AnimationID.MINING_MITHRIL_PICKAXE,
            AnimationID.MINING_ADAMANT_PICKAXE,
            AnimationID.MINING_RUNE_PICKAXE,
            AnimationID.MINING_GILDED_PICKAXE,
            AnimationID.MINING_DRAGON_PICKAXE,
            AnimationID.MINING_DRAGON_PICKAXE_UPGRADED,
            AnimationID.MINING_DRAGON_PICKAXE_OR,
            AnimationID.MINING_INFERNAL_PICKAXE,
            AnimationID.MINING_3A_PICKAXE,
            AnimationID.MINING_CRYSTAL_PICKAXE
    ));

    private volatile KspRuniteMinerKillerConfig config;
    private volatile State state = State.STOPPED;
    private volatile boolean stopping;
    private volatile String status = "Stopped";
    private volatile String targetName = "-";
    private volatile long startedAt;
    private volatile int deaths;
    private volatile int lootedStacks;
    private volatile int bankTrips;
    private volatile long lootedGp;
    private volatile long bankedGp;

    private WorldPoint mine;
    private WorldPoint lastTargetPosition;
    private volatile WorldPoint deathPosition;
    private volatile boolean targetDeathEvent;
    private boolean targetHasBeenAttacked;
    private long lastTargetSeen;
    private long targetStarted;
    private long nextActionAt;
    private long lootUntil;
    private long routeLastProgress;
    private WorldPoint routeLastPosition;
    private int bankActionFailures;

    private final Map<String, Long> recentlyEngaged = new HashMap<>();
    private final Map<String, Integer> preKillGround = new HashMap<>();
    // Only the quantities *actually collected* are eligible for depositing.
    private final Map<Integer, Integer> pendingBankLoot = new HashMap<>();
    private final Map<Integer, Integer> knownPrices = new HashMap<>();

    private int pendingLootId = -1;
    private int pendingLootBefore;
    private int pendingLootPrice;
    private long pendingLootAt;
    private int pendingDepositId = -1;
    private int pendingDepositBefore;
    private long pendingDepositAt;
    private int failedDepositAttempts;
    private int pendingWithdrawId = -1;
    private int pendingWithdrawBefore;
    private long pendingWithdrawAt;
    private int failedWithdrawalAttempts;

    @Inject
    public KspRuniteMinerKillerScript() {}

    public synchronized boolean start(KspRuniteMinerKillerConfig configuration)
    {
        shutdown();
        KspRuniteMinerKillerFeroxRoute.reset();
        KspRuniteMinerKillerDeathRecovery.reset();
        this.config = configuration;
        this.mine = new WorldPoint(configuration.mineX(), configuration.mineY(), 0);
        this.startedAt = System.currentTimeMillis();
        this.deaths = 0;
        this.lootedStacks = 0;
        this.bankTrips = 0;
        this.lootedGp = 0L;
        this.bankedGp = 0L;
        this.targetName = "-";
        this.status = "Starting";
        this.stopping = false;
        this.nextActionAt = 0L;
        this.targetDeathEvent = false;
        this.pendingLootId = -1;
        this.pendingDepositId = -1;
        this.failedDepositAttempts = 0;
        this.pendingWithdrawId = -1;
        this.failedWithdrawalAttempts = 0;
        this.bankActionFailures = 0;
        this.pendingBankLoot.clear();
        this.preKillGround.clear();
        this.knownPrices.clear();
        this.recentlyEngaged.clear();
        this.state = State.STARTING;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (stopping || !Microbot.isLoggedIn()) return;
            try
            {
                // Consume food independently of the Script's normal pause gate.
                if (Rs2Player.getHealthPercentage() <= config.eatAtPercent()
                        && Rs2Player.eatAt(config.eatAtPercent()))
                {
                    nextActionAt = System.currentTimeMillis() + 550L;
                    return;
                }
                if (!super.run()) {
                    status = KspRuniteMinerKillerFeroxRoute.onMicrobotGuardBlocked(status);
                    return;
                }
                step();
            }
            catch (Throwable ex)
            {
                // NoSuchMethodError, IncompatibleClassChangeError etc. are Errors,
                // not Exceptions. Never allow one to silently kill the scheduled loop.
                log.error("KSP Runite Miner Killer loop failed", ex);
                fail("Script error (see client.log): " + ex.getClass().getSimpleName());
            }
        }, 0L, LOOP_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void step()
    {
        final long now = System.currentTimeMillis();
        // Death recovery MUST preempt normal cooldowns, banking, fighting and walking.
        // Do not keep following an old Wilderness path after Lumbridge respawn.
        if (KspRuniteMinerKillerDeathRecovery.active()) {
            String recovery = KspRuniteMinerKillerDeathRecovery.advance(Rs2Player.getWorldLocation(), now);
            if (recovery == null) {
                WorldPoint atFerox = Rs2Player.getWorldLocation();
                if (atFerox != null) {
                    enterBankingRoute(atFerox);
                    status = "Death recovery: resupplying at Ferox bank";
                }
            } else if (recovery.startsWith("FAILED: ")) {
                fail(recovery.substring(8));
            } else {
                status = recovery;
            }
            return;
        }
        if (now < nextActionAt) return;

        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) { status = "Waiting for player position"; return; }

        // An unskulled player still has real Wilderness risk. Prefer disengagement to a bad death.
        if (state != State.TO_BANK && state != State.BANKING && state != State.RETURNING
                && state != State.STARTING && Rs2Player.getHealthPercentage() <= config.fleeAtPercent())
        {
            status = "Low health - disengaging to Ferox";
            clearTarget();
            enterBankingRoute(here);
            return;
        }

        switch (state)
        {
            case STARTING:
                if (mine.getX() < 1 || mine.getY() < 1 || mine.getPlane() != 0)
                {
                    fail("Invalid mine coordinates in the plugin settings.");
                    return;
                }
                Rs2CombatSpells spell = selectedSpell();
                if (!spell.hasRequirements())
                {
                    fail("Requires the standard spellbook and " + spell.getRequiredLevel()
                            + " Magic for " + spellName() + ".");
                    return;
                }
                if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
                if (Rs2Pvp.isInWilderness() && !KspRuniteMinerKillerFeroxRoute.atFerox(here))
                {
                    if (!KspRuniteMinerKillerFeroxPrep.wildernessReady(config))
                    {
                        status = "Wilderness loadout/supplies incomplete - preparing at Ferox";
                        enterBankingRoute(here);
                        return;
                    }
                    state = State.PATROLLING;
                    status = "Wilderness loadout ready - skipping preparation";
                    break;
                }
                state = State.PATROLLING;
                status = "Checking supplies";
                break;
            case PATROLLING:
                patrol(here, now);
                break;
            case FIGHTING:
                fight(now);
                break;
            case LOOTING:
                loot(now);
                break;
            case TO_BANK:
                goToBank(here, now);
                break;
            case BANKING:
                bank(now);
                break;
            case RETURNING:
                returnToMine(here, now);
                break;
            default:
                break;
        }
    }

    private void patrol(WorldPoint here, long now)
    {
        if (!hasEnoughCasts() || Rs2Inventory.getInventoryFood().size() < 2)
        {
            status = "Resupplying before PvP";
            enterBankingRoute(here);
            return;
        }
        if (!pendingBankLoot.isEmpty() && Rs2Inventory.emptySlotCount() <= config.bankAtFreeSlots())
        {
            status = "Inventory nearing capacity";
            enterBankingRoute(here);
            return;
        }
        if (here.distanceTo(mine) > Math.max(6, config.mineRadius()))
        {
            state = State.RETURNING;
            status = "Walking to Lava Maze runite mine";
            // Defer to RETURNING so the Castle Wars transfer is selected first.
            return;
        }
        if (!Rs2Pvp.isInWilderness())
        {
            status = "Mine approach - not yet in Wilderness";
            return;
        }

        Rs2PlayerModel selected = findTarget(null, false);
        if (selected == null)
        {
            String hopStatus = KspRuniteMinerKillerWorldHop.tick(now);
            status = hopStatus == null ? "Scanning live scene for runite miners" : hopStatus;
            targetName = "-";
            return;
        }

        KspRuniteMinerKillerWorldHop.targetSeen();
        targetName = selected.getName();
        lastTargetPosition = selected.getWorldLocation();
        lastTargetSeen = now;
        targetStarted = now;
        targetHasBeenAttacked = false;
        targetDeathEvent = false;
        deathPosition = null;
        pendingLootId = -1;
        preKillGround.clear();
        preKillGround.putAll(groundSnapshot(lastTargetPosition, config.lootRadius() + 1));
        state = State.FIGHTING;
        status = "Locked target " + targetName;
    }

    private void fight(long now)
    {
        if (targetDeathEvent)
        {
            startLootWindow(now);
            return;
        }
        if (now - targetStarted > 90_000L)
        {
            rejectTarget(now, "Combat timeout - re-evaluating targets");
            return;
        }
        if (!hasEnoughCasts())
        {
            status = "Out of safe spell reserve";
            enterBankingRoute(Rs2Player.getWorldLocation());
            return;
        }
        Rs2PlayerModel player = findTarget(targetName, true);
        if (player == null)
        {
            if (now - lastTargetSeen >= TARGET_LOST_MS)
            {
                rejectTarget(now, "Target left scene / hopped");
            }
            else
            {
                status = "Waiting for target visibility";
            }
            return;
        }

        WorldPoint current = player.getWorldLocation();
        if (current == null || current.distanceTo(mine) > config.mineRadius() + 9)
        {
            rejectTarget(now, "Target left hunting boundary");
            return;
        }

        lastTargetSeen = now;
        if (lastTargetPosition != null && current.distanceTo(lastTargetPosition) >= 3)
        {
            groundSnapshot(current, config.lootRadius()).forEach((key, count) ->
                    preKillGround.merge(key, count, Math::max));
        }
        lastTargetPosition = current;
        if (KspRuniteMinerKillerCombatSetup.attack(selectedSpell(), player.getPlayer()))
        {
            targetHasBeenAttacked = true;
            status = "Autocast attacking " + targetName;
            nextActionAt = now + CAST_INTERVAL_MS;
        }
        else
        {
            status = "Autocast attack unavailable - checking readiness";
            nextActionAt = now + 1500L;
        }
    }

    private void rejectTarget(long now, String reason)
    {
        if (!"-".equals(targetName)) recentlyEngaged.put(targetName.toLowerCase(Locale.ROOT), now + 45_000L);
        clearTarget();
        state = State.PATROLLING;
        status = reason;
    }

    /** Called on the RuneLite client thread by the plugin event subscriber. */
    public void onActorDeath(Actor actor)
    {
        // RuneLite calls onActorDeath on the client thread, so identity is safe to read.
        // Local-player death is not a successful PK; it cancels the old route and
        // clears in-flight loot/bank transactions before respawn recovery begins.
        if (KspRuniteMinerKillerDeathRecovery.recordDeath(actor)) {
            clearTarget();
            pendingBankLoot.clear();
            recentlyEngaged.clear();
            pendingLootId = pendingDepositId = pendingWithdrawId = -1;
            failedDepositAttempts = failedWithdrawalAttempts = bankActionFailures = 0;
            deathPosition = null;
            lastTargetPosition = null;
            lootUntil = 0L;
            nextActionAt = 0L;
            routeLastPosition = null;
            routeLastProgress = 0L;
            state = State.RETURNING;
            status = "Death detected - waiting for respawn";
            return;
        }
        if (!(actor instanceof Player)) return;
        String name = actor.getName();
        if (name == null || !name.equalsIgnoreCase(targetName)
                || state != State.FIGHTING || !targetHasBeenAttacked || targetDeathEvent)
        {
            return;
        }
        deathPosition = actor.getWorldLocation();
        targetDeathEvent = true;
    }

    private void startLootWindow(long now)
    {
        targetDeathEvent = false;
        WorldPoint deadAt = deathPosition != null ? deathPosition : lastTargetPosition;
        if (deadAt == null || deadAt.distanceTo(mine) > config.mineRadius() + 9)
        {
            rejectTarget(now, "Death was outside permitted loot boundary");
            return;
        }
        lastTargetPosition = deadAt;
        deaths++;
        status = "Death observed - checking new drops";
        recentlyEngaged.put(targetName.toLowerCase(Locale.ROOT), now + 90_000L);
        state = State.LOOTING;
        lootUntil = now + LOOT_WINDOW_MS;
        pendingLootId = -1;
    }

    private void loot(long now)
    {
        if (resolvePendingLoot(now)) return;
        if (Rs2Inventory.emptySlotCount() <= 0)
        {
            status = "Inventory full - banking pickups";
            clearTarget();
            enterBankingRoute(Rs2Player.getWorldLocation());
            return;
        }
        WorldPoint corpse = lastTargetPosition;
        if (corpse == null) { finishLoot(); return; }
        if (now >= lootUntil)
        {
            finishLoot();
            return;
        }

        RS2Item target = selectNewGroundItem(corpse);
        if (target == null)
        {
            status = "Waiting for visible death loot";
            return;
        }

        int itemId = target.getItem().getId();
        int oldQuantity = Rs2Inventory.itemQuantity(itemId);
        int unitPrice = itemPrice(itemId);
        if (Rs2GroundItem.interact(target))
        {
            pendingLootId = itemId;
            pendingLootBefore = oldQuantity;
            pendingLootPrice = unitPrice;
            pendingLootAt = now;
            status = "Taking " + target.getItem().getName();
            nextActionAt = now + 700L;
        }
        else
        {
            nextActionAt = now + ACTION_INTERVAL_MS;
        }
    }

    /** Confirm pickup by inventory delta, not by a successful click return value. */
    private boolean resolvePendingLoot(long now)
    {
        if (pendingLootId < 0) return false;
        int id = pendingLootId;
        int gained = Rs2Inventory.itemQuantity(id) - pendingLootBefore;
        if (gained > 0)
        {
            lootedStacks++;
            lootedGp += (long) gained * pendingLootPrice;
            pendingBankLoot.merge(id, gained, Integer::sum);
            // A successful pickup can leave other eligible stacks on the ground.
            pendingLootId = -1;
            status = "Looted +" + gained + " item(s)";
            return true;
        }
        if (now - pendingLootAt < 3500L)
        {
            status = "Confirming pickup";
            return true;
        }
        // The item may not have become lootable yet. Don't credit a failed pickup.
        pendingLootId = -1;
        nextActionAt = now + 600L;
        return true;
    }

    private void finishLoot()
    {
        clearTarget();
        if (!pendingBankLoot.isEmpty() && config.bankAfterKill())
        {
            enterBankingRoute(Rs2Player.getWorldLocation());
        }
        else
        {
            state = State.PATROLLING;
            status = "Loot window closed - scanning";
        }
    }

    private RS2Item selectNewGroundItem(WorldPoint corpse)
    {
        List<RS2Item> items = new ArrayList<>();
        for (RS2Item item : Rs2GroundItem.getAllFromWorldPoint(config.lootRadius(), corpse))
        {
            if (item == null || item.getItem() == null || item.getTile() == null || item.getTileItem() == null) continue;
            WorldPoint position = item.getTile().getWorldLocation();
            if (position == null || position.distanceTo(corpse) > config.lootRadius()) continue;
            if (position.distanceTo(mine) > config.mineRadius() + 9) continue;
            int id = item.getItem().getId();
            int currentStack = Math.max(0, item.getTileItem().getQuantity());
            // A spawn is considered new only if it exceeds the snapshot taken BEFORE our attack.
            if (!KspRuniteMinerKillerPolicy.isNewLootStack(
                    preKillGround.getOrDefault(itemKey(id, position), 0), currentStack)) continue;
            long value = (long) itemPrice(id) * currentStack;
            if (value < config.minimumLootValue()) continue;
            items.add(item);
        }
        items.sort(Comparator.comparingLong((RS2Item i) ->
                (long) itemPrice(i.getItem().getId()) * Math.max(1, i.getTileItem().getQuantity())).reversed());
        return items.isEmpty() ? null : items.get(0);
    }

    private Map<String, Integer> groundSnapshot(WorldPoint origin, int range)
    {
        Map<String, Integer> snapshot = new HashMap<>();
        if (origin == null) return snapshot;
        for (RS2Item item : Rs2GroundItem.getAllFromWorldPoint(range, origin))
        {
            if (item == null || item.getItem() == null || item.getTile() == null || item.getTileItem() == null) continue;
            snapshot.merge(itemKey(item.getItem().getId(), item.getTile().getWorldLocation()),
                    Math.max(0, item.getTileItem().getQuantity()), Math::max);
        }
        return snapshot;
    }

    private static String itemKey(int id, WorldPoint point)
    {
        return id + ":" + point.getX() + ":" + point.getY() + ":" + point.getPlane();
    }

    private int itemPrice(int itemId)
    {
        if (itemId == 995) return 1;
        Integer cached = knownPrices.get(itemId);
        if (cached != null) return cached;
        int price = Microbot.getClientThread().runOnClientThreadOptional((java.util.concurrent.Callable<Integer>) () ->
                Microbot.getItemManager().getItemPrice(itemId)).orElse(0);
        price = Math.max(0, price);
        knownPrices.put(itemId, price);
        return price;
    }

    private List<Rs2PlayerModel> scenePlayers()
    {
        return Microbot.getClientThread().runOnClientThreadOptional((java.util.concurrent.Callable<List<Rs2PlayerModel>>) () ->
        {
            if (Microbot.getClient().getLocalPlayer() == null) return List.<Rs2PlayerModel>of();
            Player local = Microbot.getClient().getLocalPlayer();
            List<Rs2PlayerModel> players = new ArrayList<>();
            // IMPORTANT: IndexedObjectSet is an INTERFACE (not a class) on Microbot 2.6.43.
            // Compile this call to invokeinterface IndexedObjectSet.stream().
            Microbot.getClient().getTopLevelWorldView().players().stream()
                    .filter(p -> p != null && p != local)
                    .map(Rs2PlayerModel::new)
                    .forEach(players::add);
            return players;
        }).orElse(List.of());
    }

    private Rs2PlayerModel findTarget(String exactName, boolean keepCurrent)
    {
        if (!Rs2Pvp.isInWilderness()) return null;
        WorldPoint self = Rs2Player.getWorldLocation();
        if (self == null) return null;
        int radius = Math.max(2, config.mineRadius());
        long now = System.currentTimeMillis();
        Set<String> ignored = ignoredNames();
        List<Rs2PlayerModel> candidates = new ArrayList<>();

        for (Rs2PlayerModel player : scenePlayers())
        {
            String name = player.getName();
            WorldPoint where = player.getWorldLocation();
            if (name == null || name.isBlank() || where == null) continue;
            if (exactName != null && !name.equalsIgnoreCase(exactName)) continue;
            if (where.getPlane() != mine.getPlane() || Rs2Pvp.getWildernessLevelFrom(where) <= 0) continue;
            if (where.distanceTo(mine) > radius + (keepCurrent ? 9 : 0)) continue;
            if (where.distanceTo(self) > 16) continue;
            if (!Rs2Pvp.isAttackable(player)) continue;
            if (player.isFriend() || player.isClanMember() || player.isFriendsChatMember()) continue;
            String normal = name.toLowerCase(Locale.ROOT).trim();
            if (ignored.contains(normal)) continue;
            if (!keepCurrent && recentlyEngaged.getOrDefault(normal, 0L) > now) continue;
            if (!keepCurrent && config.requireMiningEvidence() && !looksLikeMiner(player)) continue;
            candidates.add(player);
        }
        candidates.sort(Comparator.comparingInt(p -> p.getWorldLocation().distanceTo(self)));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private boolean looksLikeMiner(Rs2PlayerModel player)
    {
        if (MINING_ANIMATIONS.contains(player.getAnimation())) return true;
        if (player.getPlayerComposition() == null) return false;
        try
        {
            return Rs2Player.getPlayerEquipmentNames(player).values().stream()
                    .filter(name -> name != null)
                    .anyMatch(name -> name.toLowerCase(Locale.ROOT).contains("pickaxe"));
        }
        catch (Exception ex)
        {
            // Fails closed. Some client revisions cannot resolve other players' equipment.
            return false;
        }
    }

    private Set<String> ignoredNames()
    {
        Set<String> names = new HashSet<>();
        for (String part : config.ignoredNames().split(","))
        {
            String name = part.trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }

    private void clearTarget()
    {
        targetName = "-";
        targetHasBeenAttacked = false;
        targetDeathEvent = false;
        pendingLootId = -1;
        preKillGround.clear();
    }

    private Rs2CombatSpells selectedSpell() { return config.spell().spell(); }

    private boolean hasEnoughCasts()
    {
        return selectedSpell().hasRequirements()
                && KspRuniteMinerKillerRuneSupply.hasRequiredRunes(selectedSpell(), config.minimumCasts());
    }

    private void enterBankingRoute(WorldPoint here)
    {
        // Pick one bank per trip. In a safe area, don't detour to Ferox when
        // already beside a bank (e.g. GE); wilderness trips still use Ferox.
        KspRuniteMinerKillerNavigation.chooseBank(here);
        state = State.TO_BANK;
        status = "Returning to selected bank";
        routeLastPosition = here;
        routeLastProgress = System.currentTimeMillis();
        nextActionAt = 0L;
    }

    private void goToBank(WorldPoint here, long now)
    {
        if (here.distanceTo(KspRuniteMinerKillerNavigation.bankTarget()) <= 7)
        {
            state = State.BANKING;
            status = "Opening selected bank";
            return;
        }
        status = "Walking to selected bank";
        travelTo(KspRuniteMinerKillerNavigation.bankTarget(), here, 4, now);
    }

    private void bank(long now)
    {
        if (!Rs2Bank.isOpen())
        {
            status = "Opening selected bank";
            if (!Rs2Bank.openBank() && ++bankActionFailures >= 8)
            {
                fail("Could not open selected bank after 8 attempts.");
            }
            nextActionAt = now + 550L;
            return;
        }
        bankActionFailures = 0;
        if (!Rs2Bank.setWithdrawAsItem())
        {
            status = "Setting unnoted withdrawals";
            nextActionAt = now + 450L;
            return;
        }

        // Complete a bank transfer only after inventory quantity actually changed.
        // Rs2Bank.depositX may return before the game's inventory update.
        if (pendingDepositId >= 0)
        {
            int id = pendingDepositId;
            int deposited = Math.max(0, pendingDepositBefore - Rs2Inventory.itemQuantity(id));
            if (deposited > 0)
            {
                bankedGp += (long) deposited * itemPrice(id);
                pendingBankLoot.merge(id, -deposited, Integer::sum);
                if (pendingBankLoot.getOrDefault(id, 0) <= 0) pendingBankLoot.remove(id);
                failedDepositAttempts = 0;
                pendingDepositId = -1;
            }
            else if (now - pendingDepositAt < 5_000L)
            {
                status = "Confirming bank deposit";
                return;
            }
            else
            {
                pendingDepositId = -1;
                if (++failedDepositAttempts >= 4)
                {
                    fail("Loot deposit did not appear in the bank after 4 attempts.");
                    return;
                }
            }
        }

        // Deposit only quantities picked up during this session, not the original inventory.
        for (Map.Entry<Integer, Integer> entry : new ArrayList<>(pendingBankLoot.entrySet()))
        {
            int id = entry.getKey();
            int count = KspRuniteMinerKillerPolicy.quantityToDeposit(
                    entry.getValue(), Rs2Inventory.itemQuantity(id));
            if (count <= 0)
            {
                pendingBankLoot.remove(id);
                continue;
            }
            status = "Banking looted item " + id;
            int beforeDeposit = Rs2Inventory.itemQuantity(id);
            if (Rs2Bank.depositX(id, count))
            {
                pendingDepositId = id;
                pendingDepositBefore = beforeDeposit;
                pendingDepositAt = System.currentTimeMillis();
            }
            else if (++failedDepositAttempts >= 4)
            {
                fail("Could not deposit looted item " + id + " after 4 attempts.");
            }
            nextActionAt = System.currentTimeMillis() + ACTION_INTERVAL_MS;
            return;
        }

        // Verify the previous withdrawal before issuing another. Bank actions can
        // complete asynchronously even when the API returns true immediately.
        if (pendingWithdrawId >= 0)
        {
            int increased = Rs2Inventory.itemQuantity(pendingWithdrawId) - pendingWithdrawBefore;
            if (increased > 0)
            {
                pendingWithdrawId = -1;
                failedWithdrawalAttempts = 0;
            }
            else if (now - pendingWithdrawAt < 5_000L)
            {
                status = "Confirming supply withdrawal";
                return;
            }
            else
            {
                pendingWithdrawId = -1;
                if (++failedWithdrawalAttempts >= 4)
                {
                    fail("Supply withdrawal was not confirmed after 4 attempts.");
                    return;
                }
            }
        }

        // Fill rune stock one kind at a time. Missing rune calculation accounts for
        // staff, equipped gear, rune pouch and combination-rune substitutions.
        for (Map.Entry<Runes, Integer> shortage : KspRuniteMinerKillerRuneSupply.getMissingRunes(selectedSpell(),
                Math.max(config.minimumCasts(), config.stockCasts())).entrySet())
        {
            int id = shortage.getKey().getItemId();
            int available = KspRuniteMinerKillerRuneSupply.bankQuantity(id);
            if (available <= 0) continue;
            int quantity = Math.min(available, shortage.getValue());
            if (quantity <= 0) continue;
            status = "Restocking " + shortage.getKey().name().toLowerCase(Locale.ROOT) + " runes";
            int before = Rs2Inventory.itemQuantity(id);
            if (Rs2Bank.withdrawX(id, quantity))
            {
                pendingWithdrawId = id;
                pendingWithdrawBefore = before;
                pendingWithdrawAt = System.currentTimeMillis();
            }
            else if (++failedWithdrawalAttempts >= 4)
            {
                fail("Failed to withdraw required runes from Ferox bank.");
            }
            nextActionAt = System.currentTimeMillis() + ACTION_INTERVAL_MS;
            return;
        }

        if (!hasEnoughCasts())
        {
            if (KspRuniteMinerKillerRuneSupply.shouldWaitForBankSnapshot())
            {
                status = "Waiting for a live bank rune snapshot";
                nextActionAt = now + 450L;
                return;
            }
            fail(KspRuniteMinerKillerRuneSupply.explain("Not enough runes for " + config.minimumCasts() + " casts of " + spellName()
                    + ". Checked available bank and carried runes/staves."));
            return;
        }

        String foodName = config.foodName().trim();
        int haveFood = Rs2Inventory.itemQuantity(foodName, true);
        int needFood = Math.max(0, config.stockFood() - haveFood);
        if (needFood > 0 && Rs2Bank.hasBankItem(foodName, true))
        {
            status = "Withdrawing " + foodName;
            int take = Math.min(needFood, Rs2Bank.count(item -> item.getName().equalsIgnoreCase(foodName)));
            if (take > 0)
            {
                // Resolve the item id from the bank entry to confirm exact inventory delta.
                net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel bankFood =
                        Rs2Bank.bankItems().stream()
                                .filter(i -> foodName.equalsIgnoreCase(i.getName()) && i.getQuantity() > 0)
                                .findFirst().orElse(null);
                int foodId = bankFood == null ? -1 : bankFood.getId();
                int before = foodId < 0 ? 0 : Rs2Inventory.itemQuantity(foodId);
                if (Rs2Bank.withdrawX(foodName, take, true) && foodId > 0)
                {
                    pendingWithdrawId = foodId;
                    pendingWithdrawBefore = before;
                    pendingWithdrawAt = System.currentTimeMillis();
                }
                else if (++failedWithdrawalAttempts >= 4)
                {
                    fail("Could not withdraw food from Ferox bank.");
                }
            }
            nextActionAt = System.currentTimeMillis() + ACTION_INTERVAL_MS;
            return;
        }
        if (Rs2Inventory.getInventoryFood().size() < 2)
        {
            fail("Not enough food in inventory/bank. Configured food: " + foodName);
            return;
        }

        if (!Rs2Bank.closeBank())
        {
            status = "Closing bank";
            nextActionAt = now + 450L;
            return;
        }
        bankTrips++;
        if (config.returnAfterBank())
        {
            state = State.RETURNING;
            routeLastProgress = now;
            routeLastPosition = Rs2Player.getWorldLocation();
            status = "Supplied - returning to runite mine";
        }
        else
        {
            fail("Banking complete - return to mine disabled (enable plugin again when ready).");
        }
        nextActionAt = now + 450L;
    }

    private void returnToMine(WorldPoint here, long now)
    {
        // Ferox loadout restoration and pool are mandatory before heading north.
        // Never run this while already hunting deep in the Wilderness.
        String preparation = KspRuniteMinerKillerFeroxPrep.advance(here, now, selectedSpell());
        if (preparation != null)
        {
            if (preparation.startsWith("FAILED: ")) fail(preparation.substring(8));
            else status = preparation;
            return;
        }
        // In safe areas use GE recruiters and Castle Wars' F2P exit to reach Ferox.
        // The helper owns each stage and waits for inventory/position deltas before retrying.
        String transferStatus = KspRuniteMinerKillerFeroxRoute.advance(here, now);
        if (transferStatus != null)
        {
            if (transferStatus.startsWith("FAILED: ")) fail(transferStatus.substring(8));
            else status = transferStatus;
            return;
        }
        if (here.distanceTo(mine) <= Math.max(3, config.mineRadius() - 2))
        {
            state = State.PATROLLING;
            status = "At runite mine - scanning";
            return;
        }
        status = "Walking back to Lava Maze mine";
        travelTo(mine, here, 3, now);
    }

    private void travelTo(WorldPoint destination, WorldPoint here, int radius, long now)
    {
        if (destination == null || here == null) return;
        if (routeLastPosition == null || routeLastProgress == 0L)
        {
            routeLastPosition = here;
            routeLastProgress = now;
        }
        if (here.distanceTo(routeLastPosition) >= 3)
        {
            routeLastPosition = here;
            routeLastProgress = now;
        }
        if (now - routeLastProgress > ROUTE_STALL_MS)
        {
            fail("Route stalled for 90 seconds. Check wilderness obstacles/path and client.log.");
            return;
        }
        // Force walking for this route only, restoring the shared walker flag
        // so another plugin is not left with teleports disabled after shutdown.
        boolean priorTeleportSetting = Rs2Walker.disableTeleports;
        try
        {
            Rs2Walker.disableTeleports = true;
            Rs2Walker.walkTo(destination, radius);
        }
        finally
        {
            Rs2Walker.disableTeleports = priorTeleportSetting;
        }
        nextActionAt = now + 1550L;
    }

    private void fail(String message)
    {
        log.warn("KSP Runite Miner Killer stopped: {}", message);
        status = "STOPPED: " + message;
        stopping = true;
        state = State.STOPPED;
        if (mainScheduledFuture != null) mainScheduledFuture.cancel(false);
    }

    @Override
    public synchronized void shutdown()
    {
        stopping = true;
        state = State.STOPPED;
        super.shutdown();
    }

    public String status() { return status; }
    public String target() { return targetName; }
    public String spellName() { return config == null ? "-" : config.spell().name().replace('_', ' '); }
    public int confirmedDeaths() { return deaths; }
    public int lootItems() { return lootedStacks; }
    public int bankTrips() { return bankTrips; }
    public long lootValue() { return lootedGp; }
    public long bankedValue() { return bankedGp; }
    public long runtimeMs() { return startedAt <= 0 ? 0 : System.currentTimeMillis() - startedAt; }
    public long lootValuePerHour()
    {
        long ms = runtimeMs();
        return ms < 1000L ? 0 : (long) (lootedGp * 3_600_000.0 / ms);
    }
}
