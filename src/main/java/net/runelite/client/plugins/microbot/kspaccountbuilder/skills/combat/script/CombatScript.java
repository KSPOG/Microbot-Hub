package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.script;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.kspaccountbuilder.grandexchange.GrandExchange;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.areas.MobArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.gear.CombatGearManager;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.loot.CombatLootManager;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.style.CombatStyleResolver;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.targeting.CombatTargetSelector;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

@Slf4j
public class CombatScript {

    private static final WorldPoint GOBLIN_AREA_CENTER = new WorldPoint(3252, 3230, 0);
    private static final WorldPoint COW_AREA_CENTER = new WorldPoint(3259, 3275, 0);
    private static final int AREA_LOOT_RADIUS = 12;

    private final GrandExchange grandExchange = new GrandExchange();
    private final CombatStyleResolver combatStyleResolver = new CombatStyleResolver();
    private final CombatTargetSelector combatTargetSelector = new CombatTargetSelector();
    private final CombatGearManager combatGearManager = new CombatGearManager();
    private final CombatLootManager combatLootManager = new CombatLootManager(120L);

    private String status = "Idle";

    public void initialize() {
        status = "Initializing combat";
        combatLootManager.reset();
    }

    public void shutdown() {
        status = "Combat stopped";
        combatLootManager.reset();
    }

    public String getStatus() {
        return status;
    }

    public boolean hasCombatSetupReady() {
        return combatGearManager.hasCombatSetupReady();
    }

    public void execute() {
        if (!Microbot.isLoggedIn()) {
            status = "Waiting for login";
            return;
        }

        if (!ensureCombatSetup()) {
            return;
        }

        if (!hasCombatSetupReady()) {
            status = "Waiting for required combat gear";
            return;
        }

        if (bankWhenInventoryFull()) {
            return;
        }

        if (buryBonesInInventory()) {
            return;
        }

        if (!isInTrainingArea() && !walkToTrainingArea()) {
            return;
        }

        if (!isAnimatingSafeForClick()) {
            status = "Waiting for animation to finish";
            return;
        }

        if (combatLootManager.tryLoot(AREA_LOOT_RADIUS, combatGearManager.getDefaultLootNames())) {
            status = "Looting drops";
            return;
        }

        ensureBalancedMeleeStatsTraining();
        attackTrainingMob();
    }

    private boolean ensureCombatSetup() {
        if (hasCombatSetupReady()) {
            status = "Combat setup ready";
            return true;
        }

        status = "Opening bank for combat setup";
        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }

        combatGearManager.withdrawFood();
        combatGearManager.withdrawBestAvailableGear();
        combatGearManager.equipInventoryGear();

        if (!combatGearManager.hasCombatSetupReady()) {
            if (combatGearManager.getMissingUpgradeNames().isEmpty()) {
                status = "Waiting to equip available gear";
                Rs2Bank.closeBank();
                return false;
            }

            status = "Missing upgrades - going to GE";
            Rs2Bank.closeBank();
            return sellLootAndBuyUpgrades();
        }

        Rs2Bank.closeBank();
        return true;
    }

    private boolean sellLootAndBuyUpgrades() {
        boolean success = grandExchange.sellAndBuyIfNeeded(
                combatGearManager.getSellItemIds(grandExchange.getDefaultSellItemIds()),
                combatGearManager::getMissingUpgradeNames,
                combatGearManager::getItemName
        );
        if (!success) {
            status = grandExchange.getStatus();
            return false;
        }

        status = "GE cycle complete";
        return true;
    }

    private boolean bankWhenInventoryFull() {
        if (!Rs2Inventory.isFull()) {
            return false;
        }

        status = "Inventory full - banking";
        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return true;
        }

        Rs2Bank.depositAllExcept(combatGearManager.getProtectedInventoryIds());
        combatGearManager.withdrawFood();
        combatGearManager.withdrawBestAvailableGear();
        combatGearManager.equipInventoryGear();
        Rs2Bank.closeBank();
        return true;
    }

    private boolean buryBonesInInventory() {
        if (Rs2Inventory.interact(ItemID.BONES, "Bury")) {
            status = "Burying bones";
            return true;
        }

        if (Rs2Inventory.interact(ItemID.BIG_BONES, "Bury")) {
            status = "Burying big bones";
            return true;
        }

        return false;
    }

    private boolean ensureBalancedMeleeStatsTraining() {
        if (Rs2Player.isInteracting() || Rs2Player.isAnimating()) {
            return true;
        }

        Skill skillToTrain = combatStyleResolver.getLowestMeleeSkill();
        int styleIndex = combatStyleResolver.findStyleIndexForSkill(skillToTrain);
        if (styleIndex < 0) {
            return false;
        }

        int currentStyle = Microbot.getVarbitPlayerValue(VarPlayer.ATTACK_STYLE);
        if (currentStyle == styleIndex) {
            return true;
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT) {
            Rs2Tab.switchToCombatOptionsTab();
        }

        status = "Balancing melee stats";
        Rs2Combat.setAttackStyle(combatStyleResolver.mapStyleWidget(styleIndex));
        return true;
    }

    private void attackTrainingMob() {
        if (!isAnimatingSafeForClick()) {
            status = "Waiting for animation to finish";
            return;
        }

        if (Rs2Player.isMoving()) {
            status = "Moving";
            return;
        }

        if (shouldTrainAtCows()) {
            status = "Attacking cows";
            Rs2NpcModel target = combatTargetSelector.getBestAttackableNpc(AREA_LOOT_RADIUS, this::isInTrainingArea, "Cow calf", "Cow");
            if (target != null) {
                Rs2Npc.interact(target, "Attack");
                return;
            }
            Rs2Npc.attack("Cow calf");
            Rs2Npc.attack("Cow");
            return;
        }

        status = "Attacking goblin";
        Rs2NpcModel target = combatTargetSelector.getBestAttackableNpc(AREA_LOOT_RADIUS, this::isInTrainingArea, "Goblin");
        if (target != null) {
            Rs2Npc.interact(target, "Attack");
            return;
        }
        Rs2Npc.attack("Goblin");
    }


    private boolean walkToTrainingArea() {
        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            return false;
        }

        if (shouldTrainAtCows()) {
            status = "Walking to cow area";
            return Rs2Walker.walkTo(COW_AREA_CENTER);
        }

        status = "Walking to goblin area";
        return Rs2Walker.walkTo(GOBLIN_AREA_CENTER);
    }

    private boolean shouldTrainAtCows() {
        int attack = getMeleeLevel(Skill.ATTACK);
        int strength = getMeleeLevel(Skill.STRENGTH);
        int defence = getMeleeLevel(Skill.DEFENCE);

        boolean hasPassedGoblinStarterLevels = attack >= 5 || strength >= 5 || defence >= 5;
        boolean stillNeedsMeleeLevels = attack < 20 || strength < 20 || defence < 20;

        return hasPassedGoblinStarterLevels && stillNeedsMeleeLevels;
       boolean allAtLeastFive = attack >= 5 && strength >= 5 && defence >= 5;
        boolean anyBelowTwenty = attack < 20 || strength < 20 || defence < 20;

        return allAtLeastFive && anyBelowTwenty;

    }

    private boolean isInTrainingArea() {
        if (shouldTrainAtCows()) {
            return MobArea.COWS.contains(Rs2Player.getWorldLocation());
        }
        return MobArea.GOBLINS.contains(Rs2Player.getWorldLocation());
    }

    private boolean isAnimatingSafeForClick() {
        return !Rs2Player.isAnimating();
    }

    private int getMeleeLevel(Skill skill) {
        return Microbot.getClient().getRealSkillLevel(skill);
    }
}
