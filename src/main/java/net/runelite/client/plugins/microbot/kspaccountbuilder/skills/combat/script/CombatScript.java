package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.script;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.inventory.Rs2ItemModel;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.areas.MobArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.gear.armour.Armour;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.gear.swords.Swords;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.ge.sell.Sell;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.loot.Loot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.needed.Gear;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.List;

@Slf4j
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

        return hasFoodInInventory() && hasBestWeaponEquipped() && hasBestArmourEquipped() && isWearingTeamCape();


        return hasFoodInInventory() && hasBestWeaponEquipped() && hasBestArmourEquipped() && isWearingTeamCape();

    }

    public void execute() {
        if (!Microbot.isLoggedIn()) {
            status = "Waiting for login";
            return;
        }

        if (isPlayerBusy()) {
            status = "Waiting for current action";
            return;
        }

        if (!ensureCombatSetup()) {
            return;
        }

        if (buryBonesInInventory()) {
            return;
        }

        if (bankWhenInventoryFull()) {
            return;
        }

        if (!ensureBalancedMeleeStatsTraining()) {
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


        if (!hasBestWeaponEquipped() || !hasBestArmourEquipped() || !isWearingTeamCape()) {


        if (!hasBestWeaponEquipped() || !hasBestArmourEquipped() || !isWearingTeamCape()) {


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

        if (!isWearingTeamCape() && hasTeamCapeInBank()) {
            Rs2Bank.withdrawX(this::isTeamCapeItem, 1);
            Global.sleep(150);
        }
    }

    private void equipInventoryGear() {
        Rs2Inventory.wield(bestWeaponForAttackLevel());
        for (int armourId : bestArmourForDefenceLevel()) {
            Rs2Inventory.wield(armourId);
        }
        Rs2Inventory.wield(Gear.AMULET_OF_POWER);
        if (!isWearingTeamCape()) {
            Rs2Inventory.interact(this::isTeamCapeItem, "Wear");
        }
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

        if (!isWearingTeamCape() && !hasTeamCapeInBank()) {
            names.add(Gear.DEFAULT_TEAM_CAPE_NAME);
        }

        return names;
    }

    private String getItemName(int itemId) {
        ItemComposition item = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getItemManager().getItemComposition(itemId))
                .orElse(null);
        return item == null ? null : item.getName();
    }

    private boolean isWearingTeamCape() {


        return Rs2Equipment.isWearing(this::isTeamCapeItem)
                || Rs2Equipment.isWearing(Gear.DEFAULT_TEAM_CAPE_NAME);
    }

    private boolean hasTeamCapeInBank() {
        return Rs2Bank.hasItem(this::isTeamCapeItem)
                || Rs2Bank.hasItem(Gear.DEFAULT_TEAM_CAPE_NAME);


        return Rs2Equipment.isWearing(this::isTeamCapeItem);
    }

    private boolean hasTeamCapeInBank() {
        return Rs2Bank.hasItem(this::isTeamCapeItem);

    }

    private boolean isTeamCapeItem(Rs2ItemModel item) {
        if (item == null || item.getName() == null) {
            return false;
        }

        String lowerName = item.getName().toLowerCase();

        if (lowerName.contains(Gear.DEFAULT_TEAM_CAPE_NAME.toLowerCase())) {
            return true;
        }


        for (String teamCapeNameMatcher : Gear.TEAM_CAPE_NAME_MATCHERS) {
            if (lowerName.contains(teamCapeNameMatcher.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean buryBonesInInventory() {
        List<Rs2ItemModel> bones = Rs2Inventory.getBones();
        if (bones != null && !bones.isEmpty() && Rs2Inventory.interact(bones.get(0), "Bury")) {
            status = "Burying bones";
            Global.sleep(350);
            return true;
        }

        if (Rs2Inventory.interact(ItemID.BIG_BONES, "Bury")) {
            status = "Burying big bones";
            Global.sleep(350);
            return true;
        }

        return false;
    }

    private boolean bankWhenInventoryFull() {
        if (!Rs2Inventory.isFull()) {
            return false;
        }

        status = "Inventory full - banking";
        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }

        Rs2Bank.depositAll(item -> item != null && shouldBankInventoryItem(item));
        Global.sleep(250);

        withdrawFood();
        withdrawBestAvailableGear();
        equipInventoryGear();

        Rs2Bank.closeBank();
        return true;
    }

    private boolean shouldBankInventoryItem(Rs2ItemModel item) {
        if (item.getName() != null && isTeamCapeItem(item)) {
            return false;
        }

        return !isGearItemId(item.getId());
    }

    private boolean isGearItemId(int itemId) {
        if (itemId == Gear.AMULET_OF_POWER) {
            return true;
        }

        for (int foodId : Gear.FOOD_REQUIREMENTS) {
            if (foodId == itemId) {
                return true;
            }
        }

        for (int weaponId : Gear.BEST_MELEE_WEAPONS_BY_LEVEL) {
            if (weaponId == itemId) {
                return true;
            }
        }

        for (int armourId : Gear.BEST_MELEE_ARMOUR_BY_LEVEL) {
            if (armourId == itemId) {
                return true;
            }
        }

        return false;
    }

    private boolean ensureBalancedMeleeStatsTraining() {
        if (Rs2Player.isInCombat() || Rs2Player.isInteracting()) {
            return true;
        }

        int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        int strengthLevel = Microbot.getClient().getRealSkillLevel(Skill.STRENGTH);
        int defenceLevel = Microbot.getClient().getRealSkillLevel(Skill.DEFENCE);

        int targetStyle = 1;
        if (attackLevel <= strengthLevel && attackLevel <= defenceLevel) {
            targetStyle = 0;
        } else if (defenceLevel <= attackLevel && defenceLevel <= strengthLevel) {
            targetStyle = 3;
        }

        int currentStyle = Microbot.getVarbitPlayerValue(VarPlayer.ATTACK_STYLE);
        if (currentStyle == targetStyle) {
            return true;
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT) {
            Rs2Tab.switchToCombatOptionsTab();
            Global.sleep(250);
        }

        status = "Balancing melee stats";
        Rs2Combat.setAttackStyle(mapStyleWidget(targetStyle));
        Global.sleep(250);
        return true;
    }

    private WidgetInfo mapStyleWidget(int styleIndex) {
        if (styleIndex == 0) {
            return WidgetInfo.COMBAT_STYLE_ONE;
        }
        if (styleIndex == 1) {
            return WidgetInfo.COMBAT_STYLE_TWO;
        }
        if (styleIndex == 2) {
            return WidgetInfo.COMBAT_STYLE_THREE;
        }
        return WidgetInfo.COMBAT_STYLE_FOUR;
    }

    private boolean walkToGoblinArea() {
        if (MobArea.GOBLINS.contains(Rs2Player.getWorldLocation())) {
            return true;
        }

        if (isPlayerBusy()) {
            return false;
        }

        status = "Walking to goblin area";
        return Rs2Walker.walkTo(GOBLIN_AREA_CENTER);
    }

    private boolean lootTrainingAreaDrops() {
        if (isPlayerBusy()) {
            return false;
        }

        if (Loot.lootCoins(AREA_LOOT_RADIUS)) {
            status = "Looting coins";
            return true;
        }

        for (int lootId : Loot.DEFAULT_LOOT) {
            String lootName = getItemName(lootId);
            if (lootName == null || lootName.isBlank()) {
                continue;
            }

            if (Rs2GroundItem.lootItemsBasedOnNames(new LootingParameters(
                    AREA_LOOT_RADIUS,
                    1,
                    1,
                    0,

                    true,

                    false,

                    true,
                    lootName
            ))) {
                status = "Looting drops";
                return true;
            }
        }

        return false;
    }

    private void attackGoblin() {
        if (Rs2Player.isInCombat()) {
            status = "Fighting";
            return;
        }

        if (isPlayerBusy()) {
            return;
        }

        status = "Attacking goblin";
        Rs2Npc.attack("Goblin");
    }

    private boolean isPlayerBusy() {
        return Rs2Player.isMoving() || Rs2Player.isInteracting() || Rs2Player.isAnimating();
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

    private int bestWeaponForAttackLevel() {
        int attack = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        if (attack >= Swords.RUNE_SCIMITAR_ATTACK_LEVEL) return Swords.RUNE_SCIMITAR;
        if (attack >= Swords.ADAMANT_SCIMITAR_ATTACK_LEVEL) return Swords.ADAMANT_SCIMITAR;
        if (attack >= Swords.MITHRIL_SCIMITAR_ATTACK_LEVEL) return Swords.MITHRIL_SCIMITAR;
        if (attack >= Swords.BLACK_SCIMITAR_ATTACK_LEVEL) return Swords.BLACK_SCIMITAR;
        if (attack >= Swords.STEEL_SCIMITAR_ATTACK_LEVEL) return Swords.STEEL_SCIMITAR;
        if (attack >= Swords.IRON_SCIMITAR_ATTACK_LEVEL) return Swords.IRON_SCIMITAR;
        return Swords.BRONZE_SCIMITAR;
    }

    private int[] bestArmourForDefenceLevel() {
        int defence = Microbot.getClient().getRealSkillLevel(Skill.DEFENCE);

        if (defence >= Armour.RUNE_ARMOUR_EQUIP_LEVEL) return new int[]{
                Armour.RUNE_FULL_HELM,
                Armour.RUNE_PLATEBODY,
                Armour.RUNE_PLATELEGS,
                Armour.RUNE_KITESHIELD
        };
        if (defence >= Armour.ADAMANT_ARMOUR_EQUIP_LEVEL) return new int[]{
                Armour.ADAMANT_FULL_HELM,
                Armour.ADAMANT_PLATEBODY,
                Armour.ADAMANT_PLATELEGS,
                Armour.ADAMANT_KITESHIELD
        };
        if (defence >= Armour.MITHRIL_ARMOUR_EQUIP_LEVEL) return new int[]{
                Armour.MITHRIL_FULL_HELM,
                Armour.MITHRIL_PLATEBODY,
                Armour.MITHRIL_PLATELEGS,
                Armour.MITHRIL_KITESHIELD
        };
        if (defence >= Armour.BLACK_ARMOUR_EQUIP_LEVEL) return new int[]{
                Armour.BLACK_FULL_HELM,
                Armour.BLACK_PLATEBODY,
                Armour.BLACK_PLATELEGS,
                Armour.BLACK_KITESHIELD
        };
        if (defence >= Armour.IRON_ARMOUR_EQUIP_LEVEL) return new int[]{
                Armour.IRON_FULL_HELM,
                Armour.IRON_PLATEBODY,
                Armour.IRON_PLATELEGS,
                Armour.IRON_KITESHIELD
        };

        return new int[]{
                Armour.BRONZE_FULL_HELM,
                Armour.BRONZE_PLATEBODY,
                Armour.BRONZE_PLATELEGS,
                Armour.BRONZE_KITESHIELD
        };
    }
}
