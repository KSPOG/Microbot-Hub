package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.equiplevels.PickaxeEquip;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.levelreqmining.MiningReq;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.gearea.GEArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.equiplevels.AxeEquip;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.levelreqwc.WoodCuttingReq;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Singleton;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class BuyScript extends Script
{
    private static final int LOOP_DELAY_MS = 600;
    private static final int WEB_WALK_COOLDOWN_MS = 3_000;
    private static final int ACTION_COOLDOWN_MS = 1_200;
    private static final int BANK_WAIT_TIMEOUT_MS = 3_000;
    private static final int[] GRAND_EXCHANGE_SLOT_IDS = {
            465, 468, 471, 474, 477, 480, 483, 486
    };
    private static final String[] PICKAXE_NAMES = {
            "Bronze pickaxe", "Iron pickaxe", "Steel pickaxe", "Black pickaxe",
            "Mithril pickaxe", "Adamant pickaxe", "Rune pickaxe"
    };
    private static final String[] AXE_NAMES = {
            "Bronze axe", "Iron axe", "Steel axe", "Black axe",
            "Mithril axe", "Adamant axe", "Rune axe"
    };

    @Getter
    private GEArea targetArea = GEArea.GRAND_EXCHANGE;

    private boolean debugLogging;
    private long lastWebWalkAtMs;
    private long lastActionAtMs;
    private Plugin flippingCopilot;
    private Object suggestionManager;
    private Object highlightController;

    public void setDebugLogging(boolean debugLogging)
    {
        this.debugLogging = debugLogging;
    }

    public boolean run(GEArea area)
    {
        shutdown();
        targetArea = area;
        Microbot.status = "Walking to GE";

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn())
            {
                return;
            }

            if (!ensureInTargetArea())
            {
                return;
            }

            if (Rs2Player.isMoving() || Rs2Player.isInteracting())
            {
                Microbot.status = "Waiting at GE";
                return;
            }

            String desiredPickaxe = resolveDesiredPickaxeName();
            String desiredAxe = resolveDesiredAxeName();
            if (desiredPickaxe == null || desiredAxe == null)
            {
                return;
            }

            if (hasOutdatedToolEquipped(desiredPickaxe, desiredAxe))
            {
                if (!unequipOutdatedTools())
                {
                    return;
                }
            }

            if ((Rs2GrandExchange.hasSoldOffer() || Rs2GrandExchange.hasBoughtOffer()) && ensureGrandExchangeOpen())
            {
                Microbot.status = "Collecting GE Offers";
                Rs2GrandExchange.collectAllToBank();
                sleepUntil(() -> !Rs2GrandExchange.hasSoldOffer() && !Rs2GrandExchange.hasBoughtOffer(), 5_000);
                return;
            }

            if (isGrandExchangeOfferFlowActive())
            {
                Microbot.status = "Setting Offer Price";
                handleCopilotSellFlow();
                return;
            }

            Rs2ItemModel outdatedInventoryTool = getNextOutdatedInventoryTool(desiredPickaxe, desiredAxe);
            String missingTool = getNextMissingToolToBuy(desiredPickaxe, desiredAxe);

            if (outdatedInventoryTool == null && missingTool == null)
            {
                Microbot.status = "GE Buy Complete";
                return;
            }

            if (shouldPrepareExchangeInventory(outdatedInventoryTool, missingTool, desiredPickaxe, desiredAxe))
            {
                prepareExchangeInventory(desiredPickaxe, desiredAxe, missingTool != null);
                return;
            }

            if (!ensureGrandExchangeOpen())
            {
                return;
            }

            if (outdatedInventoryTool != null)
            {
                if (handleCopilotSellFlow())
                {
                    return;
                }

                if (isCopilotAvailable())
                {
                    Microbot.status = "Selling " + outdatedInventoryTool.getName();
                    if (Rs2Inventory.interact(outdatedInventoryTool.getName(), "Offer"))
                    {
                        lastActionAtMs = System.currentTimeMillis();
                        sleepUntil(() -> Rs2GrandExchange.isOfferScreenOpen() || isCopilotPromptVisible(), 3_000);
                    }
                    return;
                }

                placeFallbackSellOffer(outdatedInventoryTool);
                return;
            }

            placeFallbackBuyOffer(missingTool);
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean ensureInTargetArea()
    {
        if (targetArea.toWorldArea().contains(Rs2Player.getWorldLocation()))
        {
            return true;
        }

        if (Rs2Player.isMoving())
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if ((now - lastWebWalkAtMs) < WEB_WALK_COOLDOWN_MS)
        {
            return false;
        }

        lastWebWalkAtMs = now;
        Microbot.status = "Walking to GE";
        WorldPoint center = getAreaCenter();
        if (!Rs2Walker.walkFastCanvas(center))
        {
            Rs2Walker.walkTo(center, 2);
        }
        return false;
    }

    private boolean shouldPrepareExchangeInventory(Rs2ItemModel outdatedInventoryTool, String missingTool, String desiredPickaxe, String desiredAxe)
    {
        if (Rs2Bank.isOpen())
        {
            return true;
        }

        if (outdatedInventoryTool != null)
        {
            return false;
        }

        if (missingTool != null && Rs2Inventory.itemQuantity(ItemID.COINS_995) > 0)
        {
            return false;
        }

        if (hasOutdatedToolInBank(desiredPickaxe, desiredAxe))
        {
            return true;
        }

        return missingTool != null;
    }

    private void prepareExchangeInventory(String desiredPickaxe, String desiredAxe, boolean needCoinsForBuying)
    {
        if (!Rs2Bank.isOpen())
        {
            if (Rs2GrandExchange.isOpen())
            {
                Microbot.status = "Closing GE";
                Rs2GrandExchange.closeExchange();
                sleepUntil(() -> !Rs2GrandExchange.isOpen(), 2_000);
                return;
            }

            Microbot.status = "Opening Bank";
            if (Rs2Bank.openBank())
            {
                sleepUntil(Rs2Bank::isOpen, BANK_WAIT_TIMEOUT_MS);
            }
            else
            {
                Microbot.status = "Walking to Bank";
                Rs2Bank.walkToBankAndUseBank();
            }
            return;
        }

        Microbot.status = "Preparing GE Items";
        if (!Rs2Inventory.isEmpty())
        {
            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty, BANK_WAIT_TIMEOUT_MS);
        }

        if (!Rs2Bank.hasWithdrawAsNote())
        {
            Rs2Bank.setWithdrawAsNote();
            sleepUntil(Rs2Bank::hasWithdrawAsNote, 2_000);
        }

        withdrawOutdatedToolsAsNotes(desiredPickaxe, desiredAxe);

        if (needCoinsForBuying && Rs2Inventory.itemQuantity(ItemID.COINS_995) <= 0 && Rs2Bank.count("Coins") > 0)
        {
            Rs2Bank.withdrawAll("Coins");
            sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.COINS_995) > 0, BANK_WAIT_TIMEOUT_MS);
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
        Microbot.status = "Opening GE";
    }

    private void withdrawOutdatedToolsAsNotes(String desiredPickaxe, String desiredAxe)
    {
        for (String pickaxeName : PICKAXE_NAMES)
        {
            if (pickaxeName.equalsIgnoreCase(desiredPickaxe))
            {
                continue;
            }

            if (Rs2Bank.count(pickaxeName) > 0 && !Rs2Inventory.isFull())
            {
                Rs2Bank.withdrawAll(pickaxeName, true);
                sleepUntil(() -> Rs2Inventory.hasItem(pickaxeName, true), BANK_WAIT_TIMEOUT_MS);
            }
        }

        for (String axeName : AXE_NAMES)
        {
            if (axeName.equalsIgnoreCase(desiredAxe))
            {
                continue;
            }

            if (Rs2Bank.count(axeName) > 0 && !Rs2Inventory.isFull())
            {
                Rs2Bank.withdrawAll(axeName, true);
                sleepUntil(() -> Rs2Inventory.hasItem(axeName, true), BANK_WAIT_TIMEOUT_MS);
            }
        }
    }

    private boolean ensureGrandExchangeOpen()
    {
        if (Rs2GrandExchange.isOpen())
        {
            return true;
        }

        if (Rs2Bank.isOpen())
        {
            Microbot.status = "Closing Bank";
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
            return false;
        }

        Microbot.status = "Opening GE";
        if (Rs2GrandExchange.openExchange())
        {
            sleepUntil(Rs2GrandExchange::isOpen, 3_000);
            return Rs2GrandExchange.isOpen();
        }

        Rs2NpcModel clerk = Rs2Npc.getNpc("Grand Exchange Clerk");
        if (clerk != null && (Rs2Npc.interact(clerk, "Exchange") || Rs2Npc.interact(clerk, "Trade")))
        {
            sleepUntil(Rs2GrandExchange::isOpen, 3_000);
            return Rs2GrandExchange.isOpen();
        }

        return false;
    }

    private boolean placeFallbackSellOffer(Rs2ItemModel item)
    {
        if (item == null || System.currentTimeMillis() - lastActionAtMs < ACTION_COOLDOWN_MS)
        {
            return false;
        }

        Microbot.status = "Selling " + item.getName();

        int price = Rs2GrandExchange.getPrice(item.getId());
        if (price <= 0)
        {
            price = 1;
        }

        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.SELL)
                .itemName(item.getName())
                .quantity(item.getQuantity())
                .price(price)
                .closeAfterCompletion(false)
                .build();
        boolean offered = Rs2GrandExchange.processOffer(request);
        if (offered)
        {
            lastActionAtMs = System.currentTimeMillis();
            sleepUntil(() -> !Rs2Inventory.hasItem(item.getName(), true), 5_000);
        }
        return offered;
    }

    private boolean placeFallbackBuyOffer(String itemName)
    {
        if (itemName == null || System.currentTimeMillis() - lastActionAtMs < ACTION_COOLDOWN_MS)
        {
            return false;
        }

        Microbot.status = "Buying " + itemName;

        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.BUY)
                .itemName(itemName)
                .quantity(1)
                .percent(8)
                .closeAfterCompletion(false)
                .build();
        boolean offered = Rs2GrandExchange.processOffer(request);
        if (offered)
        {
            lastActionAtMs = System.currentTimeMillis();
            sleepUntil(() -> Rs2GrandExchange.hasBoughtOffer()
                    || hasToolAnywhere(itemName), 10_000);
        }
        return offered;
    }

    private Rs2ItemModel getNextOutdatedInventoryTool(String desiredPickaxe, String desiredAxe)
    {
        for (Rs2ItemModel item : Rs2Inventory.all())
        {
            if (item == null || item.getName() == null)
            {
                continue;
            }

            if (isOutdatedToolName(item.getName(), desiredPickaxe, desiredAxe))
            {
                return item;
            }
        }
        return null;
    }

    private boolean hasOutdatedToolInBank(String desiredPickaxe, String desiredAxe)
    {
        for (String pickaxeName : PICKAXE_NAMES)
        {
            if (isOutdatedToolName(pickaxeName, desiredPickaxe, desiredAxe) && Rs2Bank.count(pickaxeName) > 0)
            {
                return true;
            }
        }

        for (String axeName : AXE_NAMES)
        {
            if (isOutdatedToolName(axeName, desiredPickaxe, desiredAxe) && Rs2Bank.count(axeName) > 0)
            {
                return true;
            }
        }

        return false;
    }

    private boolean hasOutdatedToolEquipped(String desiredPickaxe, String desiredAxe)
    {
        for (String pickaxeName : PICKAXE_NAMES)
        {
            if (isOutdatedToolName(pickaxeName, desiredPickaxe, desiredAxe) && Rs2Equipment.isWearing(pickaxeName))
            {
                return true;
            }
        }

        for (String axeName : AXE_NAMES)
        {
            if (isOutdatedToolName(axeName, desiredPickaxe, desiredAxe) && Rs2Equipment.isWearing(axeName))
            {
                return true;
            }
        }

        return false;
    }

    private boolean unequipOutdatedTools()
    {
        if (Rs2Tab.getCurrentTab() != InterfaceTab.EQUIPMENT)
        {
            Rs2Tab.switchTo(InterfaceTab.EQUIPMENT);
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.EQUIPMENT, 1_500);
        }

        Rs2Equipment.unEquip(EquipmentInventorySlot.WEAPON);
        sleep(250);

        if (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY)
        {
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY, 1_500);
        }

        return !Rs2Equipment.isWearing("pickaxe", false)
                && !Rs2Equipment.isWearing("pickaxe")
                && !Rs2Equipment.isWearing("axe", false)
                && !Rs2Equipment.isWearing("axe");
    }

    private String getNextMissingToolToBuy(String desiredPickaxe, String desiredAxe)
    {
        if (!hasToolAnywhere(desiredPickaxe))
        {
            return desiredPickaxe;
        }

        if (!hasToolAnywhere(desiredAxe))
        {
            return desiredAxe;
        }

        return null;
    }

    private boolean hasToolAnywhere(String toolName)
    {
        return Rs2Equipment.isWearing(toolName)
                || Rs2Inventory.hasItem(toolName)
                || Rs2Inventory.hasItem(toolName, true)
                || Rs2Bank.count(toolName) > 0;
    }

    private boolean isOutdatedToolName(String itemName, String desiredPickaxe, String desiredAxe)
    {
        for (String pickaxeName : PICKAXE_NAMES)
        {
            if (pickaxeName.equalsIgnoreCase(itemName))
            {
                return !pickaxeName.equalsIgnoreCase(desiredPickaxe);
            }
        }

        for (String axeName : AXE_NAMES)
        {
            if (axeName.equalsIgnoreCase(itemName))
            {
                return !axeName.equalsIgnoreCase(desiredAxe);
            }
        }

        return false;
    }

    private String resolveDesiredPickaxeName()
    {
        MiningReq bestMiningReq = MiningReq.bestForMiningLevel(Microbot.getClient().getRealSkillLevel(Skill.MINING));
        PickaxeEquip bestAttackReq = PickaxeEquip.bestForAttackLevel(Microbot.getClient().getRealSkillLevel(Skill.ATTACK));
        return PICKAXE_NAMES[Math.min(bestMiningReq.ordinal(), bestAttackReq.ordinal())];
    }

    private String resolveDesiredAxeName()
    {
        WoodCuttingReq bestWoodcuttingReq = WoodCuttingReq.bestForWoodcuttingLevel(Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING));
        AxeEquip bestAttackReq = AxeEquip.bestForAttackLevel(Microbot.getClient().getRealSkillLevel(Skill.ATTACK));
        return AXE_NAMES[Math.min(bestWoodcuttingReq.ordinal(), bestAttackReq.ordinal())];
    }

    private boolean handleCopilotSellFlow()
    {
        if (!isCopilotAvailable())
        {
            return false;
        }

        if (checkAndPressCopilotKeybind())
        {
            return true;
        }

        if (checkAndAbortOrModifyIfNeeded())
        {
            return true;
        }

        return checkAndClickHighlightedWidgets();
    }

    private boolean isCopilotAvailable()
    {
        if (flippingCopilot != null && suggestionManager != null && highlightController != null)
        {
            return true;
        }

        Plugin plugin = getFlippingCopilot();
        if (plugin == null)
        {
            return false;
        }

        suggestionManager = getSuggestionManager(plugin);
        highlightController = getHighlightController(plugin);
        return suggestionManager != null && highlightController != null;
    }

    private Plugin getFlippingCopilot()
    {
        if (flippingCopilot != null)
        {
            return flippingCopilot;
        }

        flippingCopilot = Microbot.getPluginManager()
                .getPlugins()
                .stream()
                .filter(plugin -> plugin.getClass().getSimpleName().equalsIgnoreCase("FlippingCopilotPlugin"))
                .findFirst()
                .orElse(null);
        return flippingCopilot;
    }

    private Object getSuggestionManager(Plugin plugin)
    {
        try
        {
            Field field = plugin.getClass().getDeclaredField("suggestionManager");
            field.setAccessible(true);
            return field.get(plugin);
        }
        catch (Exception ex)
        {
            debug("Unable to access Flipping Copilot suggestionManager: {}", ex.getMessage());
            return null;
        }
    }

    private Object getHighlightController(Plugin plugin)
    {
        try
        {
            Field field = plugin.getClass().getDeclaredField("highlightController");
            field.setAccessible(true);
            return field.get(plugin);
        }
        catch (Exception ex)
        {
            debug("Unable to access Flipping Copilot highlightController: {}", ex.getMessage());
            return null;
        }
    }

    private List<Object> getHighlightOverlays()
    {
        if (highlightController == null)
        {
            return new ArrayList<>();
        }

        try
        {
            Field field = highlightController.getClass().getDeclaredField("highlightOverlays");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Object> overlays = (List<Object>) field.get(highlightController);
            return overlays != null ? overlays : new ArrayList<>();
        }
        catch (Exception ex)
        {
            debug("Unable to access Flipping Copilot highlight overlays: {}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Widget> getHighlightWidgets()
    {
        List<Widget> widgets = new ArrayList<>();
        for (Object overlay : getHighlightOverlays())
        {
            try
            {
                Field widgetField = overlay.getClass().getDeclaredField("widget");
                widgetField.setAccessible(true);
                Widget widget = (Widget) widgetField.get(overlay);
                if (widget != null)
                {
                    widgets.add(widget);
                }
            }
            catch (Exception ex)
            {
                debug("Unable to access Flipping Copilot highlighted widget: {}", ex.getMessage());
            }
        }
        return widgets;
    }

    private boolean checkAndPressCopilotKeybind()
    {
        Widget copilotItemWidget = Rs2Widget.findWidget("Copilot item:", null, false);
        if (copilotItemWidget != null && Rs2Widget.isWidgetVisible(copilotItemWidget.getId()))
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
            sleep(250, 400);
            lastActionAtMs = System.currentTimeMillis();
            return true;
        }

        if (System.currentTimeMillis() - lastActionAtMs < ACTION_COOLDOWN_MS)
        {
            return false;
        }

        Widget setPriceWidget = Rs2Widget.findWidget("Set a price for each item:", null, true);
        Widget setQuantityWidget = Rs2Widget.findWidget("How many do you wish to ", null, false);
        if ((setPriceWidget != null && Rs2Widget.isWidgetVisible(setPriceWidget.getId()))
                || (setQuantityWidget != null && Rs2Widget.isWidgetVisible(setQuantityWidget.getId())))
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_E);
            sleep(250, 400);
            Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
            lastActionAtMs = System.currentTimeMillis();
            return true;
        }

        return false;
    }

    private boolean checkAndAbortOrModifyIfNeeded()
    {
        if (System.currentTimeMillis() - lastActionAtMs < ACTION_COOLDOWN_MS)
        {
            return false;
        }

        Object currentSuggestion = getSuggestion();
        String suggestionType = getSuggestionType(currentSuggestion);
        if (!Objects.equals(suggestionType, "abort")
                && !Objects.equals(suggestionType, "modify_buy")
                && !Objects.equals(suggestionType, "modify_sell"))
        {
            return false;
        }

        Widget highlightedWidget = getWidgetFromOverlay(suggestionType);
        if (highlightedWidget == null)
        {
            return false;
        }

        NewMenuEntry menuEntry = Objects.equals(suggestionType, "modify_buy")
                || Objects.equals(suggestionType, "modify_sell")
                ? new NewMenuEntry("Modify offer", "", 3, MenuAction.CC_OP, 2, highlightedWidget.getId(), false)
                : new NewMenuEntry("Abort offer", "", 2, MenuAction.CC_OP, 2, highlightedWidget.getId(), false);
        Rectangle bounds = highlightedWidget.getBounds() != null && Rs2UiHelper.isRectangleWithinCanvas(highlightedWidget.getBounds())
                ? highlightedWidget.getBounds()
                : Rs2UiHelper.getDefaultRectangle();
        Microbot.doInvoke(menuEntry, bounds);
        lastActionAtMs = System.currentTimeMillis();
        return true;
    }

    private boolean checkAndClickHighlightedWidgets()
    {
        if (System.currentTimeMillis() - lastActionAtMs < ACTION_COOLDOWN_MS)
        {
            return false;
        }

        Widget highlightedWidget = getWidgetFromOverlay(getSuggestionType(getSuggestion()));
        if (highlightedWidget == null || !Rs2Widget.isWidgetVisible(highlightedWidget.getId()))
        {
            return false;
        }

        Rs2Widget.clickWidget(highlightedWidget);
        Rs2Random.wait(100, 200);
        lastActionAtMs = System.currentTimeMillis();

        if ((highlightedWidget.getText() != null
                && highlightedWidget.getText().toLowerCase(Locale.ENGLISH).contains("confirm"))
                || (highlightedWidget.getActions() != null
                && highlightedWidget.getActions().length > 0
                && highlightedWidget.getActions()[0] != null
                && highlightedWidget.getActions()[0].toLowerCase(Locale.ENGLISH).contains("confirm")))
        {
            if (!sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), 5_000))
            {
                Rs2GrandExchange.backToOverview();
                return false;
            }
        }
        return true;
    }

    private Widget getWidgetFromOverlay(String suggestionType)
    {
        List<Widget> highlightedWidgets = getHighlightWidgets();
        if (highlightedWidgets.isEmpty())
        {
            return null;
        }

        if (Objects.equals(suggestionType, "abort")
                || Objects.equals(suggestionType, "modify_buy")
                || Objects.equals(suggestionType, "modify_sell"))
        {
            return highlightedWidgets.stream()
                    .filter(widget -> Arrays.stream(GRAND_EXCHANGE_SLOT_IDS).anyMatch(id -> id == widget.getId()))
                    .findFirst()
                    .orElse(null);
        }

        return highlightedWidgets.get(0);
    }

    private Object getSuggestion()
    {
        if (suggestionManager == null)
        {
            return null;
        }

        try
        {
            Field field = suggestionManager.getClass().getDeclaredField("suggestion");
            field.setAccessible(true);
            return field.get(suggestionManager);
        }
        catch (Exception ex)
        {
            debug("Unable to access Flipping Copilot suggestion: {}", ex.getMessage());
            return null;
        }
    }

    private String getSuggestionType(Object suggestion)
    {
        if (suggestion == null)
        {
            return null;
        }

        try
        {
            Field field = suggestion.getClass().getDeclaredField("type");
            field.setAccessible(true);
            return (String) field.get(suggestion);
        }
        catch (Exception ex)
        {
            debug("Unable to access Flipping Copilot suggestion type: {}", ex.getMessage());
            return null;
        }
    }

    private boolean isCopilotPromptVisible()
    {
        return Rs2Widget.findWidget("Copilot item:", null, false) != null
                || Rs2Widget.findWidget("Set a price for each item:", null, true) != null
                || Rs2Widget.findWidget("How many do you wish to ", null, false) != null;
    }

    private boolean isGrandExchangeOfferFlowActive()
    {
        return Rs2GrandExchange.isOfferScreenOpen() || isCopilotPromptVisible();
    }

    private WorldPoint getAreaCenter()
    {
        int centerX = (targetArea.getSouthWest().getX() + targetArea.getNorthEast().getX()) / 2;
        int centerY = (targetArea.getSouthWest().getY() + targetArea.getNorthEast().getY()) / 2;
        int plane = targetArea.getSouthWest().getPlane();
        return new WorldPoint(centerX, centerY, plane);
    }

    private void debug(String message, Object... args)
    {
        if (debugLogging)
        {
            log.info(message, args);
        }
    }

    @Override
    public void shutdown()
    {
        lastWebWalkAtMs = 0L;
        lastActionAtMs = 0L;
        flippingCopilot = null;
        suggestionManager = null;
        highlightController = null;
        super.shutdown();
    }
}
