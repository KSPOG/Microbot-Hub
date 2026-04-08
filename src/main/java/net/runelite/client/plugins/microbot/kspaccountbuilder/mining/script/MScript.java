package net.runelite.client.plugins.microbot.kspaccountbuilder.mining.script;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KSPAccountBuilderConfig;
import net.runelite.client.plugins.microbot.kspaccountbuilder.mining.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.mining.pickelevel.PickaxeELevel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.mining.pickulevel.PickaxeMLevel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public class MScript extends Script {
    private static final String COPPER_ORE = "Copper ore";
    private KSPAccountBuilderConfig config;
    private static final String TIN_ORE = "Tin ore";

    public boolean run(KSPAccountBuilderConfig config) {
        this.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::execute, 0, 800, TimeUnit.MILLISECONDS);
        return true;
    }

    private void execute() {
        if (!Microbot.isLoggedIn() || !super.run()) return;
        if (Microbot.getClient().getRealSkillLevel(Skill.MINING) < 1) return;
        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) return;

        if (Rs2Inventory.isFull()) {
            bankAndDepositEverythingExceptPickaxe();
            return;
        }

        equipBestAvailablePickaxeForCurrentAttackLevel();

        MiningTarget target = getMiningTargetForCurrentLevel();

        if (!isInsideArea(target.area)) {
            walkToAreaCenter(target.area);
            return;
        }

        mineTarget(target);
    }

    private MiningTarget getMiningTargetForCurrentLevel() {
        int miningLevel = Microbot.getClient().getRealSkillLevel(Skill.MINING);

        if (miningLevel >= 30) {
            return new MiningTarget(Areas.COAL_AREA, "Coal", false);
        }
        if (miningLevel >= 20) {
            return new MiningTarget(Areas.SILVER_ORE_AREA, "Silver", false);
        }
        if (miningLevel >= 15) {
            return new MiningTarget(Areas.IRON_AREA, "Iron", false);
        }

        return new MiningTarget(Areas.COPPER_TIN_AREA, "", true);
    }

    private void mineTarget(MiningTarget target) {
        if (target.balancedCopperTin) {
            mineEqualCopperAndTin();
            return;
        }

        if (!Rs2GameObject.interact(target.rockName, "Mine")) {
            Rs2GameObject.interact("Rocks", "Mine");
        }

        sleepUntil(Rs2Player::isAnimating, 5000);
    }

    private void bankAndDepositEverythingExceptPickaxe() {
        BankLocation nearestBank = Rs2Bank.getNearestBank();
        if (nearestBank == null) return;

        if (!Rs2Bank.isOpen()) {
            Rs2Bank.walkToBankAndUseBank(nearestBank);
            return;
        }

        handlePickaxeUpgradeAtBank();

        String bestPickaxe = getBestMiningPickaxeName();
        if (bestPickaxe != null) {
            Rs2Bank.depositAllExcept(bestPickaxe);
        } else {
            Rs2Bank.depositAll();
        }

        depositOutdatedPickaxes(bestPickaxe);
        if (hasOutdatedPickaxeInInventory(bestPickaxe)) {
            return;
        }

        sleepUntil(() -> !Rs2Inventory.isFull(), 5000);
        Rs2Bank.closeBank();
    }

    private void handlePickaxeUpgradeAtBank() {
        equipBestAvailablePickaxeForCurrentAttackLevel();

        String bestPickaxe = getBestMiningPickaxeName();
        if (bestPickaxe == null) return;

        if (!Rs2Equipment.isWearing(bestPickaxe)) {
            if (Rs2Inventory.hasItem(bestPickaxe)) {
                Rs2Inventory.interact(bestPickaxe, "Wield");
            } else if (Rs2Bank.hasItem(bestPickaxe)) {
                Rs2Bank.withdrawAndEquip(bestPickaxe);
            }
        }

        depositOutdatedPickaxes(bestPickaxe);
    }

    private String getBestMiningPickaxeName() {
        int miningLevel = Microbot.getClient().getRealSkillLevel(Skill.MINING);

        PickaxeMLevel bestPickaxe = java.util.Arrays.stream(PickaxeMLevel.values())
                .filter(p -> p.getLevel() <= miningLevel)
                .max(Comparator.comparingInt(PickaxeMLevel::getLevel))
                .orElse(null);

        if (bestPickaxe == null) return null;
        return formatPickaxeName(bestPickaxe.name());
    }

    private void depositOutdatedPickaxes(String bestPickaxe) {
        for (PickaxeMLevel pickaxe : PickaxeMLevel.values()) {
            String pickaxeName = formatPickaxeName(pickaxe.name());
            if (pickaxeName.equalsIgnoreCase(bestPickaxe)) continue;
            if (Rs2Inventory.hasItem(pickaxeName)) {
                Rs2Bank.depositAll(pickaxeName);
            }
        }
    }

    private boolean hasOutdatedPickaxeInInventory(String bestPickaxe) {
        for (PickaxeMLevel pickaxe : PickaxeMLevel.values()) {
            String pickaxeName = formatPickaxeName(pickaxe.name());
            if (!pickaxeName.equalsIgnoreCase(bestPickaxe) && Rs2Inventory.hasItem(pickaxeName)) {
                return true;
            }
        }
        return false;
    }

    private void equipBestAvailablePickaxeForCurrentAttackLevel() {
        int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);

        PickaxeELevel bestPickaxe = java.util.Arrays.stream(PickaxeELevel.values())
                .filter(p -> p.getLevel() <= attackLevel)
                .max(Comparator.comparingInt(PickaxeELevel::getLevel))
                .orElse(null);

        if (bestPickaxe == null) return;

        String pickaxeName = formatPickaxeName(bestPickaxe.name());
        if (Rs2Equipment.isWearing(pickaxeName)) return;

        if (!Rs2Bank.isOpen() && !Rs2Inventory.hasItem(pickaxeName)) {
            BankLocation nearestBank = Rs2Bank.getNearestBank();
            if (nearestBank == null) return;
            Rs2Bank.walkToBankAndUseBank(nearestBank);
            return;
        }

        if (Rs2Bank.isOpen() && !Rs2Inventory.hasItem(pickaxeName)) {
            Rs2Bank.withdrawAndEquip(pickaxeName);
            return;
        }

        if (Rs2Inventory.hasItem(pickaxeName)) {
            Rs2Inventory.interact(pickaxeName, "Wield");
        }
    }

    private void mineEqualCopperAndTin() {
        int copperCount = Rs2Inventory.count(COPPER_ORE);
        int tinCount = Rs2Inventory.count(TIN_ORE);
        String targetRock = copperCount <= tinCount ? "Copper" : "Tin";

        if (!Rs2GameObject.interact(targetRock, "Mine")) {
            Rs2GameObject.interact("Rocks", "Mine");
        }

        sleepUntil(() -> Rs2Player.isAnimating() || Rs2Inventory.count(COPPER_ORE) + Rs2Inventory.count(TIN_ORE) > copperCount + tinCount, 5000);
    }

    private boolean isInsideArea(Areas.Area area) {
        WorldPoint point = Rs2Player.getWorldLocation();
        int minX = Math.min(area.getX1(), area.getX2());
        int maxX = Math.max(area.getX1(), area.getX2());
        int minY = Math.min(area.getY1(), area.getY2());
        int maxY = Math.max(area.getY1(), area.getY2());

        return point.getX() >= minX && point.getX() <= maxX && point.getY() >= minY && point.getY() <= maxY;
    }

    private void walkToAreaCenter(Areas.Area area) {
        int x = (area.getX1() + area.getX2()) / 2;
        int y = (area.getY1() + area.getY2()) / 2;
        Rs2Walker.walkTo(new WorldPoint(x, y, 0));
    }

    private String formatPickaxeName(String enumName) {
        String normalized = enumName.equals("MITRHIL") ? "MITHRIL" : enumName;
        return normalized.substring(0, 1) + normalized.substring(1).toLowerCase() + " pickaxe";
    }

    private static final class MiningTarget {
        private final Areas.Area area;
        private final String rockName;
        private final boolean balancedCopperTin;

        private MiningTarget(Areas.Area area, String rockName, boolean balancedCopperTin) {
            this.area = area;
            this.rockName = rockName;
            this.balancedCopperTin = balancedCopperTin;
        }
    }
}