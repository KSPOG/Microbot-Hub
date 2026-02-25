package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.script;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.areas.MobArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.needed.Gear;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Combat progression planner for KSP Account Builder.
 */
@Slf4j
@Getter
public class CombatScript {
    private static final int BUY_WAIT_TIMEOUT_MS = 20_000;
    private String status = "Idle";

    public CombatTrainingTarget resolveTrainingTarget() {
        int lowestCombatLevel = getLowestMeleeCombatLevel();

        if (lowestCombatLevel < 5) {
            return CombatTrainingTarget.GOBLINS;
        }

        if (lowestCombatLevel < 20) {
            return CombatTrainingTarget.COWS;
        }

        if (lowestCombatLevel < 30) {
            return CombatTrainingTarget.AL_KHARID_GUARDS;
        }

        return CombatTrainingTarget.COMBAT_COMPLETE;
    }

    public void execute() {
        try {
            if (!prepareCombatSuppliesAndUpgrades()) {
                return;
            }

            CombatTrainingTarget target = resolveTrainingTarget();
            if (target == CombatTrainingTarget.COMBAT_COMPLETE) {
                status = target.getStatusText();
                return;
            }

            WorldArea targetArea = target.getArea();
            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            if (targetArea != null && playerLocation != null && !targetArea.contains(playerLocation)) {
                status = "Walking to " + target.name().replace('_', ' ').toLowerCase();
                Rs2Walker.walkTo(getAreaCenter(targetArea));
                return;
            }

            status = target.getStatusText() + " | Focus " + getLowestMeleeSkillDisplay()
                    + " (A/S/D: " + getLevel(Skill.ATTACK) + "/" + getLevel(Skill.STRENGTH) + "/" + getLevel(Skill.DEFENCE) + ")";
        } catch (Exception ex) {
            log.error("CombatScript execution failure", ex);
            status = "Combat action failed, retrying";
        }
    }

    public boolean prepareCombatSuppliesAndUpgrades() {
        List<String> neededPurchases = new ArrayList<>();

        String bestWeapon = getBestWeaponForCurrentAttackLevel();
        if (!hasItemAnywhere(bestWeapon)) {
            neededPurchases.add(bestWeapon);
        }

        for (String armourPiece : getBestArmourForCurrentDefenceLevel()) {
            if (!hasItemAnywhere(armourPiece)) {
                neededPurchases.add(armourPiece);
            }
        }

        if (!hasItemAnywhere("Amulet of power")) {
            neededPurchases.add("Amulet of power");
        }

        if (!hasAnyTeamCape()) {
            neededPurchases.add("Team-1 cape");
        }

        int troutCount = countItemAnywhere("Trout");
        if (troutCount < Gear.MIN_TROUT_REQUIRED) {
            neededPurchases.add("Trout");
        }

        if (!neededPurchases.isEmpty()) {
            status = "Buying missing combat items";
            if (!buyFromGrandExchange(neededPurchases)) {
                return false;
            }

        }

        return withdrawNeededCombatItems();
    }


    private boolean withdrawNeededCombatItems() {
        status = "Withdrawing combat setup";

        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }


        }

        return withdrawNeededCombatItems();
    }


    private boolean withdrawNeededCombatItems() {
        status = "Withdrawing combat setup";

        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }


        Rs2Bank.depositAll();

        String bestWeapon = getBestWeaponForCurrentAttackLevel();
        withdrawAndEquipIfAvailable(bestWeapon);

        for (String armourPiece : getBestArmourForCurrentDefenceLevel()) {
            withdrawAndEquipIfAvailable(armourPiece);
        }


        withdrawAndEquipIfAvailable("Amulet of power");


        withdrawTeamCapeIfAvailable();
        Rs2Bank.withdrawX("Trout", Gear.MIN_TROUT_REQUIRED, true);

        Rs2Bank.closeBank();

        boolean hasWeapon = Rs2Equipment.isWearing(bestWeapon) || Rs2Inventory.hasItem(bestWeapon);
        boolean hasRequiredArmour = getBestArmourForCurrentDefenceLevel().stream()
                .allMatch(piece -> Rs2Equipment.isWearing(piece) || Rs2Inventory.hasItem(piece));

        boolean hasAmuletOfPower = Rs2Equipment.isWearing("Amulet of power") || Rs2Inventory.hasItem("Amulet of power");
        boolean hasFood = countInventoryItem("Trout") >= Gear.MIN_TROUT_REQUIRED;

        if (!hasWeapon || !hasRequiredArmour || !hasAmuletOfPower || !hasFood) {

        boolean hasFood = countInventoryItem("Trout") >= Gear.MIN_TROUT_REQUIRED;

        if (!hasWeapon || !hasRequiredArmour || !hasFood) {

            status = "Missing combat withdrawals";
            return false;
        }

        status = "Combat supplies ready";
        return true;
    }

    private void withdrawAndEquipIfAvailable(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return;
        }

        if (Rs2Equipment.isWearing(itemName) || Rs2Inventory.hasItem(itemName)) {
            return;
        }

        if (!Rs2Bank.hasItem(itemName)) {
            return;
        }

        Rs2Bank.withdrawAndEquip(itemName);
    }

    private void withdrawTeamCapeIfAvailable() {
        for (String matcher : Gear.TEAM_CAPE_NAME_MATCHERS) {
            if (Rs2Equipment.isWearing(matcher) || Rs2Inventory.hasItem(matcher)) {
                return;
            }

            if (Rs2Bank.hasItem(matcher)) {
                Rs2Bank.withdrawAndEquip(matcher);
                return;
            }
        }

        Rs2Bank.withdrawAndEquip("Team-1 cape");
    }

    public boolean shouldBuyMoreTrout(int troutInBank) {
        return troutInBank < Gear.MIN_TROUT_REQUIRED;
    }

    public boolean shouldLootCowhide(CombatTrainingTarget target) {
        return target == CombatTrainingTarget.COWS;
    }


    private WorldPoint getAreaCenter(WorldArea area) {
        int centerX = area.getX() + (area.getWidth() / 2);
        int centerY = area.getY() + (area.getHeight() / 2);
        return new WorldPoint(centerX, centerY, area.getPlane());
    }

    private int getLowestMeleeCombatLevel() {
        return Math.min(getLevel(Skill.ATTACK), Math.min(getLevel(Skill.STRENGTH), getLevel(Skill.DEFENCE)));
    }

    private String getLowestMeleeSkillDisplay() {
        int attack = getLevel(Skill.ATTACK);
        int strength = getLevel(Skill.STRENGTH);
        int defence = getLevel(Skill.DEFENCE);

        int minimum = Math.min(attack, Math.min(strength, defence));
        if (attack == minimum) {
            return "Attack";
        }

        if (strength == minimum) {
            return "Strength";
        }

        return "Defence";
    }

    private int getLevel(Skill skill) {
        if (Microbot.getClient() == null) {
            return 1;
        }

        return Microbot.getClient().getRealSkillLevel(skill);
    }

    private String getBestWeaponForCurrentAttackLevel() {
        int attackLevel = getLevel(Skill.ATTACK);

        if (attackLevel >= 20) return "Mithril scimitar";
        if (attackLevel >= 10) return "Black scimitar";
        if (attackLevel >= 5) return "Steel scimitar";
        return "Iron scimitar";
    }

    private List<String> getBestArmourForCurrentDefenceLevel() {
        int defenceLevel = getLevel(Skill.DEFENCE);

        if (defenceLevel >= 20) {
            return List.of("Mithril full helm", "Mithril platebody", "Mithril platelegs", "Mithril kiteshield");
        }

        if (defenceLevel >= 10) {
            return List.of("Black full helm", "Black platebody", "Black platelegs", "Black kiteshield");
        }

        return List.of("Iron full helm", "Iron platebody", "Iron platelegs", "Iron kiteshield");
    }

    private boolean hasAnyTeamCape() {
        for (String matcher : Gear.TEAM_CAPE_NAME_MATCHERS) {
            if (Rs2Equipment.isWearing(matcher)) {
                return true;
            }

            if (Rs2Inventory.items().anyMatch(item -> item != null
                    && item.getName() != null
                    && item.getName().toLowerCase().contains(matcher.toLowerCase()))) {
                return true;
            }
        }

        return Rs2Bank.bankItems().stream().anyMatch(item -> item.getName() != null && isTeamCapeName(item.getName()));
    }

    private boolean isTeamCapeName(String name) {
        String lower = name.toLowerCase();
        return lower.contains("team-") || lower.contains("team cape");
    }

    private boolean hasItemAnywhere(String name) {
        return Rs2Inventory.hasItem(name, true)
                || Rs2Equipment.isWearing(name)
                || countBankItem(name) > 0;
    }

    private int countInventoryItem(String itemName) {
        return (int) Rs2Inventory.items()
                .filter(item -> item != null && item.getName() != null && item.getName().equalsIgnoreCase(itemName))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

    private int countItemAnywhere(String name) {
        int inventoryCount = (int) Rs2Inventory.items()
                .filter(item -> item != null && item.getName() != null && item.getName().equalsIgnoreCase(name))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
        return inventoryCount + countBankItem(name);
    }

    private int countBankItem(String itemName) {
        return Rs2Bank.bankItems().stream()
                .filter(item -> item.getName() != null && item.getName().equalsIgnoreCase(itemName))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

    private boolean buyFromGrandExchange(List<String> purchases) {
        if (!refreshBankCache()) {
            return false;
        }

        if (!Rs2GrandExchange.walkToGrandExchange() || !Rs2GrandExchange.openExchange()) {
            return false;
        }

        sleepUntil(Rs2GrandExchange::isOpen, 7_000);
        if (!Rs2GrandExchange.isOpen()) {
            return false;
        }

        for (String itemName : purchases) {
            if (hasItemAnywhere(itemName)) {
                continue;
            }

            int quantity = "Trout".equalsIgnoreCase(itemName) ? Gear.MIN_TROUT_REQUIRED : 1;
            if (!buySingleItem(itemName, quantity)) {
                log.warn("CombatScript: failed buying {}", itemName);
            }
        }

        Rs2GrandExchange.collectAllToBank();
        Rs2GrandExchange.closeExchange();
        return true;
    }

    private boolean buySingleItem(String itemName, int quantity) {
        if (!ensureExchangeSlotAvailable()) {
            return false;
        }

        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.BUY)
                .itemName(itemName)
                .quantity(quantity)
                .percent(8)
                .closeAfterCompletion(false)
                .build();

        if (!Rs2GrandExchange.processOffer(request)) {
            return false;
        }

        sleepUntil(() -> Rs2GrandExchange.hasBoughtOffer() || hasItemAnywhere(itemName), BUY_WAIT_TIMEOUT_MS);
        Rs2GrandExchange.collectAllToBank();
        return hasItemAnywhere(itemName);
    }

    private boolean refreshBankCache() {
        if (Rs2Bank.isOpen()) {
            return true;
        }

        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }

        sleepUntil(() -> !Rs2Bank.bankItems().isEmpty(), 5_000);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
        return true;
    }

    private boolean ensureExchangeSlotAvailable() {
        if (Rs2GrandExchange.getAvailableSlotsCount() > 0) {
            return true;
        }

        Rs2GrandExchange.collectAllToBank();
        sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() > 0, 5_000);
        return Rs2GrandExchange.getAvailableSlotsCount() > 0;
    }

    @Getter
    public enum CombatTrainingTarget {
        GOBLINS(
                "Walk to goblins and fight goblins",
                MobArea.GOBLINS,
                new String[]{"Goblin"}
        ),
        COWS(
                "Walk to cows and train on cows + cow calves",
                MobArea.COWS,
                new String[]{"Cow", "Cow calf"}
        ),
        AL_KHARID_GUARDS(
                "Walk to Al Kharid guards and train",
                MobArea.AL_KHARID_GUARDS,
                new String[]{"Al-Kharid warrior", "Al-Kharid guard"}
        ),
        COMBAT_COMPLETE(
                "Attack/Strength/Defence reached level 30",
                null,
                new String[0]
        );

        private final String statusText;
        private final WorldArea area;
        private final String[] npcs;

        CombatTrainingTarget(String statusText, WorldArea area, String[] npcs) {
            this.statusText = statusText;
            this.area = area;
            this.npcs = npcs;
        }
    }
}
