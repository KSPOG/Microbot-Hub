package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.script;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.areas.MobArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.ge.sell.Sell;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.loot.Loot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.needed.Gear;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.List;

@Slf4j

public final class CombatScript {


public class CombatScript {


    private static final WorldPoint GOBLIN_AREA_CENTER = new WorldPoint(3252, 3230, 0);
    private static final int AREA_LOOT_RADIUS = 12;

    private String status = "Idle";

    public void initialize() {
        status = "Initializing combat";
    }

    public void shutdown() {
        status = "Combat stopped";
    }

    public String getStatus() {
        return status;
    }

    public boolean hasCombatSetupReady() {
        return hasFoodInInventory() && hasBestWeaponEquipped() && hasArmourPieceEquipped();
    }

    public void execute() {
        if (!Microbot.isLoggedIn()) {
            status = "Waiting for login";
            return;
        }

        if (!ensureCombatSetup()) {
            return;
        }

        if (!walkToGoblinArea()) {
            return;
        }

        if (lootTrainingAreaDrops()) {
            return;
        }

        attackGoblin();
    }

    private boolean ensureCombatSetup() {
        if (hasCombatSetupReady() && hasFoodInInventory()) {
            status = "Combat setup ready";
            return true;
        }

        status = "Opening bank for combat setup";
        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }

        withdrawFood();
        withdrawBestAvailableGear();
        equipInventoryGear();

        if (!hasBestWeaponEquipped() || !hasArmourPieceEquipped()) {
            status = "Missing upgrades - going to GE";
            Rs2Bank.closeBank();
            return sellLootAndBuyUpgrades();
        }

        Rs2Bank.closeBank();
        return true;
    }

    private void withdrawFood() {
        if (hasFoodInInventory()) {
            return;
        }

        for (int foodId : Gear.FOOD_REQUIREMENTS) {
            int needed = Math.max(0, Gear.MIN_TROUT_REQUIRED - Rs2Inventory.count(foodId));
            if (needed > 0) {
                Rs2Bank.withdrawX(foodId, needed);
                Global.sleep(250);
            }
        }
    }

    private void withdrawBestAvailableGear() {
        int bestWeapon = bestWeaponForAttackLevel();
        if (!Rs2Equipment.isWearing(bestWeapon) && Rs2Bank.count(bestWeapon) > 0) {
            Rs2Bank.withdrawX(bestWeapon, 1);
            Global.sleep(200);
        }

        for (int armourId : bestArmourForDefenceLevel()) {
            if (!Rs2Equipment.isWearing(armourId) && Rs2Bank.count(armourId) > 0) {
                Rs2Bank.withdrawX(armourId, 1);
                Global.sleep(150);
            }
        }

        if (!Rs2Equipment.isWearing(Gear.AMULET_OF_POWER) && Rs2Bank.count(Gear.AMULET_OF_POWER) > 0) {
            Rs2Bank.withdrawX(Gear.AMULET_OF_POWER, 1);
            Global.sleep(150);
        }
    }

    private void equipInventoryGear() {
        Rs2Inventory.wield(bestWeaponForAttackLevel());
        for (int armourId : bestArmourForDefenceLevel()) {
            Rs2Inventory.wield(armourId);
        }
        Rs2Inventory.wield(Gear.AMULET_OF_POWER);
    }

    private boolean sellLootAndBuyUpgrades() {
        if (!Rs2GrandExchange.walkToGrandExchange() || !Rs2GrandExchange.openExchange()) {
            status = "Unable to access GE";
            return false;
        }

        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            status = "Unable to open GE bank";
            return false;
        }

        Rs2Bank.depositAll();
        for (int sellId : Sell.DEFAULT_SELL) {
            int bankCount = Rs2Bank.count(sellId);
            if (bankCount > 1) {
                Rs2Bank.withdrawX(sellId, bankCount - 1);
                Global.sleep(120);
            }
        }
        Rs2Bank.closeBank();

        status = "Selling combat loot";
        for (int sellId : Sell.DEFAULT_SELL) {
            int invCount = Rs2Inventory.count(sellId);
            if (invCount <= 0) {
                continue;
            }

            String itemName = getItemName(sellId);
            if (itemName == null || itemName.isBlank()) {
                continue;
            }

            placeOffer(itemName, GrandExchangeAction.SELL, invCount);
            Global.sleep(350);
        }

        Rs2GrandExchange.collectAllToBank();

        status = "Buying upgrades";
        List<String> upgradesToBuy = getMissingUpgradeNames();
        for (String itemName : upgradesToBuy) {
            placeOffer(itemName, GrandExchangeAction.BUY, 1);
            Global.sleep(350);
        }

        Rs2GrandExchange.collectAllToBank();
        Rs2GrandExchange.closeExchange();
        return true;
    }

    private void placeOffer(String itemName, GrandExchangeAction action, int quantity) {
        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .action(action)
                .itemName(itemName)
                .quantity(Math.max(1, quantity))
                .percent(8)
                .closeAfterCompletion(false)
                .build();
        Rs2GrandExchange.processOffer(request);
    }

    private List<String> getMissingUpgradeNames() {
        List<String> names = new ArrayList<>();

        int bestWeapon = bestWeaponForAttackLevel();
        if (!Rs2Equipment.isWearing(bestWeapon) && Rs2Bank.count(bestWeapon) <= 0) {
            String weaponName = getItemName(bestWeapon);
            if (weaponName != null) {
                names.add(weaponName);
            }
        }

        for (int armourId : bestArmourForDefenceLevel()) {
            if (!Rs2Equipment.isWearing(armourId) && Rs2Bank.count(armourId) <= 0) {
                String armourName = getItemName(armourId);
                if (armourName != null) {
                    names.add(armourName);
                }
            }
        }

        if (!Rs2Equipment.isWearing(Gear.AMULET_OF_POWER) && Rs2Bank.count(Gear.AMULET_OF_POWER) <= 0) {
            String amuletName = getItemName(Gear.AMULET_OF_POWER);
            if (amuletName != null) {
                names.add(amuletName);
            }
        }

        return names;
    }

    private String getItemName(int itemId) {
        ItemComposition item = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getItemManager().getItemComposition(itemId))
                .orElse(null);
        return item == null ? null : item.getName();
    }

    private boolean walkToGoblinArea() {
        if (MobArea.GOBLINS.contains(Rs2Player.getWorldLocation())) {
            return true;
        }

        status = "Walking to goblin area";
        return Rs2Walker.walkTo(GOBLIN_AREA_CENTER);
    }

    private boolean lootTrainingAreaDrops() {
        if (Loot.lootCoins(AREA_LOOT_RADIUS)) {
            status = "Looting coins";
            return true;
        }

        for (int lootId : Loot.DEFAULT_LOOT) {
            if (Rs2GroundItem.loot(lootId, AREA_LOOT_RADIUS)) {
                status = "Looting drops";
                return true;
            }
        }

        return false;
    }

    private void attackGoblin() {
        if (Rs2Player.isInCombat() || Rs2Player.isInteracting() || Rs2Player.isAnimating()) {
            status = "Fighting";
            return;
        }

        status = "Attacking goblin";
        Rs2Npc.attack("Goblin");
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

    private boolean hasArmourPieceEquipped() {
        for (int armourId : bestArmourForDefenceLevel()) {
            if (Rs2Equipment.isWearing(armourId)) {
                return true;
            }
        }
        return false;
    }

    private int bestWeaponForAttackLevel() {
        int attack = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        if (attack >= 40) return Gear.BEST_MELEE_WEAPONS_BY_LEVEL[0];
        if (attack >= 30) return Gear.BEST_MELEE_WEAPONS_BY_LEVEL[1];
        if (attack >= 20) return Gear.BEST_MELEE_WEAPONS_BY_LEVEL[2];
        if (attack >= 10) return Gear.BEST_MELEE_WEAPONS_BY_LEVEL[3];
        if (attack >= 5) return Gear.BEST_MELEE_WEAPONS_BY_LEVEL[4];
        if (attack >= 1) return Gear.BEST_MELEE_WEAPONS_BY_LEVEL[5];
        return Gear.BEST_MELEE_WEAPONS_BY_LEVEL[6];
    }

    private int[] bestArmourForDefenceLevel() {
        int defence = Microbot.getClient().getRealSkillLevel(Skill.DEFENCE);

        if (defence >= 40) return new int[]{
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[0],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[1],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[2],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[3]
        };
        if (defence >= 30) return new int[]{
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[4],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[5],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[6],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[7]
        };
        if (defence >= 20) return new int[]{
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[8],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[9],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[10],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[11]
        };
        if (defence >= 10) return new int[]{
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[12],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[13],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[14],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[15]
        };
        if (defence >= 1) return new int[]{
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[16],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[17],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[18],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[19]
        };

        return new int[]{
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[20],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[21],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[22],
                Gear.BEST_MELEE_ARMOUR_BY_LEVEL[23]
        };
    }
}
