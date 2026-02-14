package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.script;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.levels.TreeLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.needed.ItemReqs;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.tools.AxeWCLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.tools.EquipLevels;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.needed.ItemReqs;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;


/**
 * Skeleton woodcutting workflow for KSPAccountBuilder.
 */
@Slf4j
@Getter
public class WoodcuttingScript {

    private static final int[] AXE_IDS_DESC = {
            ItemReqs.RUNE_AXE,
            ItemReqs.ADAMANT_AXE,
            ItemReqs.MITHRIL_AXE,
            ItemReqs.BLACK_AXE,
            ItemReqs.STEEL_AXE,
            ItemReqs.BRONZE_AXE
    };

    private WoodcuttingState state = WoodcuttingState.NOT_STARTED;

    public void initialize() {
        state = WoodcuttingState.INITIALIZING;
        log.info("WoodcuttingScript skeleton initialized.");
    }

    public boolean hasRequiredTools() {
        final int axeCount = getUsableAxeCount();
        return axeCount >= ItemReqs.MIN_AXE_COUNT;
    }

    private int getUsableAxeCount() {
        int count = 0;
        for (int axeId : ItemReqs.ACCEPTED_AXE_IDS) {
            if (Rs2Inventory.hasItem(axeId) || Rs2Equipment.isWearing(axeId)) {
                count++;
            }
        }
        return count;
    }

    public void execute() {
        state = WoodcuttingState.RUNNING;

        if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
            return;
        }

        int woodcuttingLevel = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);
        int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);

        if (!ensureBestAxeForCurrentLevel(woodcuttingLevel, attackLevel)) {
            return;
        }

        WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
        WorldArea bestArea = getBestAreaForLevel(woodcuttingLevel, playerLocation);
        if (!bestArea.contains(playerLocation)) {
            Rs2Walker.walkTo(getAreaCenter(bestArea), 3);
        }

        // TODO: Add tree selection and cutting workflow.
    }

    private boolean ensureBestAxeForCurrentLevel(int woodcuttingLevel, int attackLevel) {
        int bestAxeForLevel = getBestAxeIdByWoodcuttingLevel(woodcuttingLevel);
        if (bestAxeForLevel == -1) {
            return false;
        }

        if (!hasAxe(bestAxeForLevel)) {
            if (!Rs2Bank.walkToBank()) {
                return false;
            }

            if (!Rs2Bank.openBank()) {
                return false;
            }

            int bestAvailableAxe = getBestAvailableAxeId(woodcuttingLevel);
            if (bestAvailableAxe == -1) {
                log.info("No usable axe found in bank/inventory/equipment for woodcutting level {}.", woodcuttingLevel);
                return false;
            }

            if (!hasAxe(bestAvailableAxe)) {
                Rs2Bank.withdrawItem(bestAvailableAxe);
            }
            bestAxeForLevel = bestAvailableAxe;
        }

        if (canEquip(bestAxeForLevel, attackLevel)
                && Rs2Inventory.hasItem(bestAxeForLevel)
                && !Rs2Equipment.isWearing(bestAxeForLevel)) {
            Rs2Inventory.interact(bestAxeForLevel, "Wield");
        }

        return hasAxe(bestAxeForLevel);
    }

    private int getBestAvailableAxeId(int woodcuttingLevel) {
        for (int axeId : AXE_IDS_DESC) {
            if (woodcuttingLevel < getWoodcuttingRequirement(axeId)) {
                continue;
            }

            if (hasAxe(axeId) || Rs2Bank.hasItem(axeId)) {
                return axeId;
            }
        }

        return -1;
    }

    private boolean hasAxe(int axeId) {
        return Rs2Inventory.hasItem(axeId) || Rs2Equipment.isWearing(axeId);
    }

    private int getBestAxeIdByWoodcuttingLevel(int woodcuttingLevel) {
        for (int axeId : AXE_IDS_DESC) {
            if (woodcuttingLevel >= getWoodcuttingRequirement(axeId)) {
                return axeId;
            }
        }

        return -1;
    }

    private int getWoodcuttingRequirement(int axeId) {
        if (axeId == ItemReqs.RUNE_AXE) return AxeWCLevels.RUNE_AXE;
        if (axeId == ItemReqs.ADAMANT_AXE) return AxeWCLevels.ADAMANT_AXE;
        if (axeId == ItemReqs.MITHRIL_AXE) return AxeWCLevels.MITHRIL_AXE;
        if (axeId == ItemReqs.BLACK_AXE) return AxeWCLevels.BLACK_AXE;
        if (axeId == ItemReqs.STEEL_AXE) return AxeWCLevels.STEEL_AXE;
        return AxeWCLevels.BRONZE_AXE;
    }

    private boolean canEquip(int axeId, int attackLevel) {
        return attackLevel >= getAttackRequirement(axeId);
    }

    private int getAttackRequirement(int axeId) {
        if (axeId == ItemReqs.RUNE_AXE) return EquipLevels.RUNE_AXE;
        if (axeId == ItemReqs.ADAMANT_AXE) return EquipLevels.ADAMANT_AXE;
        if (axeId == ItemReqs.MITHRIL_AXE) return EquipLevels.MITHRIL_AXE;
        if (axeId == ItemReqs.BLACK_AXE) return EquipLevels.BLACK_AXE;
        if (axeId == ItemReqs.STEEL_AXE) return EquipLevels.STEEL_AXE;
        return EquipLevels.BRONZE_AXE;
    }

    private WorldArea getBestAreaForLevel(int woodcuttingLevel, WorldPoint playerLocation) {
        if (woodcuttingLevel >= TreeLevels.YEW) {
            return Areas.YEW;
        }

        if (woodcuttingLevel >= TreeLevels.WILLOW) {
            return Areas.WILLOW;
        }

        if (woodcuttingLevel >= TreeLevels.OAK) {
            return getClosestOakArea(playerLocation);
        }

        return Areas.TREES;
    }

    private WorldArea getClosestOakArea(WorldPoint playerLocation) {
        WorldPoint varrockOakCenter = getAreaCenter(Areas.OAK_VARROCK);
        WorldPoint draynorOakCenter = getAreaCenter(Areas.OAK_DRAYNOR);

        int distanceToVarrock = playerLocation.distanceTo2D(varrockOakCenter);
        int distanceToDraynor = playerLocation.distanceTo2D(draynorOakCenter);

        return distanceToVarrock <= distanceToDraynor ? Areas.OAK_VARROCK : Areas.OAK_DRAYNOR;
    }

    private WorldPoint getAreaCenter(WorldArea area) {
        return new WorldPoint(
                area.getX() + (area.getWidth() / 2),
                area.getY() + (area.getHeight() / 2),
                area.getPlane());
    }

    public void shutdown() {
        state = WoodcuttingState.STOPPED;
    }

    public enum WoodcuttingState {
        NOT_STARTED,
        INITIALIZING,
        RUNNING,
        STOPPED
    }
}
