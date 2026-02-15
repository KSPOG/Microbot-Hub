package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.script;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.levels.TreeLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.needed.ItemReqs;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.trees.TreeID;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.tools.AxeWCLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.tools.EquipLevels;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

@Slf4j
public class WoodcuttingScript {
    private static final String CHOP_ACTION = "Chop down";

    private static final Axe[] AXE_PRIORITY = new Axe[]{
            new Axe(ItemReqs.RUNE_AXE, AxeWCLevels.RUNE_AXE, EquipLevels.RUNE_AXE),
            new Axe(ItemReqs.ADAMANT_AXE, AxeWCLevels.ADAMANT_AXE, EquipLevels.ADAMANT_AXE),
            new Axe(ItemReqs.MITHRIL_AXE, AxeWCLevels.MITHRIL_AXE, EquipLevels.MITHRIL_AXE),
            new Axe(ItemReqs.BLACK_AXE, AxeWCLevels.BLACK_AXE, EquipLevels.BLACK_AXE),
            new Axe(ItemReqs.STEEL_AXE, AxeWCLevels.STEEL_AXE, EquipLevels.STEEL_AXE),
            new Axe(ItemReqs.BRONZE_AXE, AxeWCLevels.BRONZE_AXE, EquipLevels.BRONZE_AXE)
    };

    public void initialize() {
        // reserved for future state initialization
    }

    public void shutdown() {
        // reserved for future cleanup
    }

    public boolean hasRequiredTools() {
        for (int axeId : ItemReqs.ACCEPTED_AXE_IDS) {
            if (Rs2Equipment.isWearing(axeId)) {
                return true;
            }
        }
        return false;
    }

    public void execute() {
        if (!bankForBestAvailableAxe()) {
            log.debug("No bankable/equippable axe found for current levels");
            return;
        }

        TreeTarget target = resolveTreeTarget();
        Rs2Walker.walkTo(target.getLocation());
        Rs2GameObject.interact(target.getTreeIds(), CHOP_ACTION);
    }

    private boolean bankForBestAvailableAxe() {
        int wcLevel = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);
        int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);

        if (isWearingBestPossibleAxe(wcLevel, attackLevel)) {
            return true;
        }

        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }

        Rs2Bank.depositAllExcept(ItemReqs.ACCEPTED_AXE_IDS);

        int axeToEquip = findBestAxeInBank(wcLevel, attackLevel);
        if (axeToEquip == -1) {
            Rs2Bank.closeBank();
            return hasRequiredTools();
        }

        Rs2Bank.withdrawAndEquip(axeToEquip);
        Rs2Bank.closeBank();
        return Rs2Equipment.isWearing(axeToEquip);
    }

    private boolean isWearingBestPossibleAxe(int wcLevel, int attackLevel) {
        for (Axe axe : AXE_PRIORITY) {
            if (wcLevel >= axe.getWoodcuttingLevel() && attackLevel >= axe.getAttackLevel()) {
                return Rs2Equipment.isWearing(axe.getItemId());
            }
        }
        return false;
    }

    private int findBestAxeInBank(int wcLevel, int attackLevel) {
        for (Axe axe : AXE_PRIORITY) {
            if (wcLevel < axe.getWoodcuttingLevel() || attackLevel < axe.getAttackLevel()) {
                continue;
            }

            if (Rs2Bank.hasItem(axe.getItemId())) {
                return axe.getItemId();
            }
        }

        return -1;
    }

    private TreeTarget resolveTreeTarget() {
        int wcLevel = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);
        int combatLevel = Microbot.getClientThread().invoke(() ->
                Microbot.getClient().getLocalPlayer() != null
                        ? Microbot.getClient().getLocalPlayer().getCombatLevel()
                        : 0
        );

        if (wcLevel >= TreeLevels.YEW) {
            return new TreeTarget(Areas.YEW, TreeID.YEW);
        }

        if (wcLevel >= TreeLevels.WILLOW) {
            return new TreeTarget(Areas.WILLOW, TreeID.WILLOW);
        }

        if (wcLevel >= TreeLevels.OAK) {
            return new TreeTarget(combatLevel >= 40 ? Areas.OAK_DRAYNOR : Areas.OAK_VARROCK, TreeID.OAK);
        }

        return new TreeTarget(Areas.TREE, TreeID.NORMAL);
    }

    private static final class Axe {
        private final int itemId;
        private final int woodcuttingLevel;
        private final int attackLevel;

        private Axe(int itemId, int woodcuttingLevel, int attackLevel) {
            this.itemId = itemId;
            this.woodcuttingLevel = woodcuttingLevel;
            this.attackLevel = attackLevel;
        }

        private int getItemId() {
            return itemId;
        }

        private int getWoodcuttingLevel() {
            return woodcuttingLevel;
        }

        private int getAttackLevel() {
            return attackLevel;
        }
    }

    private static final class TreeTarget {
        private final WorldPoint location;
        private final int[] treeIds;

        private TreeTarget(WorldPoint location, int[] treeIds) {
            this.location = location;
            this.treeIds = treeIds;
        }

        private WorldPoint getLocation() {
            return location;
        }

        private int[] getTreeIds() {
            return treeIds;
        }
    }
}
