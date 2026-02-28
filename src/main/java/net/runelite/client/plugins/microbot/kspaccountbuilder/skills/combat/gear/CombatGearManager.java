package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.gear;

import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.gear.armour.Armour;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.gear.swords.Swords;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.needed.Gear;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CombatGearManager {

    private static final String[] TEAM_CAPE_NAME_CANDIDATES = buildTeamCapeNameCandidates();

    public boolean hasCombatSetupReady() {
        return hasFoodInInventory() && hasBestWeaponEquipped() && hasBestArmourEquipped() && isWearingTeamCape();
    }

    public void withdrawFood() {
        if (hasFoodInInventory()) {
            return;
        }

        for (int foodId : Gear.FOOD_REQUIREMENTS) {
            int needed = Math.max(0, Gear.MIN_TROUT_REQUIRED - Rs2Inventory.count(foodId));
            if (needed > 0) {
                Rs2Bank.withdrawX(foodId, needed);
            }
        }
    }

    public void withdrawBestAvailableGear() {
        int bestWeapon = bestWeaponForAttackLevel();
        if (!Rs2Equipment.isWearing(bestWeapon) && Rs2Bank.count(bestWeapon) > 0) {
            Rs2Bank.withdrawX(bestWeapon, 1);
        }

        for (int armourId : bestArmourForDefenceLevel()) {
            if (!Rs2Equipment.isWearing(armourId) && Rs2Bank.count(armourId) > 0) {
                Rs2Bank.withdrawX(armourId, 1);
            }
        }

        if (!Rs2Equipment.isWearing(Gear.AMULET_OF_POWER) && Rs2Bank.count(Gear.AMULET_OF_POWER) > 0) {
            Rs2Bank.withdrawX(Gear.AMULET_OF_POWER, 1);
        }

        if (!isWearingTeamCape() && hasTeamCapeInBank()) {
            Rs2Bank.withdrawX(getBestAvailableTeamCapeName(), 1);
        }
    }

    public void equipInventoryGear() {
        Rs2Inventory.wield(bestWeaponForAttackLevel());
        for (int armourId : bestArmourForDefenceLevel()) {
            Rs2Inventory.wield(armourId);
        }

        Rs2Inventory.wield(Gear.AMULET_OF_POWER);
        if (isWearingTeamCape()) {
            return;
        }

        String cape = getTeamCapeInInventoryName();
        if (cape != null) {
            Rs2Inventory.interact(cape, "Wear");
        }
    }

    public Integer[] getProtectedInventoryIds() {
        List<Integer> protectedIds = new ArrayList<>();
        protectedIds.add(Gear.AMULET_OF_POWER);

        for (int foodId : Gear.FOOD_REQUIREMENTS) {
            protectedIds.add(foodId);
        }
        for (int weaponId : getAllMeleeWeaponUpgrades()) {
            protectedIds.add(weaponId);
        }
        for (int armourId : getAllMeleeArmourUpgrades()) {
            protectedIds.add(armourId);
        }

        return protectedIds.toArray(new Integer[0]);
    }

    public List<Integer> getSellItemIds(List<Integer> defaults) {
        List<Integer> idsToSell = new ArrayList<>(defaults);

        int bestWeapon = bestWeaponForAttackLevel();
        for (int weaponId : getAllMeleeWeaponUpgrades()) {
            if (weaponId != bestWeapon && !idsToSell.contains(weaponId)) {
                idsToSell.add(weaponId);
            }
        }

        int[] bestArmourSet = bestArmourForDefenceLevel();
        for (int armourId : getAllMeleeArmourUpgrades()) {
            if (!containsId(bestArmourSet, armourId) && !idsToSell.contains(armourId)) {
                idsToSell.add(armourId);
            }
        }

        return idsToSell;
    }

    public List<String> getMissingUpgradeNames() {
        List<String> names = new ArrayList<>();

        int bestWeapon = bestWeaponForAttackLevel();
        if (!hasItemAvailable(bestWeapon)) {
            addNameIfPresent(names, getItemName(bestWeapon));
        }

        for (int armourId : bestArmourForDefenceLevel()) {
            if (!hasItemAvailable(armourId)) {
                addNameIfPresent(names, getItemName(armourId));
            }
        }

        if (!hasItemAvailable(Gear.AMULET_OF_POWER)) {
            addNameIfPresent(names, getItemName(Gear.AMULET_OF_POWER));
        }

        if (!isWearingTeamCape() && getTeamCapeInInventoryName() == null && !hasTeamCapeInBank()) {
            addNameIfPresent(names, Gear.DEFAULT_TEAM_CAPE_NAME);
        }

        return names;
    }

    public String getItemName(int itemId) {
        ItemComposition item = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getItemManager().getItemComposition(itemId))
                .orElse(null);
        return item == null ? null : item.getName();
    }

    public List<String> getDefaultLootNames() {
        Set<String> names = new LinkedHashSet<>();
        for (int id : net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.loot.Loot.DEFAULT_LOOT) {
            String name = getItemName(id);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    private void addNameIfPresent(List<String> names, String name) {
        if (name != null && !name.isBlank() && !names.contains(name)) {
            names.add(name);
        }
    }

    private boolean hasItemAvailable(int itemId) {
        return Rs2Equipment.isWearing(itemId) || Rs2Inventory.count(itemId) > 0 || Rs2Bank.count(itemId) > 0;
    }

    private boolean isWearingTeamCape() {
        for (String candidate : TEAM_CAPE_NAME_CANDIDATES) {
            if (Rs2Equipment.isWearing(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTeamCapeInBank() {
        return getBestAvailableTeamCapeName() != null;
    }

    private String getBestAvailableTeamCapeName() {
        return Arrays.stream(TEAM_CAPE_NAME_CANDIDATES)
                .filter(Rs2Bank::hasItem)
                .findFirst()
                .orElse(null);
    }

    private String getTeamCapeInInventoryName() {
        return Arrays.stream(TEAM_CAPE_NAME_CANDIDATES)
                .filter(Rs2Inventory::hasItem)
                .findFirst()
                .orElse(null);
    }

    private static String[] buildTeamCapeNameCandidates() {
        List<String> names = new ArrayList<>();
        names.add(Gear.DEFAULT_TEAM_CAPE_NAME);
        for (int i = 1; i <= 50; i++) {
            names.add("Team-" + i + " cape");
        }
        return names.toArray(new String[0]);
    }

    private boolean hasFoodInInventory() {
        for (int foodId : Gear.FOOD_REQUIREMENTS) {
            if (Rs2Inventory.count(foodId) >= Gear.MIN_TROUT_REQUIRED) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBestWeaponEquipped() {
        return Rs2Equipment.isWearing(bestWeaponForAttackLevel());
    }

    private boolean hasBestArmourEquipped() {
        for (int armourId : bestArmourForDefenceLevel()) {
            if (!Rs2Equipment.isWearing(armourId)) {
                return false;
            }
        }
        return true;
    }

    private int getMeleeLevel(Skill skill) {
        return Microbot.getClient().getRealSkillLevel(skill);
    }

    private int[] getAllMeleeWeaponUpgrades() {
        return new int[]{
                Swords.RUNE_SCIMITAR,
                Swords.ADAMANT_SCIMITAR,
                Swords.MITHRIL_SCIMITAR,
                Swords.BLACK_SCIMITAR,
                Swords.STEEL_SCIMITAR,
                Swords.IRON_SCIMITAR,
                Swords.BRONZE_SCIMITAR
        };
    }

    private int[] getAllMeleeArmourUpgrades() {
        return new int[]{
                Armour.RUNE_FULL_HELM, Armour.RUNE_PLATEBODY, Armour.RUNE_PLATELEGS, Armour.RUNE_KITESHIELD,
                Armour.ADAMANT_FULL_HELM, Armour.ADAMANT_PLATEBODY, Armour.ADAMANT_PLATELEGS, Armour.ADAMANT_KITESHIELD,
                Armour.MITHRIL_FULL_HELM, Armour.MITHRIL_PLATEBODY, Armour.MITHRIL_PLATELEGS, Armour.MITHRIL_KITESHIELD,
                Armour.BLACK_FULL_HELM, Armour.BLACK_PLATEBODY, Armour.BLACK_PLATELEGS, Armour.BLACK_KITESHIELD,
                Armour.IRON_FULL_HELM, Armour.IRON_PLATEBODY, Armour.IRON_PLATELEGS, Armour.IRON_KITESHIELD,
                Armour.BRONZE_FULL_HELM, Armour.BRONZE_PLATEBODY, Armour.BRONZE_PLATELEGS, Armour.BRONZE_KITESHIELD
        };
    }

    private int bestWeaponForAttackLevel() {
        int attack = getMeleeLevel(Skill.ATTACK);
        if (attack >= Swords.RUNE_SCIMITAR_ATTACK_LEVEL) return Swords.RUNE_SCIMITAR;
        if (attack >= Swords.ADAMANT_SCIMITAR_ATTACK_LEVEL) return Swords.ADAMANT_SCIMITAR;
        if (attack >= Swords.MITHRIL_SCIMITAR_ATTACK_LEVEL) return Swords.MITHRIL_SCIMITAR;
        if (attack >= Swords.BLACK_SCIMITAR_ATTACK_LEVEL) return Swords.BLACK_SCIMITAR;
        if (attack >= Swords.STEEL_SCIMITAR_ATTACK_LEVEL) return Swords.STEEL_SCIMITAR;
        if (attack >= Swords.IRON_SCIMITAR_ATTACK_LEVEL) return Swords.IRON_SCIMITAR;
        return Swords.BRONZE_SCIMITAR;
    }

    private int[] bestArmourForDefenceLevel() {
        int defence = getMeleeLevel(Skill.DEFENCE);

        if (defence >= Armour.RUNE_ARMOUR_EQUIP_LEVEL) {
            return armourSet(Armour.RUNE_FULL_HELM, Armour.RUNE_PLATEBODY, Armour.RUNE_PLATELEGS, Armour.RUNE_KITESHIELD);
        }
        if (defence >= Armour.ADAMANT_ARMOUR_EQUIP_LEVEL) {
            return armourSet(Armour.ADAMANT_FULL_HELM, Armour.ADAMANT_PLATEBODY, Armour.ADAMANT_PLATELEGS, Armour.ADAMANT_KITESHIELD);
        }
        if (defence >= Armour.MITHRIL_ARMOUR_EQUIP_LEVEL) {
            return armourSet(Armour.MITHRIL_FULL_HELM, Armour.MITHRIL_PLATEBODY, Armour.MITHRIL_PLATELEGS, Armour.MITHRIL_KITESHIELD);
        }
        if (defence >= Armour.BLACK_ARMOUR_EQUIP_LEVEL) {
            return armourSet(Armour.BLACK_FULL_HELM, Armour.BLACK_PLATEBODY, Armour.BLACK_PLATELEGS, Armour.BLACK_KITESHIELD);
        }
        if (defence >= Armour.IRON_ARMOUR_EQUIP_LEVEL) {
            return armourSet(Armour.IRON_FULL_HELM, Armour.IRON_PLATEBODY, Armour.IRON_PLATELEGS, Armour.IRON_KITESHIELD);
        }

        return armourSet(Armour.BRONZE_FULL_HELM, Armour.BRONZE_PLATEBODY, Armour.BRONZE_PLATELEGS, Armour.BRONZE_KITESHIELD);
    }

    private int[] armourSet(int helm, int body, int legs, int shield) {
        return new int[]{helm, body, legs, shield};
    }

    private boolean containsId(int[] values, int target) {
        for (int value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }
}
