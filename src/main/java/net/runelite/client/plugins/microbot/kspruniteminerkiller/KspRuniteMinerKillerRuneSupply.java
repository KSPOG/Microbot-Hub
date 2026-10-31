package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.magic.Spell;

/**
 * Rune-stack-aware supply reconciliation for the KSP Runite Miner Killer.
 * Quantities are units, never the number of occupied inventory/bank slots.
 * The engine's missing-runes calculation remains authoritative for staff,
 * rune-pouch and combination-rune substitutions.
 */
public final class KspRuniteMinerKillerRuneSupply {
    private static final Logger log = LoggerFactory.getLogger(KspRuniteMinerKillerRuneSupply.class);
    private static volatile int fallbackStaffId = -1;
    private static volatile String fallbackStaffName = "";
    private static volatile int pendingStaffId = -1;
    private static volatile long pendingStaffAt;
    private static final long STAFF_WITHDRAW_TIMEOUT_MS = 6_000L;

    // Called by FeroxPrep for every slot. Only WEAPON can be temporarily
    // substituted; persisted GearStore data is untouched.
    public static String effectiveName(KspRuniteMinerKillerGearStore store, EquipmentInventorySlot slot) {
        return slot == EquipmentInventorySlot.WEAPON && fallbackStaffId > 0
                ? fallbackStaffName : store.get(slot);
    }
    public static int effectiveId(KspRuniteMinerKillerGearStore store, EquipmentInventorySlot slot) {
        return slot == EquipmentInventorySlot.WEAPON && fallbackStaffId > 0
                ? fallbackStaffId : store.itemId(slot);
    }

    public static int fallbackStaffId() { return fallbackStaffId; }
    public static String fallbackStaffName() { return fallbackStaffName; }
    public static void resetStaffFallback() {
        fallbackStaffId = pendingStaffId = -1;
        fallbackStaffName = "";
        pendingStaffAt = 0L;
    }

    private static final ThreadLocal<Spell> CURRENT_SPELL = new ThreadLocal<>();
    private static final ThreadLocal<Integer> CHECK_CASTS = new ThreadLocal<>();
    private static volatile long waitingForBankSince;

    /** Do not conclude supplies are absent while a fresh bank snapshot is pending. */
    public static boolean shouldWaitForBankSnapshot() {
        if (pendingStaffId > 0) {
            if (Rs2Inventory.itemQuantity(pendingStaffId) > 0) {
                pendingStaffId = -1;
                return false;
            }
            if (System.currentTimeMillis() - pendingStaffAt < STAFF_WITHDRAW_TIMEOUT_MS) return true;
            log.warn("[KSP Runite Miner Killer] Elemental staff withdrawal timed out, id={}", pendingStaffId);
            pendingStaffId = -1;
        }
        if (!Rs2Bank.isOpen() || Rs2Bank.getBankLiveEpoch() > 0) {
            waitingForBankSince = 0L;
            return false;
        }
        long now = System.currentTimeMillis();
        if (waitingForBankSince == 0L) waitingForBankSince = now;
        return now - waitingForBankSince < 5_000L;
    }


    private KspRuniteMinerKillerRuneSupply() { }

    public static Map<Runes, Integer> getMissingRunes(Spell spell, int casts) {
        if (spell == null) return Collections.emptyMap();
        CURRENT_SPELL.set(spell);
        CHECK_CASTS.set(Math.max(1, casts));
        Map<Runes, Integer> missing = new LinkedHashMap<>(Rs2Magic.getMissingRunes(spell, casts));
        // Defensive reconciliation against the *unit quantity* in each
        // carried rune stack. RuneLite's itemQuantity counts units, not slots.
        for (Map.Entry<Runes, Integer> entry : spell.getRequiredRunes(casts).entrySet()) {
            if (Rs2Inventory.itemQuantity(entry.getKey().getItemId()) >= entry.getValue()) {
                missing.remove(entry.getKey());
            }
        }
        return missing;
    }

    public static boolean hasRequiredRunes(Spell spell, int casts) {
        Map<Runes, Integer> missing = getMissingRunes(spell, casts);
        if (missing.isEmpty()) return true;
        // At the bank only: a selected staff is projected into the Ferox
        // WEAPON slot before leaving Ferox. Never treat it as equipped in
        // the Wilderness while it is merely carried or banked.
        return Rs2Bank.isOpen() && arrangeStaffForMissingRunes(spell, casts, missing);
    }

    private static boolean arrangeStaffForMissingRunes(Spell spell, int casts, Map<Runes,Integer> missing) {
        if (spell == null || missing.isEmpty()) return false;
        if (pendingStaffId > 0 && Rs2Inventory.itemQuantity(pendingStaffId) == 0) return false;
        if (pendingStaffId > 0) pendingStaffId = -1;
        for (Rs2Staff staff : Rs2Staff.values()) {
            // This plugin hops only to F2P worlds, so exclude members-only
            // mystic, combination and specialty staves from consideration.
            switch (staff) {
                case STAFF_OF_AIR: case STAFF_OF_WATER: case STAFF_OF_EARTH: case STAFF_OF_FIRE:
                case AIR_BATTLESTAFF: case WATER_BATTLESTAFF: case EARTH_BATTLESTAFF: case FIRE_BATTLESTAFF:
                    break;
                default: continue;
            }
            int id = staff.getItemID();
            // Replacing another elemental staff can un-cover its rune.
            // Evaluate the FULL spell requirement with the proposed staff,
            // not only the rune currently missing on the equipped weapon.
            boolean allCovered = true;
            for (Map.Entry<Runes,Integer> required : spell.getRequiredRunes(casts).entrySet()) {
                if (!staff.provides(required.getKey())
                        && Rs2Inventory.itemQuantity(required.getKey().getItemId()) < required.getValue()) {
                    allCovered = false;
                    break;
                }
            }
            if (!allCovered) continue;
            boolean carried = Rs2Inventory.itemQuantity(id) > 0;
            boolean banked = bankQuantity(id) > 0;
            if (!carried && !banked) continue;
            String name = "";
            if (carried) {
                Rs2ItemModel item = Rs2Inventory.get(new int[]{id});
                if (item != null) name = item.getName();
            }
            if (name == null || name.isEmpty()) {
                for (Rs2ItemModel item : Rs2Bank.bankItems()) {
                    if (item != null && item.getId() == id) { name = item.getName(); break; }
                }
            }
            if (name == null || name.isEmpty()) continue;
            fallbackStaffId = id;
            fallbackStaffName = name;
            if (carried) {
                log.info("[KSP Runite Miner Killer] Using carried elemental staff {} (id={}) instead of unavailable rune units", name, id);
                return true;
            }
            if (Rs2Inventory.emptySlotCount() <= 0) return false;
            if (Rs2Bank.withdrawX(id,1)) {
                pendingStaffId = id;
                pendingStaffAt = System.currentTimeMillis();
                log.info("[KSP Runite Miner Killer] Withdrawing elemental staff {} (id={}) to cover missing runes", name, id);
            } else {
                log.warn("[KSP Runite Miner Killer] Unable to withdraw elemental staff {} (id={})", name, id);
            }
            return false; // Await live inventory update before leaving bank.
        }
        return false;
    }

    /** Count the number of units across all bank stacks with the given ID. */
    public static int bankQuantity(int itemId) {
        if (itemId <= 0) return 0;
        // Only one traversal for the relevant rune ID. A fallback count is
        // needed when the bank mirror has not exposed a live item list yet.
        long total = 0L;
        for (Rs2ItemModel item : Rs2Bank.bankItems()) {
            if (item != null && item.getId() == itemId && item.getQuantity() > 0)
                total += item.getQuantity();
        }
        if (total > 0) return (int) Math.min(Integer.MAX_VALUE, total);
        return Rs2Bank.count(itemId);
    }

    /** Enrich the existing stop reason with the exact number of rune units available. */
    public static String explain(String previousReason) {
        Spell spell = CURRENT_SPELL.get();
        if (spell == null) return previousReason;
        int casts = extractCastCount(previousReason, CHECK_CASTS.get() == null ? 1 : CHECK_CASTS.get());
        Map<Runes, Integer> missing = getMissingRunes(spell, casts);
        StringBuilder text = new StringBuilder(previousReason).append(" Rune stack quantities:");
        if (Rs2Bank.getBankLiveEpoch() == 0) {
            text.append(" WARNING: live bank snapshot not confirmed;");
        }
        for (Map.Entry<Runes, Integer> entry : spell.getRequiredRunes(casts).entrySet()) {
            Runes rune = entry.getKey();
            int id = rune.getItemId();
            text.append(' ').append(rune.name().toLowerCase(Locale.ROOT))
                    .append("[need=").append(entry.getValue())
                    .append(",inventory=").append(Rs2Inventory.itemQuantity(id))
                    .append(",bank=").append(bankQuantity(id))
                    .append(",uncovered=").append(missing.getOrDefault(rune, 0))
                    .append(']');
        }
        if (fallbackStaffId > 0) {
            text.append(" Fallback staff=").append(fallbackStaffName)
                    .append(" (id=").append(fallbackStaffId).append(")");
        }
        if (missing.isEmpty()) {
            text.append(" All rune quantities are covered; check spellbook and Magic level.");
        } else {
            text.append(" Uncovered quantities may be supplied by elemental staves, combination runes or a rune pouch.");
        }
        return text.toString();
    }

    static int extractCastCount(String message, int fallback) {
        int start = message.indexOf("for ");
        if (start < 0) return fallback;
        start += 4;
        int end = start;
        while (end < message.length() && Character.isDigit(message.charAt(end))) end++;
        if (end == start) return fallback;
        try { return Integer.parseInt(message.substring(start, end)); }
        catch (NumberFormatException e) { return fallback; }
    }
}
