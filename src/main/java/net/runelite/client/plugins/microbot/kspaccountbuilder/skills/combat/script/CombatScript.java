package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.script;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.areas.MobArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.loot.Loot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.needed.Gear;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
@Getter
public class CombatScript {
    private static final int BUY_WAIT_TIMEOUT_MS = 20_000;
    private static final int LOOT_RADIUS = 8;
    private static final int GE_TROUT_RESTOCK_AMOUNT = 500;

    private static final String[] SELLABLE_BANK_ITEMS_KEEP_ONE = {
            "Goblin mail",
            "Chef's hat",
            "Air talisman",
            "Earth talisman",
            "Cowhide",
            "Steel longsword",
            "Limpwurt root",
            "Body talisman"
    };

    private boolean boneBuryEnabled = true;
    private boolean coinLootingEnabled = true;
    private String status = "Idle";

    public void configureLooting(boolean enableBoneBury, boolean enableCoinLooting) {
        this.boneBuryEnabled = enableBoneBury;
        this.coinLootingEnabled = enableCoinLooting;
    }

    public void execute() {
        try {
            setBalancedMeleeStyle();

            if (!ensureCombatLoadout()) {
                return;
            }

            CombatTrainingTarget target = resolveTrainingTarget();
            if (target == CombatTrainingTarget.COMBAT_COMPLETE) {
                status = target.getStatusText();
                return;
            }

            if (!isPlayerInTargetArea(target.getArea())) {
                status = "Walking to " + target.getDisplayName();
                Rs2Walker.walkTo(getAreaCenter(target.getArea()));
                return;
            }

            if (Rs2Player.isInteracting() || Rs2Player.isAnimating() || Rs2Player.isMoving()) {
                status = "Fighting in " + target.getDisplayName();
                return;
            }

            if (lootDropsInArea()) {
                status = "Looting drops";
                return;
            }

            if (attackTrainingNpc(target)) {
                status = "Attacking " + target.getDisplayName();
                return;
            }

            status = "Waiting for targets in " + target.getDisplayName();
        } catch (Exception ex) {
            log.error("CombatScript execution failure", ex);
            status = "Combat action failed, retrying";
        }
    }

    public boolean hasCombatSetupReady() {
        String weapon = getBestWeaponForAttack();
        boolean hasWeapon = hasItemInInventoryOrEquipped(weapon);
        boolean hasArmour = getBestArmourForDefence().stream().allMatch(this::hasItemInInventoryOrEquipped);
        boolean hasFood = countInventoryItem("Trout") >= Gear.MIN_TROUT_REQUIRED;
        return hasWeapon && hasArmour && hasFood;
    }

    private boolean ensureCombatLoadout() {
        if (hasCombatSetupReady()) {
            return true;
        }

        // If we already carry needed items in inventory, don't force banking.
        if (hasInventoryLoadoutWithoutFood() && countInventoryItem("Trout") >= Gear.MIN_TROUT_REQUIRED) {
            return true;
        }

        if (!refreshBankCache()) {
            status = "Unable to read bank";
            return false;
        }

        if (!sellConfiguredBankOverflow()) {
            return false;
        }

        if (countBankItem("Trout") < Gear.MIN_TROUT_REQUIRED) {
            if (!buyFromGrandExchange("Trout", GE_TROUT_RESTOCK_AMOUNT)) {
                status = "Failed buying trout";
                return false;
            }
        }

        List<String> upgradeItems = getRequiredUpgradeItems();
        for (String item : upgradeItems) {
            if (!hasItemAnywhere(item)) {
                if (!buyFromGrandExchange(item, 1)) {
                    log.warn("Failed buying upgrade: {}", item);
                }
            }
        }

        if (!withdrawCombatSetup()) {
            status = "Could not withdraw combat setup";
            return false;
        }

        return hasCombatSetupReady();
    }

    private boolean lootDropsInArea() {
        if (!isPlayerInTargetArea(resolveTrainingTarget().getArea())) {
            return false;
        }

        if (boneBuryEnabled && buryBonesInInventory()) {
            return true;
        }

        if (Rs2Inventory.isFull()) {
            return false;
        }

        if (boneBuryEnabled && lootByName("bones")) {
            return true;
        }

        if (coinLootingEnabled && Rs2GroundItem.lootCoins(new LootingParameters(LOOT_RADIUS, 1, 1, 0, false, true, "coins"))) {
            sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isInteracting(), 2_500);
            return true;
        }

        for (int lootId : Loot.DEFAULT_LOOT) {
            String name = Microbot.getItemManager().getItemComposition(lootId).getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (lootByName(name)) {
                return true;
            }
        }

        return false;
    }

    private boolean buryBonesInInventory() {
        List<Rs2ItemModel> bones = Rs2Inventory.getBones();
        if (bones == null || bones.isEmpty()) {
            return false;
        }
        if (Rs2Inventory.interact(bones.get(0), "bury")) {
            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Inventory.getBones().size() < bones.size(), 2_000);
            return true;
        }
        return false;
    }

    private boolean lootByName(String itemName) {
        LootingParameters params = new LootingParameters(LOOT_RADIUS, 1, 1, 0, false, true, itemName);
        if (Rs2GroundItem.lootItemsBasedOnNames(params)) {
            sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isInteracting(), 2_500);
            return true;
        }
        return false;
    }

    private boolean attackTrainingNpc(CombatTrainingTarget target) {
        Rs2NpcModel npc = Rs2Npc.getNpcs(n -> n != null
                        && n.getName() != null
                        && matchesAnyTarget(n.getName(), target.getNpcs())
                        && !n.isDead()
                        && n.getWorldLocation() != null
                        && target.getArea().contains(n.getWorldLocation()))
                .min(Comparator.comparingInt(Rs2NpcModel::getDistanceFromPlayer))
                .orElse(null);

        if (npc == null) {
            return false;
        }

        if (!Rs2Npc.interact(npc, "Attack")) {
            return false;
        }

        sleepUntil(() -> Rs2Player.isInteracting() || Rs2Player.isAnimating(), 3_000);
        return true;
    }

    private boolean withdrawCombatSetup() {
        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }

        Rs2Bank.depositAll();
        Rs2Bank.depositEquipment();

        withdrawAndEquip(getBestWeaponForAttack());
        for (String piece : getBestArmourForDefence()) {
            withdrawAndEquip(piece);
        }

        Rs2Bank.withdrawX("Trout", Gear.MIN_TROUT_REQUIRED, true);

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
        return true;
    }

    private void withdrawAndEquip(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return;
        }
        if (hasItemInInventoryOrEquipped(itemName)) {
            return;
        }
        if (!Rs2Bank.hasItem(itemName)) {
            return;
        }
        Rs2Bank.withdrawAndEquip(itemName);
    }

    private boolean sellConfiguredBankOverflow() {
        if (!Rs2GrandExchange.walkToGrandExchange() || !Rs2GrandExchange.openExchange()) {
            status = "Walking to GE";
            return false;
        }

        sleepUntil(Rs2GrandExchange::isOpen, 7_000);
        if (!Rs2GrandExchange.isOpen()) {
            return false;
        }

        for (String itemName : SELLABLE_BANK_ITEMS_KEEP_ONE) {
            int amountInBank = countBankItem(itemName);
            int toSell = Math.max(0, amountInBank - 1);
            if (toSell <= 0) {
                continue;
            }

            if (!ensureExchangeSlotAvailable()) {
                continue;
            }

            GrandExchangeRequest request = GrandExchangeRequest.builder()
                    .action(GrandExchangeAction.SELL)
                    .itemName(itemName)
                    .quantity(toSell)
                    .percent(-5)
                    .closeAfterCompletion(false)
                    .build();

            Rs2GrandExchange.processOffer(request);
            sleepUntil(() -> Rs2GrandExchange.hasSoldOffer() || Rs2GrandExchange.hasBoughtOffer(), BUY_WAIT_TIMEOUT_MS);
            Rs2GrandExchange.collectAllToBank();
        }

        Rs2GrandExchange.closeExchange();
        return true;
    }

    private boolean buyFromGrandExchange(String itemName, int quantity) {
        if (!Rs2GrandExchange.walkToGrandExchange() || !Rs2GrandExchange.openExchange()) {
            return false;
        }

        sleepUntil(Rs2GrandExchange::isOpen, 7_000);
        if (!Rs2GrandExchange.isOpen()) {
            return false;
        }

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

        boolean offered = Rs2GrandExchange.processOffer(request);
        if (!offered) {
            return false;
        }

        sleepUntil(() -> hasItemAnywhere(itemName) || Rs2GrandExchange.hasBoughtOffer(), BUY_WAIT_TIMEOUT_MS);
        Rs2GrandExchange.collectAllToBank();
        Rs2GrandExchange.closeExchange();
        return hasItemAnywhere(itemName);
    }

    private boolean refreshBankCache() {
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

    private void setBalancedMeleeStyle() {
        int attack = getLevel(Skill.ATTACK);
        int strength = getLevel(Skill.STRENGTH);
        int defence = getLevel(Skill.DEFENCE);

        Skill targetSkill = Skill.ATTACK;
        if (strength < attack && strength <= defence) {
            targetSkill = Skill.STRENGTH;
        } else if (defence < attack && defence <= strength) {
            targetSkill = Skill.DEFENCE;
        }

        int currentStyle = Microbot.getVarbitPlayerValue(VarPlayer.ATTACK_STYLE);
        WidgetInfo desired = getStyleWidgetForSkill(targetSkill);
        int desiredStyle = styleIndexForSkill(targetSkill);

        if (currentStyle == desiredStyle) {
            return;
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT) {
            Rs2Tab.switchToCombatOptionsTab();
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT, 2_000);
        }

        Rs2Combat.setAttackStyle(desired);
    }

    private WidgetInfo getStyleWidgetForSkill(Skill skill) {
        if (skill == Skill.STRENGTH) {
            return WidgetInfo.COMBAT_STYLE_TWO;
        }
        if (skill == Skill.DEFENCE) {
            return WidgetInfo.COMBAT_STYLE_FOUR;
        }
        return WidgetInfo.COMBAT_STYLE_ONE;
    }

    private int styleIndexForSkill(Skill skill) {
        if (skill == Skill.STRENGTH) {
            return 1;
        }
        if (skill == Skill.DEFENCE) {
            return 3;
        }
        return 0;
    }

    private CombatTrainingTarget resolveTrainingTarget() {
        int attack = getLevel(Skill.ATTACK);
        int strength = getLevel(Skill.STRENGTH);
        int defence = getLevel(Skill.DEFENCE);

        if (attack < 5 && strength < 5 && defence < 5) {
            return CombatTrainingTarget.GOBLINS;
        }

        if (attack >= 5 && strength >= 5 && defence >= 5 && attack < 10 && strength < 10 && defence < 10) {
            return CombatTrainingTarget.COWS;
        }

        if (attack >= 20 && strength >= 20 && defence >= 20) {
            return CombatTrainingTarget.AL_KHARID_GUARDS;
        }

        return CombatTrainingTarget.COWS;
    }

    private List<String> getRequiredUpgradeItems() {
        List<String> items = new ArrayList<>();
        items.add(getBestWeaponForAttack());
        items.addAll(getBestArmourForDefence());
        return items;
    }

    private String getBestWeaponForAttack() {
        int attack = getLevel(Skill.ATTACK);
        if (attack >= 20) return "Mithril scimitar";
        if (attack >= 10) return "Black scimitar";
        if (attack >= 5) return "Steel scimitar";
        return "Iron scimitar";
    }

    private List<String> getBestArmourForDefence() {
        int defence = getLevel(Skill.DEFENCE);
        if (defence >= 20) {
            return List.of("Mithril full helm", "Mithril platebody", "Mithril platelegs", "Mithril kiteshield");
        }
        if (defence >= 10) {
            return List.of("Black full helm", "Black platebody", "Black platelegs", "Black kiteshield");
        }
        return List.of("Iron full helm", "Iron platebody", "Iron platelegs", "Iron kiteshield");
    }

    private boolean isPlayerInTargetArea(WorldArea area) {
        WorldPoint location = Rs2Player.getWorldLocation();
        return area != null && location != null && area.contains(location);
    }

    private WorldPoint getAreaCenter(WorldArea area) {
        return new WorldPoint(area.getX() + (area.getWidth() / 2), area.getY() + (area.getHeight() / 2), area.getPlane());
    }

    private boolean matchesAnyTarget(String npcName, String[] targetNames) {
        String normalized = npcName.trim().toLowerCase();
        for (String target : targetNames) {
            String needle = target.trim().toLowerCase();
            if (normalized.equals(needle) || normalized.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInventoryLoadoutWithoutFood() {
        String weapon = getBestWeaponForAttack();
        boolean hasWeapon = hasItemInInventoryOrEquipped(weapon);
        boolean hasArmour = getBestArmourForDefence().stream().allMatch(this::hasItemInInventoryOrEquipped);
        return hasWeapon && hasArmour;
    }

    private boolean hasItemInInventoryOrEquipped(String item) {
        return Rs2Inventory.hasItem(item, true) || Rs2Equipment.isWearing(item);
    }

    private boolean hasItemAnywhere(String item) {
        return hasItemInInventoryOrEquipped(item) || countBankItem(item) > 0;
    }

    private int countInventoryItem(String itemName) {
        return (int) Rs2Inventory.items()
                .filter(i -> i != null && i.getName() != null && i.getName().equalsIgnoreCase(itemName))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

    private int countBankItem(String itemName) {
        return Rs2Bank.bankItems().stream()
                .filter(i -> i.getName() != null && i.getName().equalsIgnoreCase(itemName))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

    private int getLevel(Skill skill) {
        if (Microbot.getClient() == null) {
            return 1;
        }
        return Microbot.getClient().getRealSkillLevel(skill);
    }

    @Getter
    public enum CombatTrainingTarget {
        GOBLINS("goblins", MobArea.GOBLINS, new String[]{"Goblin"}),
        COWS("cows", MobArea.COWS, new String[]{"Cow", "Cow calf"}),
        AL_KHARID_GUARDS("Al-Kharid", MobArea.AL_KHARID_GUARDS, new String[]{"Al-Kharid warrior", "Al-Kharid guard"}),
        COMBAT_COMPLETE("combat complete", null, new String[0]);

        private final String displayName;
        private final WorldArea area;
        private final String[] npcs;

        CombatTrainingTarget(String displayName, WorldArea area, String[] npcs) {
            this.displayName = displayName;
            this.area = area;
            this.npcs = npcs;
        }

        public String getStatusText() {
            return "Training in " + displayName;
        }
    }
}
