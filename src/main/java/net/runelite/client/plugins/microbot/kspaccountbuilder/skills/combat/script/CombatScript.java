package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.script;

import lombok.Getter;
import net.runelite.api.Actor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.areas.MobArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.loot.Loot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.needed.Gear;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.ge.buy.Buy;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.ge.sell.Sell;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
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
    private static final long REPOSITION_COOLDOWN_MS = 1_500L;
    private static final int BURY_WAIT_TIMEOUT_MS = 700;
    private static final int LOOT_ACTION_WAIT_TIMEOUT_MS = 1_000;

    private static final long LOOT_WAIT_AFTER_KILL_MS = 1_200L;

    private String status = "Idle";
    private long lastRepositionAttemptAt = 0L;
    private int cachedTargetNpcIndex = -1;
    private long waitForLootUntilMs = 0L;


    private static final long LOOT_WAIT_AFTER_KILL_MS = 1_200L;

    private String status = "Idle";
    private long lastRepositionAttemptAt = 0L;
    private int cachedTargetNpcIndex = -1;
    private long waitForLootUntilMs = 0L;


    private String status = "Idle";
    private long lastRepositionAttemptAt = 0L;



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
                clearLootWaitState();
                status = "Walking to " + target.getDisplayName();
                Rs2Walker.walkTo(getAreaCenter(target.getArea()));
                return;
            }


            updateCombatTargetTracking();


            updateCombatTargetTracking();


            if (Rs2Player.isInteracting() || Rs2Player.isAnimating() || Rs2Player.isInCombat() || Rs2Combat.inCombat()) {
                status = "Fighting in " + target.getDisplayName();
                return;
            }


            if (shouldWaitForLoot(target)) {
                if (lootDropsInArea(target)) {
                    status = "Looting drops";
                } else {
                    status = "Waiting for loot";
                }
                return;
            }


            if (Rs2Player.isMoving()) {
                status = "Moving in " + target.getDisplayName();
                return;
            }

            if (lootDropsInArea(target)) {
                status = "Looting drops";
                return;
            }

            if (attackTrainingNpc(target)) {
                status = "Attacking " + target.getDisplayName();
                return;
            }

            if (moveTowardsTargetNpc(target)) {
                status = "Moving to target in " + target.getDisplayName();
                return;
            }

            if (repositionWithinTrainingArea(target.getArea())) {
                status = "Repositioning in " + target.getDisplayName();
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
        boolean hasAmulet = hasItemInInventoryOrEquipped("Amulet of power");
        boolean hasTeamCape = hasAnyTeamCapeInInventoryOrEquipped();
        boolean hasFood = countInventoryItem("Trout") >= Gear.MIN_TROUT_REQUIRED;
        return hasWeapon && hasArmour && hasAmulet && hasTeamCape && hasFood;
    }

    private boolean ensureCombatLoadout() {
        if (hasCombatSetupReady()) {

            clearLootWaitState();

            waitForLootUntilMs = 0L;

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

        List<String> purchases = getMissingPurchaseItems();
        int firemakingLevel = getLevel(Skill.FIREMAKING);

        if (Sell.hasSellableOverflowInBank(firemakingLevel)
                && !Sell.sellConfiguredBankOverflow(firemakingLevel)) {
            return false;
        }

        for (String item : purchases) {
            int quantity = "Trout".equalsIgnoreCase(item) ? GE_TROUT_RESTOCK_AMOUNT : 1;
            if (!Buy.buyItemToBank(item, quantity)) {
                log.warn("Failed buying item: {}", item);
            }
        }

        if (!withdrawCombatSetup()) {
            status = "Could not withdraw combat setup";
            return false;
        }

        return hasCombatSetupReady();
    }

    private List<String> getMissingPurchaseItems() {
        List<String> missing = new ArrayList<>();

        if (countBankItem("Trout") < Gear.MIN_TROUT_REQUIRED && !Rs2Inventory.hasItem("Trout", true)) {
            missing.add("Trout");
        }

        for (String item : getRequiredUpgradeItems()) {
            boolean owned = "Team-1 cape".equalsIgnoreCase(item)
                    ? hasAnyTeamCapeAnywhere()
                    : hasItemAnywhere(item);
            if (!owned) {
                missing.add(item);
            }
        }

        return missing;
    }


    private void clearLootWaitState() {
        cachedTargetNpcIndex = -1;
        waitForLootUntilMs = 0L;
    }


    private void updateCombatTargetTracking() {
        Actor interacting = Rs2Player.getInteracting();
        if (interacting instanceof Rs2NpcModel) {
            Rs2NpcModel npc = (Rs2NpcModel) interacting;
            if (!npc.isDead() && npc.getHealthRatio() > 0) {
                cachedTargetNpcIndex = npc.getIndex();
            }
        }

        if (cachedTargetNpcIndex == -1) {
            return;
        }

        Rs2NpcModel cachedNpc = Rs2Npc.getNpcByIndex(cachedTargetNpcIndex);
        if (cachedNpc == null || cachedNpc.isDead() || (cachedNpc.getHealthRatio() == 0 && cachedNpc.getHealthScale() > 0)) {
            waitForLootUntilMs = System.currentTimeMillis() + LOOT_WAIT_AFTER_KILL_MS;
            cachedTargetNpcIndex = -1;
        }
    }

    private boolean shouldWaitForLoot(CombatTrainingTarget target) {
        if (waitForLootUntilMs <= 0L || System.currentTimeMillis() >= waitForLootUntilMs) {

            clearLootWaitState();

            waitForLootUntilMs = 0L;

            return false;
        }

        if (!isPlayerInTargetArea(target.getArea())) {
            return false;
        }

        if (!hasAnyGroundItemsNearby() && (Rs2Inventory.getBones() == null || Rs2Inventory.getBones().isEmpty())) {

            clearLootWaitState();

            waitForLootUntilMs = 0L;

            return false;
        }

        return true;
    }

    private boolean lootDropsInArea(CombatTrainingTarget target) {

        if (!isPlayerInTargetArea(target.getArea())) {




        if (!isPlayerInTargetArea(target.getArea()) || Rs2Player.isInCombat() || Rs2Combat.inCombat()) {

            return false;
        }

        if (!hasAnyGroundItemsNearby()) {
            return buryBonesInInventory();
        }




        if (!isPlayerInTargetArea(target.getArea())) {
            return false;
        }


        if (!hasAnyGroundItemsNearby()) {
            return buryBonesInInventory();
        }



        if (buryBonesInInventory()) {
            return true;
        }

        if (Rs2Inventory.isFull()) {
            return false;
        }

        if (lootByName("bones")) {
            return true;
        }

        if (Loot.lootCoins(LOOT_RADIUS)) {

            sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isInteracting(), LOOT_ACTION_WAIT_TIMEOUT_MS);
            clearLootWaitState();


            sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isInteracting(), LOOT_ACTION_WAIT_TIMEOUT_MS);
            waitForLootUntilMs = 0L;

            sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isInteracting(), LOOT_ACTION_WAIT_TIMEOUT_MS);


            sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isInteracting(), LOOT_ACTION_WAIT_TIMEOUT_MS);

            sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isInteracting(), 1_000);



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




    private boolean hasAnyGroundItemsNearby() {
        return Rs2GroundItem.getAll(LOOT_RADIUS).length > 0;
    }


    private boolean buryBonesInInventory() {

        List<Rs2ItemModel> bones = Rs2Inventory.getBones();
        if (bones == null || bones.isEmpty()) {
            return false;
        }
        if (Rs2Inventory.interact(bones.get(0), "bury")) {
            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Inventory.getBones().size() < bones.size(), BURY_WAIT_TIMEOUT_MS);

            clearLootWaitState();

            return true;
        }
        return false;
    }

    private boolean lootByName(String itemName) {
        LootingParameters params = new LootingParameters(LOOT_RADIUS, 1, 1, 0, false, true, itemName);
        if (Rs2GroundItem.lootItemsBasedOnNames(params)) {
            sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isInteracting(), LOOT_ACTION_WAIT_TIMEOUT_MS);

            clearLootWaitState();


            waitForLootUntilMs = 0L;

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
                        && target.getArea().contains(n.getWorldLocation())
                        && isNpcAvailableForAttack(n))

                .min(Comparator.comparingInt((Rs2NpcModel n) -> n.getInteracting() == Microbot.getClient().getLocalPlayer() ? 0 : 1)
                        .thenComparingInt(Rs2NpcModel::getDistanceFromPlayer))


                .min(Comparator.comparingInt((Rs2NpcModel n) -> n.getInteracting() == Microbot.getClient().getLocalPlayer() ? 0 : 1)
                        .thenComparingInt(Rs2NpcModel::getDistanceFromPlayer))

                .min(Comparator.comparingInt(Rs2NpcModel::getDistanceFromPlayer))


                .orElse(null);

        if (npc == null) {
            return false;
        }

        if (!Rs2Npc.interact(npc, "Attack")) {
            return false;
        }

        cachedTargetNpcIndex = npc.getIndex();
        waitForLootUntilMs = 0L;
        sleepUntil(() -> Rs2Player.isInteracting() || Rs2Player.isAnimating() || Rs2Player.isMoving(), 1_500);
        return Rs2Player.isInteracting() || Rs2Player.isAnimating() || Rs2Player.isMoving();
    }

    private boolean moveTowardsTargetNpc(CombatTrainingTarget target) {
        if (Rs2Player.isMoving()) {
            return false;
        }

        Rs2NpcModel nearest = Rs2Npc.getNpcs(n -> n != null
                        && n.getName() != null
                        && matchesAnyTarget(n.getName(), target.getNpcs())
                        && !n.isDead()
                        && n.getWorldLocation() != null
                        && target.getArea().contains(n.getWorldLocation())
                        && isNpcAvailableForAttack(n))

                .min(Comparator.comparingInt((Rs2NpcModel n) -> n.getInteracting() == Microbot.getClient().getLocalPlayer() ? 0 : 1)
                        .thenComparingInt(Rs2NpcModel::getDistanceFromPlayer))


                .min(Comparator.comparingInt((Rs2NpcModel n) -> n.getInteracting() == Microbot.getClient().getLocalPlayer() ? 0 : 1)
                        .thenComparingInt(Rs2NpcModel::getDistanceFromPlayer))

                .min(Comparator.comparingInt(Rs2NpcModel::getDistanceFromPlayer))

                .orElse(null);

        if (nearest == null || nearest.getWorldLocation() == null) {
            return false;
        }

        if (nearest.getDistanceFromPlayer() <= 2) {
            return false;
        }

        Rs2Walker.walkTo(nearest.getWorldLocation());
        sleepUntil(Rs2Player::isMoving, 2_000);
        return Rs2Player.isMoving();
    }

    private boolean isNpcAvailableForAttack(Rs2NpcModel npc) {
        Actor interacting = npc.getInteracting();
        return interacting == null || interacting == Microbot.getClient().getLocalPlayer();
    }

    private boolean repositionWithinTrainingArea(WorldArea area) {
        if (area == null || Rs2Player.isMoving()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastRepositionAttemptAt < REPOSITION_COOLDOWN_MS) {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint center = getAreaCenter(area);
        if (playerLocation == null || center == null || !area.contains(playerLocation)) {
            return false;
        }

        lastRepositionAttemptAt = now;
        Rs2Walker.walkTo(center);
        sleepUntil(Rs2Player::isMoving, 2_000);
        return Rs2Player.isMoving();
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
        withdrawAndEquip("Amulet of power");
        withdrawAnyTeamCape();

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


    private boolean hasAnyTeamCapeAnywhere() {
        if (hasAnyTeamCapeInInventoryOrEquipped()) {
            return true;
        }

        return Rs2Bank.bankItems().stream()
                .anyMatch(item -> item.getName() != null && isTeamCapeName(item.getName()));
    }

    private boolean hasAnyTeamCapeInInventoryOrEquipped() {
        for (String matcher : Gear.TEAM_CAPE_NAME_MATCHERS) {
            if (Rs2Equipment.isWearing(matcher) || Rs2Inventory.hasItem(matcher, true)) {
                return true;
            }
        }
        return false;
    }

    private void withdrawAnyTeamCape() {
        if (hasAnyTeamCapeInInventoryOrEquipped()) {
            return;
        }

        for (String matcher : Gear.TEAM_CAPE_NAME_MATCHERS) {
            if (Rs2Bank.hasItem(matcher)) {
                Rs2Bank.withdrawAndEquip(matcher);
                sleepUntil(this::hasAnyTeamCapeInInventoryOrEquipped, 2_000);
                return;
            }
        }

        if (Rs2Bank.hasItem("Team-1 cape")) {
            Rs2Bank.withdrawAndEquip("Team-1 cape");
            sleepUntil(this::hasAnyTeamCapeInInventoryOrEquipped, 2_000);
        }
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
        int lowestMelee = Math.min(getLevel(Skill.ATTACK), Math.min(getLevel(Skill.STRENGTH), getLevel(Skill.DEFENCE)));

        if (lowestMelee < 5) {
            return CombatTrainingTarget.GOBLINS;
        }

        if (lowestMelee < 10) {
            return CombatTrainingTarget.COWS;
        }

        if (lowestMelee >= 20) {
            return CombatTrainingTarget.AL_KHARID_GUARDS;
        }

        return CombatTrainingTarget.COWS;
    }

    private List<String> getRequiredUpgradeItems() {
        List<String> items = new ArrayList<>();
        items.add(getBestWeaponForAttack());
        items.addAll(getBestArmourForDefence());
        items.add("Amulet of power");
        items.add("Team-1 cape");
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

    private boolean isTeamCapeName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.contains("team-") || lower.contains("team cape");
    }

    private boolean hasInventoryLoadoutWithoutFood() {
        String weapon = getBestWeaponForAttack();
        boolean hasWeapon = hasItemInInventoryOrEquipped(weapon);
        boolean hasArmour = getBestArmourForDefence().stream().allMatch(this::hasItemInInventoryOrEquipped);
        boolean hasAmulet = hasItemInInventoryOrEquipped("Amulet of power");
        boolean hasTeamCape = hasAnyTeamCapeInInventoryOrEquipped();
        return hasWeapon && hasArmour && hasAmulet && hasTeamCape;
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
