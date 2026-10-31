package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import java.util.stream.IntStream;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Safe-area F2P travel: Grand Exchange -> Castle Wars recruiters -> team waiting room
 * -> exit portal -> Castle Wars main lobby -> double doors -> Ferox Enclave.
 *
 * Intentional constraints: no dropping items, no depositing arbitrary equipment or inventory;
 * no walking through Wilderness as a fallback; no repeated clicks while awaiting transfers.
 * Does not activate while in the Wilderness away from Castle Wars/Ferox.
 */
public final class KspRuniteMinerKillerFeroxRoute {
    private static final Logger log = LoggerFactory.getLogger(KspRuniteMinerKillerFeroxRoute.class);
    private static final WorldPoint GE = new WorldPoint(3164, 3487, 0);
    // User-observed recruiter positions: WorldArea(3139, 3468, 0, 9, 8).
    // The old (3154, 3480) point was outside this area and the 9-tile
    // arrival tolerance incorrectly treated the GE bank as close enough.
    private static final int RECRUITER_MIN_X = 3139;
    private static final int RECRUITER_MAX_X = 3147;
    private static final int RECRUITER_MIN_Y = 3468;
    private static final int RECRUITER_MAX_Y = 3475;
    private static final WorldPoint RECRUITER_CENTRE = new WorldPoint(3143, 3472, 0);
    private static final long RECRUITER_SCENE_TIMEOUT_MS = 45_000L;
    private static final WorldPoint FEROX = new WorldPoint(3130, 3631, 0);
    // Verified Castle Wars main-lobby double doors (F2P -> Ferox Enclave).
    private static final int[] FEROX_DOOR_IDS = { 30387, 30388 };
    private static final WorldPoint FEROX_DOOR_APPROACH = new WorldPoint(2443, 3089, 0);
    private static final long PREPARATION_RECHECK_MS = 2000L;
    private static final int[] RECRUITER_IDS = { 8644, 10732 };
    private static final EquipmentInventorySlot[] CLEAR_SLOTS = {
        EquipmentInventorySlot.HEAD, EquipmentInventorySlot.CAPE
    };
    private static final long ACTION_GAP = 600L;
    private static final long TRANSFER_WAIT = 8500L;
    private static final long INVENTORY_WAIT = 4500L;
    private static final int MAX_ACTION_FAILURES = 5;

    private enum Phase {
        UNSTARTED, WALK_GE, REMOVE_EQUIPMENT, WAIT_REMOVE, OPEN_BANK,
        DEPOSIT_REMOVED, WAIT_DEPOSIT, DEPOSIT_FOOD, WAIT_FOOD_DEPOSIT, CLOSE_BANK, WALK_RECRUITERS,
        JOIN_TEAM, WAIT_RECRUITER_CLICK, WAIT_RECRUITER, EXIT_WAITING_ROOM, WAIT_EXIT,
        FEROX_DOORS, WAIT_FEROX, COMPLETE, FAILED
    }

    private static Phase phase = Phase.UNSTARTED;
    private static long notBefore;
    private static long actionAt;
    private static int failures;
    private static int slotCursor;
    private static int recruiterCursor;
    private static long missingRecruiterSince;
    private static long lastRecruiterScanLog;
    private static long lastPreparationCheckAt;
    private static long lastRecruiterWalkAt;
    private static long lastRecruiterRepositionAt;
    private static int recruiterRepositionCount;
    // Scanning the live client scene must not block the script's route executor.
    // The client-thread callback creates a fresh observation, never a persistent NPC cache.
    /** Immutable values are populated on RuneLite's client thread. Script code must
     * never call NPC.getName/getId, even solely for debug logging. */
    private static final class RecruiterObservation {
        final NPC npc;
        final int id;
        final String name;
        final long observedAt;
        RecruiterObservation(NPC npc, int id, String name, long observedAt) {
            this.npc = npc; this.id = id; this.name = name; this.observedAt = observedAt;
        }
    }
    private static volatile RecruiterObservation sceneRecruiter;
    private static volatile int clickSequence;
    private static volatile boolean clickFinished;
    private static volatile boolean clickIssued;
    private static volatile String clickError = "";
    private static String clickRecruiterName = "";
    private static int clickRecruiterId;
    private static long clickRequestedAt;
    private static final long CLICK_CALLBACK_TIMEOUT_MS = 8000L;
    private static volatile long sceneObservedAt;
    private static volatile boolean sceneScanPending;
    private static long sceneScanRequestedAt;
    private static volatile int sceneGeneration;
    private static long lastSceneErrorLog;
    private static long lastGuardWarningAt;
    private static int[] removedIds = { -1, -1 };
    private static int[] removedBefore = { 0, 0 };
    private static int inventoryBefore;
    private static int depositingFoodId;
    private static boolean freeSpaceForEquipment;
    private static String error = "";

    private KspRuniteMinerKillerFeroxRoute() { }

    /** Per plugin start; never resume a stale transfer attempt after a restart. */
    public static synchronized void reset() {
        phase = Phase.UNSTARTED;
        notBefore = 0;
        actionAt = 0;
        failures = 0;
        slotCursor = 0;
        recruiterCursor = 0;
        missingRecruiterSince = 0L;
        lastRecruiterScanLog = 0L;
        lastPreparationCheckAt = 0L;
        lastRecruiterWalkAt = 0L;
        lastRecruiterRepositionAt = 0L;
        recruiterRepositionCount = 0;
        sceneGeneration++;
        clickSequence++;
        sceneRecruiter = null;
        clickFinished = false;
        clickIssued = false;
        clickError = "";
        clickRecruiterName = "";
        clickRecruiterId = 0;
        clickRequestedAt = 0L;
        sceneObservedAt = 0;
        sceneScanPending = false;
        sceneScanRequestedAt = 0;
        lastSceneErrorLog = 0;
        lastGuardWarningAt = 0;
        removedIds = new int[] { -1, -1 };
        removedBefore = new int[] { 0, 0 };
        inventoryBefore = 0;
        depositingFoodId = -1;
        freeSpaceForEquipment = false;
        error = "";
    }

    public static boolean atFerox(WorldPoint where) {
        return where != null && where.getPlane() == 0 && where.distanceTo(FEROX) <= 36;
    }

    private static boolean castleWaiting(WorldPoint p) {
        return p != null && p.getX() >= 2330 && p.getX() <= 2480
                && p.getY() >= 9400 && p.getY() <= 9600;
    }
    private static boolean castleLobby(WorldPoint p) {
        return p != null && p.getPlane() == 0 && p.getX() >= 2420 && p.getX() <= 2465
                && p.getY() >= 3065 && p.getY() <= 3120;
    }
    private static boolean nearGrandExchange(WorldPoint p) {
        return p != null && p.getPlane() == 0 && p.distanceTo(GE) <= 23;
    }

    /** Live inventory: consume no inventory slots when there is no Castle Wars prep to do.
     * Keep the explicit TROUT check even if an item-composition's action list is unavailable.
     * RuneLite's Rs2ItemModel.isFood covers other edible items (Eat or jug of wine).
     */
    private static int firstFoodId() {
        return Rs2Inventory.items()
                .filter(item -> item != null && (item.getId() == 333 || item.isFood()))
                .mapToInt(Rs2ItemModel::getId)
                .findFirst().orElse(-1);
    }

    private static boolean requiresGePreparation() {
        return Rs2Equipment.get(EquipmentInventorySlot.HEAD) != null
                || Rs2Equipment.get(EquipmentInventorySlot.CAPE) != null
                || firstFoodId() >= 0;
    }

    /** Only clears the two equipment slots and actual food. Non-food inventory stays intact. */
    private static void proceedToRecruiters(long now, String reason) {
        phase = Phase.WALK_RECRUITERS;
        failures = 0;
        missingRecruiterSince = 0L;
        lastRecruiterWalkAt = 0L;
        lastRecruiterScanLog = 0L;
        lastPreparationCheckAt = 0L;
        log.info("[KSP Runite Miner Killer] {} - locating Castle Wars recruiters in {}..{} / {}..{}",
                reason, RECRUITER_MIN_X, RECRUITER_MAX_X, RECRUITER_MIN_Y, RECRUITER_MAX_Y);
    }

    /** How far the player is outside the known rectangular NPC spawn region (Chebyshev). */
    static int distanceToRecruiterArea(WorldPoint here) {
        if (here == null || here.getPlane() != 0) return Integer.MAX_VALUE;
        int dx = Math.max(0, Math.max(RECRUITER_MIN_X - here.getX(), here.getX() - RECRUITER_MAX_X));
        int dy = Math.max(0, Math.max(RECRUITER_MIN_Y - here.getY(), here.getY() - RECRUITER_MAX_Y));
        return Math.max(dx, dy);
    }

    /**
     * Schedule one current-scene NPC scan for the NEXT client-thread opportunity.
     * Never synchronously wait for the client thread from the bot executor: previous
     * builds could freeze inside Rs2Npc.getNpc before logging any scan result.
     * Each observation expires in 2 seconds and is discarded after any reset.
     */
    private static void scheduleLiveRecruiterScan(long now) {
        if (sceneScanPending && now - sceneScanRequestedAt < 8000L) return;
        if (sceneScanPending) {
            sceneScanPending = false;
            if (now - lastSceneErrorLog >= 8000L) {
                log.warn("[KSP Runite Miner Killer] client thread did not complete recruiter scan within 8s");
                lastSceneErrorLog = now;
            }
        }
        if (now - sceneScanRequestedAt < 350L) return;
        sceneScanPending = true;
        sceneScanRequestedAt = now;
        final int generation = sceneGeneration;
        try {
            Microbot.getClientThread().invokeLater(() -> {
                RecruiterObservation found = null;
                try {
                    if (Microbot.getClient() != null &&
                            Microbot.getClient().getTopLevelWorldView() != null &&
                            Microbot.getClient().getTopLevelWorldView().npcs() != null) {
                        for (NPC npc : Microbot.getClient().getTopLevelWorldView().npcs()) {
                            if (npc == null) continue;
                            final int id = npc.getId();
                            final String name = npc.getName();
                            if (id == RECRUITER_IDS[0] || id == RECRUITER_IDS[1] ||
                                    "Saradominist recruiter".equalsIgnoreCase(name) ||
                                    "Zamorakian recruiter".equalsIgnoreCase(name)) {
                                found = new RecruiterObservation(npc, id, name, System.currentTimeMillis());
                                break;
                            }
                        }
                    }
                } catch (Throwable ex) {
                    log.warn("[KSP Runite Miner Killer] client-thread recruiter scene scan failed", ex);
                } finally {
                    // Do not acquire the route monitor on the client thread:
                    // advance() can own it while Rs2Walker.walkTo() is blocked,
                    // which would deadlock client clicks and route progress.
                    if (sceneGeneration == generation) {
                        sceneObservedAt = System.currentTimeMillis();
                        sceneRecruiter = found;
                        sceneScanPending = false;
                    }
                }
            });
        } catch (Throwable ex) {
            sceneScanPending = false;
            log.warn("[KSP Runite Miner Killer] could not queue live recruiter scan", ex);
        }
    }

    /** The base Microbot Script.run() guard can veto the next task tick.
     * Surface this as a paused status rather than leaving stale WALK_RECRUITERS.
     */
    public static String onMicrobotGuardBlocked(String oldStatus) {
        if (phase != Phase.WALK_RECRUITERS && phase != Phase.JOIN_TEAM &&
                phase != Phase.WAIT_RECRUITER_CLICK) return oldStatus;
        long now = System.currentTimeMillis();
        if (now - lastGuardWarningAt >= 8000L) {
            lastGuardWarningAt = now;
            log.warn("[KSP Runite Miner Killer] recruiter travel paused by Microbot Script.run() gate "
                    + "(pause/human input/blocking event); route state={}", phase);
        }
        return "Castle Wars travel paused by Microbot guard";
    }

    private static RecruiterObservation recentRecruiter() {
        RecruiterObservation candidate = sceneRecruiter;
        return candidate != null && System.currentTimeMillis() - candidate.observedAt < 2000L
                ? candidate : null;
    }

    private static String joinRecruiter(RecruiterObservation recruiter, long now) {
        // `NPC` is an opaque actor handle here. All NPC reads and the actual
        // interaction happen on the client thread, never on the script executor.
        clickRecruiterName = recruiter.name;
        clickRecruiterId = recruiter.id;
        clickRequestedAt = now;
        clickFinished = false;
        clickIssued = false;
        clickError = "";
        sceneRecruiter = null;
        phase = Phase.WAIT_RECRUITER_CLICK;
        notBefore = now + 300L;
        final int generation = sceneGeneration;
        final int sequence = ++clickSequence;
        try {
            Microbot.getClientThread().invokeLater(() -> {
                boolean issued = false;
                String problem = "";
                try {
                    if (sceneGeneration != generation || clickSequence != sequence) return;
                    // Actors from the previous scene become invalid during a world
                    // transition. Check we're still in the same scene before clicking.
                    if (Microbot.getClient() == null ||
                            Microbot.getClient().getTopLevelWorldView() == null ||
                            Microbot.getClient().getTopLevelWorldView().npcs() == null) {
                        problem = "Live NPC scene unavailable during recruiter click";
                    } else {
                        boolean stillPresent = false;
                        for (NPC actor : Microbot.getClient().getTopLevelWorldView().npcs()) {
                            if (actor == recruiter.npc) { stillPresent = true; break; }
                        }
                        if (stillPresent) issued = Rs2Npc.interact(recruiter.npc, "Join Castle Wars");
                        else problem = "Recruiter despawned or changed scenes before click";
                    }
                } catch (Throwable ex) {
                    problem = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
                    log.warn("[KSP Runite Miner Killer] recruiter interaction failed on client thread", ex);
                } finally {
                    if (sceneGeneration == generation && clickSequence == sequence) {
                        clickError = problem;
                        clickIssued = issued;
                        clickFinished = true;
                    }
                }
            });
            log.info("[KSP Runite Miner Killer] queued client-thread recruiter click: {} id={}",
                    recruiter.name, recruiter.id);
        } catch (Throwable ex) {
            clickError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            clickFinished = true;
            log.warn("[KSP Runite Miner Killer] could not queue recruiter interaction", ex);
        }
        return "Attempting Castle Wars recruiter: " + recruiter.name;
    }

    /**
     * @return null once at Ferox (or if unsafe to take the route), "FAILED: ..." on hard
     * failure, otherwise the current display status. Called only during RETURNING.
     */
    public static synchronized String advance(WorldPoint here, long now) {
        if (here == null) return "Waiting for world location";
        if (atFerox(here)) {
            if (phase != Phase.COMPLETE) log.info("[KSP Runite Miner Killer] arrived Ferox via Castle Wars: {}", here);
            phase = Phase.COMPLETE;
            return null;
        }
        // The recruiter route is safe-area only; do not try walking from deep Wilderness
        // to the Grand Exchange when the player is already north of Ferox.
        if (!castleWaiting(here) && !castleLobby(here) && here.getY() >= 3525) return null;
        if (phase == Phase.FAILED) return "FAILED: " + error;
        if (phase == Phase.COMPLETE) reset();
        if (phase == Phase.UNSTARTED) {
            phase = castleWaiting(here) ? Phase.EXIT_WAITING_ROOM
                    : castleLobby(here) ? Phase.FEROX_DOORS
                    : Phase.WALK_GE;
            log.info("[KSP Runite Miner Killer] GE -> Castle Wars -> Ferox route started from {}", here);
        }
        if (castleWaiting(here) && phase != Phase.WAIT_EXIT && phase != Phase.EXIT_WAITING_ROOM) {
            phase = Phase.EXIT_WAITING_ROOM;
            failures = 0;
        } else if (castleLobby(here) && phase != Phase.WAIT_FEROX && phase != Phase.FEROX_DOORS) {
            phase = Phase.FEROX_DOORS;
            failures = 0;
        }
        if (now < notBefore) return description();
        switch (phase) {
            case WALK_GE:
                // No helmet, cape or edible inventory: never detour to/open GE bank.
                if (!requiresGePreparation()) {
                    proceedToRecruiters(now, "No food/helmet/cape; GE bank skipped");
                    return description();
                }
                if (nearGrandExchange(here)) {
                    phase = Phase.REMOVE_EQUIPMENT;
                    slotCursor = 0;
                    return description();
                }
                walk(GE, 11);
                notBefore = now + ACTION_GAP;
                return "Walking to Grand Exchange for Castle Wars";
            case REMOVE_EQUIPMENT:
                if (slotCursor >= CLEAR_SLOTS.length) {
                    // Don't open GE bank when neither equipment nor food needs depositing.
                    if (removedIds[0] < 0 && removedIds[1] < 0 && firstFoodId() < 0) {
                        proceedToRecruiters(now, "Equipment slots already empty; GE bank skipped");
                    } else {
                        phase = Phase.OPEN_BANK;
                    }
                    failures = 0;
                    return description();
                }
                Rs2ItemModel equipped = Rs2Equipment.get(CLEAR_SLOTS[slotCursor]);
                if (equipped == null) {
                    slotCursor++;
                    return "Cape/helmet slot already empty";
                }
                if (Rs2Inventory.emptySlotCount() <= 0) {
                    // Free a slot by banking food first; never drop gear or lose it.
                    if (firstFoodId() < 0) return abort("Inventory full: cannot unequip " + equipped.getName());
                    freeSpaceForEquipment = true;
                    phase = Phase.OPEN_BANK;
                    return "Banking food to make room for Castle Wars equipment";
                }
                removedIds[slotCursor] = equipped.getId();
                removedBefore[slotCursor] = Rs2Inventory.itemQuantity(equipped.getId());
                if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
                if (!Rs2Equipment.interact(CLEAR_SLOTS[slotCursor], "Remove")) return retry(now, "Removing equipment");
                actionAt = now;
                phase = Phase.WAIT_REMOVE;
                notBefore = now + 180;
                log.info("[KSP Runite Miner Killer] unequipping slot {} itemId={}", CLEAR_SLOTS[slotCursor], removedIds[slotCursor]);
                return description();
            case WAIT_REMOVE:
                int expectedId = removedIds[slotCursor];
                if (Rs2Equipment.get(CLEAR_SLOTS[slotCursor]) == null
                        && Rs2Inventory.itemQuantity(expectedId) > removedBefore[slotCursor]) {
                    failures = 0;
                    slotCursor++;
                    phase = Phase.REMOVE_EQUIPMENT;
                    return description();
                }
                if (now - actionAt > INVENTORY_WAIT) {
                    // Don't lose ownership of an item which may have unequipped late.
                    if (Rs2Equipment.get(CLEAR_SLOTS[slotCursor]) == null) return abort("Removed gear not confirmed in inventory");
                    removedIds[slotCursor] = -1;
                    phase = Phase.REMOVE_EQUIPMENT;
                    return retry(now, "Equipment removal not confirmed");
                }
                return description();
            case OPEN_BANK:
                if (Rs2Bank.isOpen()) {
                    slotCursor = 0;
                    failures = 0;
                    phase = freeSpaceForEquipment ? Phase.DEPOSIT_FOOD : Phase.DEPOSIT_REMOVED;
                    return description();
                }
                if (!Rs2Bank.openBank()) return retry(now, "Opening GE bank");
                notBefore = now + ACTION_GAP;
                return description();
            case DEPOSIT_REMOVED:
                if (!Rs2Bank.isOpen()) { phase = Phase.OPEN_BANK; return description(); }
                if (slotCursor >= removedIds.length) {
                    phase = Phase.DEPOSIT_FOOD;
                    return description();
                }
                if (removedIds[slotCursor] < 0) { slotCursor++; return description(); }
                int id = removedIds[slotCursor];
                if (Rs2Inventory.itemQuantity(id) <= removedBefore[slotCursor])
                    return abort("Cannot locate removed item " + id + " for depositing");
                inventoryBefore = Rs2Inventory.itemQuantity(id);
                if (!Rs2Bank.depositX(id, 1)) return retry(now, "Depositing removed equipment");
                actionAt = now;
                phase = Phase.WAIT_DEPOSIT;
                notBefore = now + 180;
                return description();
            case WAIT_DEPOSIT:
                if (Rs2Inventory.itemQuantity(removedIds[slotCursor]) < inventoryBefore) {
                    log.info("[KSP Runite Miner Killer] banked removed itemId={}", removedIds[slotCursor]);
                    removedIds[slotCursor] = -1;
                    slotCursor++;
                    failures = 0;
                    phase = Phase.DEPOSIT_REMOVED;
                    return description();
                }
                if (now - actionAt > INVENTORY_WAIT) {
                    phase = Phase.DEPOSIT_REMOVED;
                    return retry(now, "Equipment deposit not confirmed");
                }
                return description();
            case DEPOSIT_FOOD:
                if (!Rs2Bank.isOpen()) { phase = Phase.OPEN_BANK; return description(); }
                depositingFoodId = firstFoodId();
                if (depositingFoodId < 0) {
                    failures = 0;
                    if (freeSpaceForEquipment) {
                        freeSpaceForEquipment = false;
                        phase = Phase.REMOVE_EQUIPMENT;
                        return "Food banked; continuing helmet/cape removal";
                    }
                    phase = Phase.CLOSE_BANK;
                    return description();
                }
                inventoryBefore = Rs2Inventory.itemQuantity(depositingFoodId);
                if (!Rs2Bank.depositAll(depositingFoodId)) return retry(now, "Depositing Castle Wars food " + depositingFoodId);
                actionAt = now;
                phase = Phase.WAIT_FOOD_DEPOSIT;
                notBefore = now + 180L;
                log.info("[KSP Runite Miner Killer] banking food before Castle Wars itemId={} quantity={}",
                        depositingFoodId, inventoryBefore);
                return description();
            case WAIT_FOOD_DEPOSIT:
                if (Rs2Inventory.itemQuantity(depositingFoodId) == 0) {
                    log.info("[KSP Runite Miner Killer] confirmed banked food itemId={}", depositingFoodId);
                    failures = 0;
                    phase = Phase.DEPOSIT_FOOD;
                    return description();
                }
                if (now - actionAt > INVENTORY_WAIT) {
                    phase = Phase.DEPOSIT_FOOD;
                    return retry(now, "Food deposit not confirmed: " + depositingFoodId);
                }
                return description();
            case CLOSE_BANK:
                if (Rs2Bank.isOpen() && firstFoodId() >= 0) {
                    phase = Phase.DEPOSIT_FOOD;
                    return "More food detected; finishing Castle Wars deposit";
                }
                if (Rs2Bank.isOpen()) {
                    if (!Rs2Bank.closeBank()) return retry(now, "Closing GE bank");
                    notBefore = now + 220;
                    return description();
                }
                proceedToRecruiters(now, "GE banking complete; food and equipment cleared");
                return description();
            case WALK_RECRUITERS:
                // Gate entry on the CURRENT inventory/equipment state, not an old snapshot.
                // In normal operation nothing changes en route; if it does, return for prep.
                // A complete inventory + worn-equipment scan on every scheduler
                // pass was wasting client time during movement. Check at entry,
                // then at most every 2s; still check before issuing a recruiter click.
                if (now - lastPreparationCheckAt >= PREPARATION_RECHECK_MS &&
                        requiresGePreparation()) {
                    log.info("[KSP Runite Miner Killer] Castle Wars prep required again; returning to GE bank");
                    phase = Phase.WALK_GE;
                    slotCursor = 0;
                    return "Returning to GE to clear food, cape or helmet";
                }
                if (now - lastPreparationCheckAt >= PREPARATION_RECHECK_MS)
                    lastPreparationCheckAt = now;
                // First attempt the LIVE actor even when still on the edge of the
                // requested rectangle. Waiting for another scheduler pass solely
                // to transition to JOIN_TEAM caused a motionless-looking bot.
                scheduleLiveRecruiterScan(now);
                RecruiterObservation live = recentRecruiter();
                if (live != null) {
                    if (requiresGePreparation()) {
                        phase = Phase.WALK_GE;
                        slotCursor = 0;
                        return "Castle Wars prep changed; returning to bank";
                    }
                    log.info("[KSP Runite Miner Killer] recruiter visible from {}: {} id={} - queueing interaction",
                            here, live.name, live.id);
                    return joinRecruiter(live, now);
                }
                int distanceFromArea = distanceToRecruiterArea(here);
                if (missingRecruiterSince == 0L) missingRecruiterSince = now;
                if (now - lastRecruiterScanLog >= 5000L || lastRecruiterScanLog == 0L) {
                    log.info("[KSP Runite Miner Killer] recruiter live scene EMPTY at {} "
                                    + "areaDistance={} target={} elapsed={}ms; "
                                    + "looking for Sara=8644 / Zam=10732 or exact NPC names; scanPending={}",
                            here, distanceFromArea, RECRUITER_CENTRE, now-missingRecruiterSince, sceneScanPending);
                    lastRecruiterScanLog = now;
                }
                if (now - missingRecruiterSince >= RECRUITER_SCENE_TIMEOUT_MS)
                    return abort("Neither Castle Wars recruiter in live scene after 45 seconds at " + here);

                // Always approach the centre (3143,3472), not the north-east
                // corner (3147,3475). Being within 3 tiles of that corner is
                // NOT equivalent to having the recruiters loaded and clickable.
                if (distanceFromArea > 0 || here.distanceTo(RECRUITER_CENTRE) > 2) {
                    if (now - lastRecruiterWalkAt >= 2200L) {
                        lastRecruiterWalkAt = now;
                        log.info("[KSP Runite Miner Killer] walking into centre of recruiter area from {} "
                                + "to {} (distance to area={})", here, RECRUITER_CENTRE, distanceFromArea);
                        boolean arrived = walk(RECRUITER_CENTRE, 1);
                        log.info("[KSP Runite Miner Killer] recruiter-area walk returned arrived={} "
                                        + "(next step will rescan live NPCs)", arrived);
                    }
                    notBefore = System.currentTimeMillis() + 500L;
                    return "Approaching Castle Wars recruiters at 3143,3472";
                }
                // Never remain indefinitely at a single tile if the scene lacks
                // a usable recruiter. Try two nearby reachable tiles (both within
                // the user-supplied rectangle) without burning NPC click retries.
                if (now - missingRecruiterSince >= 8000L &&
                        now - lastRecruiterRepositionAt >= 8000L) {
                    WorldPoint checkpoint = (recruiterRepositionCount++ % 2 == 0)
                            ? new WorldPoint(3145, 3473, 0)
                            : new WorldPoint(3141, 3471, 0);
                    lastRecruiterRepositionAt = now;
                    log.warn("[KSP Runite Miner Killer] recruiter absent after {}ms at {}: walking to nearby scout tile {}",
                            now-missingRecruiterSince, here, checkpoint);
                    walk(checkpoint, 1);
                    notBefore = System.currentTimeMillis() + 600L;
                    return "Searching recruiter area - checking adjacent scene tiles";
                }
                notBefore = now + 750L;
                return "Scanning LIVE NPC scene for Castle Wars recruiters";
            case JOIN_TEAM:
                // Retained for fallback/old phase transitions; attempt immediately
                // instead of waiting one tick for a bare enum transition.
                if (recruiterCursor >= RECRUITER_IDS.length * 2)
                    return abort("Neither Castle Wars recruiter transferred the player");
                scheduleLiveRecruiterScan(now);
                RecruiterObservation target = recentRecruiter();
                if (target == null) {
                    phase = Phase.WALK_RECRUITERS;
                    notBefore = now + 500L;
                    log.info("[KSP Runite Miner Killer] recruiter not visible before click, rescanning scene");
                    return "Reacquiring live Castle Wars recruiter";
                }
                return joinRecruiter(target, now);
            case WAIT_RECRUITER_CLICK:
                if (!clickFinished) {
                    if (now - clickRequestedAt > CLICK_CALLBACK_TIMEOUT_MS) {
                        log.warn("[KSP Runite Miner Killer] client-thread recruiter click timed out after {}ms "
                                + "(npc={} id={})", now-clickRequestedAt, clickRecruiterName, clickRecruiterId);
                        clickSequence++; // invalidate any old callback still queued
                        phase = Phase.WALK_RECRUITERS;
                        sceneRecruiter = null;
                        notBefore = now + 900L;
                        return retry(now, "Castle Wars recruiter click timed out");
                    }
                    return "Waiting for client-thread recruiter click";
                }
                if (!clickIssued) {
                    recruiterCursor++;
                    log.warn("[KSP Runite Miner Killer] recruiter click not issued: {} id={} cause={}",
                            clickRecruiterName, clickRecruiterId,
                            clickError.isEmpty() ? "interaction returned false" : clickError);
                    phase = Phase.WALK_RECRUITERS;
                    notBefore = now + 650L;
                    return retry(now, "Castle Wars recruiter click not accepted");
                }
                log.info("[KSP Runite Miner Killer] Join Castle Wars CLICK ISSUED to {} id={} "
                        + "(waiting for location change)", clickRecruiterName, clickRecruiterId);
                missingRecruiterSince = 0L;
                failures = 0;
                phase = Phase.WAIT_RECRUITER;
                actionAt = now;
                notBefore = now + 900L;
                return "Joining Castle Wars: clicked " + clickRecruiterName;
            case WAIT_RECRUITER:
                // A recruiter click is not proof of successful transfer. Dialogue
                // may need confirmation; otherwise rely on coordinates changing
                // to castleWaiting() at the top of advance().
                if (Rs2Dialogue.hasSelectAnOption()) {
                    if (Rs2Dialogue.clickOption(false, "Yes", "I do")) {
                        notBefore = now + 900L;
                        log.info("[KSP Runite Miner Killer] confirmed recruiter dialogue");
                        return "Confirming Castle Wars team entry";
                    }
                    return "Recruiter dialogue requires an unrecognised answer";
                }
                if (Rs2Dialogue.hasContinue()) {
                    Rs2Dialogue.clickContinue();
                    notBefore = now + 650L;
                    return "Continuing recruiter dialogue";
                }
                if (now - actionAt >= TRANSFER_WAIT) {
                    recruiterCursor++;
                    phase = Phase.JOIN_TEAM;
                    notBefore = now + 600L;
                    log.warn("[KSP Runite Miner Killer] recruiter click unconfirmed after {}ms; "
                            + "attempting alternative team", now-actionAt);
                    return "Trying alternate Castle Wars recruiter";
                }
                return description();
            case EXIT_WAITING_ROOM:
                // Only the waiting room contains the exit portal; never click an entry
                // portal from the Castle Wars main lobby.
                if (Rs2GameObject.interact("Portal", "Exit")
                        || Rs2GameObject.interact("Portal", "Leave")) {
                    actionAt = now;
                    phase = Phase.WAIT_EXIT;
                    notBefore = now + 1400;
                    return description();
                }
                return retry(now, "Cannot find Castle Wars waiting-room exit portal");
            case WAIT_EXIT:
                if (now - actionAt >= TRANSFER_WAIT) {
                    phase = Phase.EXIT_WAITING_ROOM;
                    return retry(now, "Castle Wars waiting-room portal did not transfer");
                }
                return description();
            case FEROX_DOORS:
                if (Rs2Dialogue.hasSelectAnOption()) {
                    if (Rs2Dialogue.clickOption(false, "Yes", "Ferox", "Travel")) {
                        notBefore = now + 1100;
                        return "Confirming Ferox transfer";
                    }
                    return "Waiting for Ferox door confirmation";
                }
                if (Rs2Dialogue.hasContinue()) {
                    Rs2Dialogue.clickContinue();
                    notBefore = now + 850;
                    return "Continuing Ferox door dialogue";
                }
                // These are the two user-confirmed lobby doors, not the Castle
                // Wars arena doors. Explicit IDs avoid fragile generic-name lookup.
                // The F2P lobby door's available action transfers to Ferox.
                if (here.distanceTo(FEROX_DOOR_APPROACH) > 8) {
                    log.info("[KSP Runite Miner Killer] approaching Castle Wars Ferox doors "
                            + "near {} from {}", FEROX_DOOR_APPROACH, here);
                    walk(FEROX_DOOR_APPROACH, 3);
                    notBefore = System.currentTimeMillis() + 400L;
                    return "Approaching Castle Wars exit doors";
                }
                int selectedDoor = -1;
                for (int doorId : FEROX_DOOR_IDS) {
                    if (Rs2GameObject.interact(doorId)) {
                        selectedDoor = doorId;
                        break;
                    }
                }
                if (selectedDoor > 0 || Rs2GameObject.interact("Large door", "")) {
                    log.info("[KSP Runite Miner Killer] Castle Wars Ferox door clicked; "
                            + "id={} location={} (waiting for transfer)",
                            selectedDoor, here);
                    phase = Phase.WAIT_FEROX;
                    failures = 0;
                    actionAt = now;
                    notBefore = now + 450L;
                    return description();
                }
                return retry(now, "Ferox lobby doors 30387/30388 unavailable at " + here);
            case WAIT_FEROX:
                if (Rs2Dialogue.hasSelectAnOption()) {
                    if (Rs2Dialogue.clickOption(false, "Yes", "Ferox", "Travel")) {
                        notBefore = now + 1400;
                        return "Confirming travel to Ferox";
                    }
                }
                if (Rs2Dialogue.hasContinue()) {
                    Rs2Dialogue.clickContinue();
                    notBefore = now + 750;
                    return "Continuing travel dialogue";
                }
                if (now - actionAt > TRANSFER_WAIT) {
                    phase = Phase.FEROX_DOORS;
                    return retry(now, "Castle Wars lobby doors did not transfer");
                }
                return description();
            default:
                return "FAILED: " + error;
        }
    }

    private static String description() {
        switch (phase) {
            case WALK_GE: return "Walking to Grand Exchange";
            case REMOVE_EQUIPMENT: case WAIT_REMOVE: return "Removing equipped helmet/cape";
            case OPEN_BANK: case DEPOSIT_REMOVED: case WAIT_DEPOSIT: case CLOSE_BANK:
            case DEPOSIT_FOOD: case WAIT_FOOD_DEPOSIT:
                return "Banking helmet/cape and food at Grand Exchange";
            case WALK_RECRUITERS: return "Walking to Castle Wars recruiters";
            case JOIN_TEAM: case WAIT_RECRUITER_CLICK: case WAIT_RECRUITER:
                return "Joining Castle Wars via GE recruiter";
            case EXIT_WAITING_ROOM: case WAIT_EXIT: return "Leaving Castle Wars waiting room";
            case FEROX_DOORS: case WAIT_FEROX: return "Using Castle Wars lobby doors to Ferox";
            default: return "Travelling to Ferox";
        }
    }
    private static String retry(long now, String reason) {
        failures++;
        notBefore = now + ACTION_GAP;
        log.warn("[KSP Runite Miner Killer] route retry {}/{}: {} phase={}", failures, MAX_ACTION_FAILURES, reason, phase);
        if (failures >= MAX_ACTION_FAILURES) return abort(reason + " (retries exhausted)");
        return reason;
    }
    private static String abort(String message) {
        error = message;
        phase = Phase.FAILED;
        log.warn("[KSP Runite Miner Killer] Ferox route stopped: {}", message);
        return "FAILED: " + message;
    }
    private static boolean walk(WorldPoint goal, int distance) {
        boolean prior = Rs2Walker.disableTeleports;
        try {
            Rs2Walker.disableTeleports = true;
            return Rs2Walker.walkTo(goal, distance);
        } finally {
            Rs2Walker.disableTeleports = prior;
        }
    }
}
