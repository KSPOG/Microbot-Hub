package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import java.util.Arrays;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ferox staging is two distinct, ordered transactions:
 * (1) gather EVERY missing saved item in inventory while bank is open;
 * (2) close and confirm the bank is closed, then equip and verify EVERY slot.
 * Never assume an inventory menu invocation succeeded without seeing the item worn.
 */
final class KspRuniteMinerKillerFeroxPrep {
    private static final Logger log = LoggerFactory.getLogger(KspRuniteMinerKillerFeroxPrep.class);
    private static final WorldPoint BANK = new WorldPoint(3130, 3631, 0);
    private static final int POOL_ID = 39651;
    private static final long ACTION_GAP = 350L;
    private static final long ITEM_CHANGE_TIMEOUT = 5000L;
    private static final long BANK_SNAPSHOT_TIMEOUT = 5000L;
    private static final int MAX_FAILURES = 5;

    private enum State { GATHER, WAIT_BANK, WAIT_WITHDRAW, CLOSE_BANK, EQUIP, WAIT_EQUIP,
                         SKULL_OFF, WAIT_SKULL_OFF, AUTOCAST, WAIT_AUTOCAST,
                         POOL, WAIT_POOL, DONE, FAILED }

    private static KspRuniteMinerKillerGearStore store;
    private static Rs2CombatSpells desiredSpell;
    private static State state = State.GATHER;
    private static int gatherCursor, equipCursor, failures;
    private static int beforeQuantity, requiredId, requestedUnits;
    private static int bankEpochAtOpen;
    private static long lastAction, notBefore;
    private static EquipmentInventorySlot currentSlot;
    private static String problem = "", lastActionName = "";
    private static boolean missingGearNoticeShown;
    private static boolean waitingForBankFromClosed;

    private KspRuniteMinerKillerFeroxPrep() { }

    static synchronized void reset(KspRuniteMinerKillerGearStore gear) {
        store = gear;
        KspRuniteMinerKillerRuneSupply.resetStaffFallback();
        desiredSpell = null;
        state = State.GATHER;
        gatherCursor = equipCursor = failures = 0;
        beforeQuantity = 0;
        requestedUnits = 0;
        requiredId = -1;
        currentSlot = null;
        bankEpochAtOpen = 0;
        lastAction = notBefore = 0L;
        lastActionName = problem = "";
        waitingForBankFromClosed = false;
        missingGearNoticeShown = false;
    }

    /** Restore the saved loadout from scratch after PvP death; preserve GearStore. */
    static synchronized void resetAfterDeath() {
        reset(store);
    }

    static synchronized boolean wildernessReady(KspRuniteMinerKillerConfig config) {
        if (store == null || config == null) return false;
        for (EquipmentInventorySlot slot : KspRuniteMinerKillerGearStore.SLOTS) {
            Rs2ItemModel worn = Rs2Equipment.get(slot);
            String savedName = effectiveName(slot);
            if (savedName.isEmpty()) {
                if (worn != null) return false;
            } else if (!matchesWorn(slot, worn)) {
                return false;
            }
        }
        if (!KspRuniteMinerKillerCombatSetup.ready(config.spell().spell())) return false;
        if (Rs2Inventory.itemQuantity(config.foodName().trim(), true)
                < Math.max(2, config.stockFood())) return false;
        return config.spell().spell().hasRequirements()
                && Rs2Magic.hasRequiredRunes(config.spell().spell(), config.minimumCasts());
    }

    // Called by KspRuniteMinerKillerScript.returnToMine; preserve the old
    // 2-argument entry point for existing auxiliary integrations/tests.
    static synchronized String advance(WorldPoint here, long now, Rs2CombatSpells spell) {
        if (spell != null) desiredSpell = spell;
        return advance(here, now);
    }

    static synchronized String advance(WorldPoint here, long now) {
        if (here == null || store == null) return "Waiting for location";
        if (!KspRuniteMinerKillerFeroxRoute.atFerox(here)) {
            if (state == State.DONE) {
                state = State.GATHER;
                gatherCursor = equipCursor = failures = 0;
            }
            return null;
        }
        if (state == State.DONE) {
            if (desiredSpell != null && !KspRuniteMinerKillerCombatSetup.ready(desiredSpell)) {
                state = State.SKULL_OFF;
                return "Ferox combat settings changed; preparing again";
            }
            return null;
        }
        if (state == State.FAILED) return "FAILED: " + problem;
        if (now < notBefore) return description();

        if (state == State.WAIT_BANK) {
            if (Rs2Bank.isOpen() && (!waitingForBankFromClosed
                    ? Rs2Bank.getBankLiveEpoch() > 0
                    : Rs2Bank.getBankLiveEpoch() > bankEpochAtOpen)) {
                state = State.GATHER;
                failures = 0;
                log.info("[KSP Runite Miner Killer] Ferox bank live snapshot ready; collecting saved loadout");
                return "Checking saved gear in Ferox bank";
            }
            if (now - lastAction > BANK_SNAPSHOT_TIMEOUT) {
                state = State.GATHER;
                return retry("Ferox bank did not provide an updated item snapshot");
            }
            return "Waiting for Ferox bank inventory";
        }

        if (state == State.WAIT_WITHDRAW) {
            int available = inventoryCount(requiredId, lastActionName);
            if (available >= beforeQuantity + requestedUnits
                    || matchesWorn(currentSlot, Rs2Equipment.get(currentSlot))) {
                log.info("[KSP Runite Miner Killer] Ferox withdrawal confirmed: slot={} item={} id={} inventoryUnits={}",
                        currentSlot, lastActionName, requiredId, available);
                gatherCursor++;
                state = State.GATHER;
                failures = 0;
                return "Collected " + lastActionName;
            }
            if (now - lastAction >= ITEM_CHANGE_TIMEOUT) {
                state = State.GATHER;
                return retry("Unconfirmed Ferox withdrawal: " + lastActionName);
            }
            return "Confirming withdrawal: " + lastActionName;
        }

        if (state == State.GATHER) {
            // Only advance to EQUIP when the ENTIRE saved loadout is either worn
            // already or physically present in inventory. Do not equip piecemeal.
            while (gatherCursor < KspRuniteMinerKillerGearStore.SLOTS.length) {
                EquipmentInventorySlot slot = KspRuniteMinerKillerGearStore.SLOTS[gatherCursor];
                String name = effectiveName(slot);
                int id = effectiveId(slot);
                int needed = neededInventoryQuantity(slot);
                if (name.isEmpty() || needed == 0
                        || inventoryCount(id, name) >= needed) {
                    gatherCursor++;
                    failures = 0;
                    continue;
                }
                currentSlot = slot;
                requiredId = id;
                lastActionName = name;
                if (here.distanceTo(BANK) > 7) {
                    boolean old = Rs2Walker.disableTeleports;
                    try { Rs2Walker.disableTeleports = true; Rs2Walker.walkTo(BANK, 4); }
                    finally { Rs2Walker.disableTeleports = old; }
                    notBefore = now + ACTION_GAP;
                    return "Walking to Ferox bank for saved gear";
                }
                if (!Rs2Bank.isOpen()) {
                    bankEpochAtOpen = Rs2Bank.getBankLiveEpoch();
                    waitingForBankFromClosed = true;
                    if (!Rs2Bank.openBank()) return retry("Unable to open Ferox bank");
                    state = State.WAIT_BANK;
                    lastAction = now;
                    notBefore = now + ACTION_GAP;
                    return "Opening Ferox bank for full loadout";
                }
                if (Rs2Bank.getBankLiveEpoch() == 0) {
                    bankEpochAtOpen = 0;
                    waitingForBankFromClosed = false;
                    state = State.WAIT_BANK;
                    lastAction = now;
                    return "Waiting for Ferox bank inventory";
                }
                if (Rs2Inventory.emptySlotCount() <= 0 && inventoryCount(id, name) == 0) {
                    return abort("Inventory full; cannot collect saved gear: " + name);
                }
                // Prefer the saved numeric ID (unambiguous when bank has similar
                // item names). Legacy name-only configs remain supported.
                beforeQuantity = inventoryCount(id, name);
                requestedUnits = needed - beforeQuantity;
                boolean inBank = id > 0 ? Rs2Bank.hasBankItem(id, requestedUnits)
                        : Rs2Bank.hasBankItem(name, requestedUnits, true);
                if (!inBank) return abort("Saved gear missing from Ferox bank: " + name
                        + " (need " + requestedUnits + " more)");
                boolean requested = id > 0 ? Rs2Bank.withdrawX(id, requestedUnits)
                        : Rs2Bank.withdrawX(name, requestedUnits, true);
                if (!requested) return retry("Ferox bank did not accept withdrawal: " + name);
                state = State.WAIT_WITHDRAW;
                lastAction = now;
                notBefore = now + ACTION_GAP;
                log.info("[KSP Runite Miner Killer] Ferox loadout withdrawal requested: slot={} item={} id={} ({}/{})",
                        slot, name, id, gatherCursor + 1, KspRuniteMinerKillerGearStore.SLOTS.length);
                return "Withdrawing saved gear: " + name;
            }
            state = State.CLOSE_BANK;
            failures = 0;
            log.info("[KSP Runite Miner Killer] all saved gear is available; closing bank before any equipping");
            return "All saved gear collected";
        }

        if (state == State.CLOSE_BANK) {
            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
                notBefore = now + ACTION_GAP;
                return "Closing bank before equipping loadout";
            }
            // Confirm the bank interface has actually closed; inventory clicks
            // while the bank is open address BANK_INVENTORY widgets, not backpack.
            state = State.EQUIP;
            equipCursor = 0;
            failures = 0;
            return "Equipping saved combat setup";
        }

        if (state == State.WAIT_EQUIP) {
            if (matchesWorn(currentSlot, Rs2Equipment.get(currentSlot))) {
                log.info("[KSP Runite Miner Killer] Ferox equip confirmed: slot={} item={}",
                        currentSlot, lastActionName);
                equipCursor++;
                failures = 0;
                state = State.EQUIP;
                return "Equipped " + lastActionName;
            }
            if (now - lastAction >= ITEM_CHANGE_TIMEOUT) {
                state = State.EQUIP;
                return retry("Equip click did not register for " + lastActionName);
            }
            return "Confirming equipped item: " + lastActionName;
        }

        if (state == State.EQUIP) {
            if (Rs2Bank.isOpen()) {
                state = State.CLOSE_BANK;
                return "Waiting for Ferox bank to close";
            }
            while (equipCursor < KspRuniteMinerKillerGearStore.SLOTS.length) {
                EquipmentInventorySlot slot = KspRuniteMinerKillerGearStore.SLOTS[equipCursor];
                String name = effectiveName(slot);
                int id = effectiveId(slot);
                if (name.isEmpty() || matchesWorn(slot, Rs2Equipment.get(slot))) {
                    equipCursor++;
                    failures = 0;
                    continue;
                }
                Rs2ItemModel item = inventoryItem(id, name);
                if (item == null) {
                    // A displaced item or other asynchronous change means the
                    // whole setup must be collected again before equipping more.
                    log.warn("[KSP Runite Miner Killer] saved gear absent during equip; recollecting slot={} item={}", slot, name);
                    state = State.GATHER;
                    gatherCursor = 0;
                    failures = 0;
                    return "Rechecking saved gear: " + name;
                }
                String action = equipAction(item, slot, failures);
                if (action == null) return abort("No Wear/Wield/Equip action for saved item: " + name);
                currentSlot = slot;
                lastActionName = name;
                requiredId = item.getId();
                // Action MUST be explicit; blank-action interact(id) in Microbot
                // uses identifier=-1 and may visually click without equipping.
                // AIO Fighter uses exact-name, explicit-action inventory clicks.
                // The saved ID is validated by inventoryItem() before this call;
                // never use a blank/default widget action or assume that a
                // successfully invoked menu command actually equipped the item.
                if (!Rs2Inventory.interact(item.getName(), action, true)) {
                    return retry("Could not issue " + action + " for " + name);
                }
                log.info("[KSP Runite Miner Killer] Ferox equip click issued: slot={} item={} id={} action={}",
                        slot, name, item.getId(), action);
                state = State.WAIT_EQUIP;
                lastAction = now;
                notBefore = now + ACTION_GAP;
                return "Equipping " + name;
            }
            state = State.SKULL_OFF;
            failures = 0;
            log.info("[KSP Runite Miner Killer] complete loadout verified; configuring PK skull prevention and autocast at Ferox");
            return "Configuring PvP safety setting and autocast";
        }

        if (state == State.SKULL_OFF) {
            if (KspRuniteMinerKillerCombatSetup.skullPreventionDisabled()) {
                state = State.AUTOCAST;
                failures = 0;
                return "Skull Prevention confirmed off";
            }
            if (!KspRuniteMinerKillerCombatSetup.requestDisableSkullPrevention()) {
                return retry("Could not locate/toggle PK Skull Prevention in game settings");
            }
            state = State.WAIT_SKULL_OFF;
            lastAction = now;
            notBefore = now + ACTION_GAP;
            return "Disabling PK Skull Prevention";
        }
        if (state == State.WAIT_SKULL_OFF) {
            if (KspRuniteMinerKillerCombatSetup.skullPreventionDisabled()) {
                KspRuniteMinerKillerCombatSetup.closeSettings();
                state = State.AUTOCAST;
                failures = 0;
                log.warn("[KSP Runite Miner Killer] PK Skull Prevention OFF for Wilderness PvP; attacking players may skull the account");
                return "PK Skull Prevention OFF (confirmed)";
            }
            if (now - lastAction >= ITEM_CHANGE_TIMEOUT) {
                KspRuniteMinerKillerCombatSetup.closeSettings();
                state = State.SKULL_OFF;
                return retry("PK Skull Prevention did not change to OFF");
            }
            return "Verifying PK Skull Prevention disabled";
        }
        if (state == State.AUTOCAST) {
            if (desiredSpell == null) return abort("No configured autocast spell supplied to Ferox preparation");
            if (!KspRuniteMinerKillerCombatSetup.skullPreventionDisabled()) {
                state = State.SKULL_OFF;
                return "PK Skull Prevention reset; correcting at Ferox";
            }
            if (KspRuniteMinerKillerCombatSetup.autocastReady(desiredSpell)) {
                state = State.POOL;
                failures = 0;
                return "Autocast confirmed: " + desiredSpell.name();
            }
            if (!KspRuniteMinerKillerCombatSetup.selectAutocast(desiredSpell)) {
                return retry("Unable to select " + desiredSpell + " autocast (check saved weapon/staff)");
            }
            state = State.WAIT_AUTOCAST;
            lastAction = now;
            notBefore = now + ACTION_GAP;
            return "Selecting autocast: " + desiredSpell.name();
        }
        if (state == State.WAIT_AUTOCAST) {
            if (KspRuniteMinerKillerCombatSetup.autocastReady(desiredSpell)) {
                state = State.POOL;
                failures = 0;
                log.info("[KSP Runite Miner Killer] non-defensive autocast verified: {}", desiredSpell);
                return "Autocast ready: " + desiredSpell.name();
            }
            if (now - lastAction >= ITEM_CHANGE_TIMEOUT) {
                state = State.AUTOCAST;
                return retry("Autocast not confirmed: " + desiredSpell);
            }
            return "Verifying selected autocast";
        }
        if (state == State.POOL) {
            if (fullVitals()) {
                state = State.DONE;
                return null;
            }
            if (Rs2GameObject.interact(POOL_ID, "Drink")) {
                state = State.WAIT_POOL;
                lastAction = now;
                notBefore = now + ACTION_GAP;
                return "Drinking Pool of Refreshment";
            }
            return retry("Pool 39651 not available in loaded Ferox scene; approach chapel");
        }
        if (state == State.WAIT_POOL) {
            if (fullVitals()) {
                state = State.DONE;
                failures = 0;
                log.info("[KSP Runite Miner Killer] Ferox loadout and pool refresh confirmed");
                return null;
            }
            if (now - lastAction >= 7000L) {
                state = State.POOL;
                return retry("Pool interaction not confirmed");
            }
            return "Confirming HP and run restoration";
        }
        return description();
    }

    /** Temporary substitute staff is only used if banked rune stacks cannot supply
     * the selected combat spell. The saved configuration remains unchanged. */
    private static String effectiveName(EquipmentInventorySlot slot) {
        if (slot == EquipmentInventorySlot.WEAPON && KspRuniteMinerKillerRuneSupply.fallbackStaffId() > 0)
            return KspRuniteMinerKillerRuneSupply.fallbackStaffName();
        return store.get(slot);
    }
    private static int effectiveId(EquipmentInventorySlot slot) {
        if (slot == EquipmentInventorySlot.WEAPON && KspRuniteMinerKillerRuneSupply.fallbackStaffId() > 0)
            return KspRuniteMinerKillerRuneSupply.fallbackStaffId();
        return store.itemId(slot);
    }

    private static Rs2ItemModel inventoryItem(int savedId, String name) {
        // Microbot 2.6.43 defines get(int... ids), whose JVM descriptor
        // accepts int[] (NOT an int). Pass the array explicitly to prevent
        // stub-only compilation against the nonexistent get(int) overload.
        if (savedId > 0) {
            return Rs2Inventory.get(new int[]{savedId});
        }
        return Rs2Inventory.get(name, true);
    }

    private static int inventoryCount(int savedId, String name) {
        return savedId > 0 ? Rs2Inventory.itemQuantity(savedId) : Rs2Inventory.itemQuantity(name, true);
    }

    private static boolean sameWornItem(EquipmentInventorySlot slot, Rs2ItemModel worn) {
        if (worn == null || effectiveName(slot).isEmpty()) return false;
        int id = effectiveId(slot);
        return id > 0 ? id == worn.getId() : effectiveName(slot).equalsIgnoreCase(worn.getName());
    }

    private static boolean matchesWorn(EquipmentInventorySlot slot, Rs2ItemModel worn) {
        if (!sameWornItem(slot, worn)) return false;
        return slot != EquipmentInventorySlot.AMMO || worn.getQuantity() >= store.itemQuantity(slot);
    }

    private static int neededInventoryQuantity(EquipmentInventorySlot slot) {
        String name = effectiveName(slot);
        if (name.isEmpty()) return 0;
        Rs2ItemModel worn = Rs2Equipment.get(slot);
        if (slot == EquipmentInventorySlot.AMMO) {
            int wornUnits = sameWornItem(slot, worn) ? Math.max(0,worn.getQuantity()) : 0;
            return Math.max(0,store.itemQuantity(slot)-wornUnits);
        }
        return sameWornItem(slot, worn) ? 0 : 1;
    }

    /** Use the item's actual currently available action, not a blank default menu option. */
    private static String equipAction(Rs2ItemModel item, EquipmentInventorySlot slot, int unsuccessfulClicks) {
        String[] actions = item.getInventoryActions();
        String[] preference = slot == EquipmentInventorySlot.WEAPON || slot == EquipmentInventorySlot.SHIELD
                || slot == EquipmentInventorySlot.AMMO
                ? new String[]{"Wield", "Wear", "Equip"}
                : new String[]{"Wear", "Wield", "Equip"};
        if (actions != null) {
            java.util.List<String> usable = new java.util.ArrayList<>();
            for (String preferred : preference) {
                for (String candidate : actions) {
                    if (candidate != null && candidate.equalsIgnoreCase(preferred)) {
                        usable.add(candidate);
                        break;
                    }
                }
            }
            if (!usable.isEmpty()) return usable.get(Math.min(unsuccessfulClicks, usable.size() - 1));
        }
        // An older cache may not expose item actions. Try the expected slot
        // action explicitly and confirm WORN before considering it successful.
        return preference[Math.min(unsuccessfulClicks, preference.length - 1)];
    }

    private static boolean fullVitals() {
        return Rs2Player.getHealthPercentage() >= 100.0 && Microbot.getClient().getEnergy() >= 10000;
    }

    private static String description() {
        switch (state) {
            case GATHER: case WAIT_BANK: case WAIT_WITHDRAW:
                return "Collecting full saved gear setup at Ferox";
            case CLOSE_BANK:
                return "Closing bank before equipping";
            case EQUIP: case WAIT_EQUIP:
                return "Equipping and verifying saved setup";
            case SKULL_OFF: case WAIT_SKULL_OFF:
                return "Disabling PK Skull Prevention in Ferox";
            case AUTOCAST: case WAIT_AUTOCAST:
                return "Selecting and verifying autocast at Ferox";
            case POOL: case WAIT_POOL:
                return "Restoring HP and run at Ferox pool";
            default: return "Preparing Ferox equipment";
        }
    }

    private static String retry(String detail) {
        failures++;
        notBefore = System.currentTimeMillis() + 550L;
        if (failures >= MAX_FAILURES) return abort(detail + " (" + failures + " retries)");
        log.warn("[KSP Runite Miner Killer] Ferox prep retry {}/{}: {}", failures, MAX_FAILURES, detail);
        return detail;
    }

    private static String abort(String detail) {
        problem = detail;
        state = State.FAILED;
        log.warn("[KSP Runite Miner Killer] Ferox preparation stopped: {}", detail);
        // User requested popups ONLY when a saved gear item is truly missing
        // from both inventory/equipment and a freshly-synchronized bank.
        if (!missingGearNoticeShown && detail.startsWith("Saved gear missing from Ferox bank:")) {
            missingGearNoticeShown = true;
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, detail,
                    "KSP Runite Miner Killer - Missing saved gear", JOptionPane.WARNING_MESSAGE));
        }
        return "FAILED: " + detail;
    }
}
