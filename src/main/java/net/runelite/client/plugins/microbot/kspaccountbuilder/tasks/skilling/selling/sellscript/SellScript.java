package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sellscript;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.gearea.GEArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sell.SellList;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
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
public class SellScript extends Script
{
    private static final int LOOP_DELAY_MS = 600;
    private static final int WEB_WALK_COOLDOWN_MS = 3_000;
    private static final int ACTION_COOLDOWN_MS = 1_200;
    private static final int INVENTORY_WAIT_TIMEOUT_MS = 3_000;
    private static final int[] GRAND_EXCHANGE_SLOT_IDS = {
            465,
            468,
            471,
            474,
            477,
            480,
            483,
            486
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

            if (Rs2GrandExchange.hasSoldOffer() && ensureGrandExchangeOpen())
            {
                Microbot.status = "Collecting Sold Items";
                Rs2GrandExchange.collectAllToBank();
                sleepUntil(() -> !Rs2GrandExchange.hasSoldOffer(), 5_000);
                return;
            }

            if (isGrandExchangeOfferFlowActive())
            {
                Microbot.status = "Setting Offer Price";
                handleCopilotSellFlow();
                return;
            }

            if (!hasSellableInventoryItems())
            {
                prepareSellInventoryFromBank();
                return;
            }

            if (!ensureGrandExchangeOpen())
            {
                return;
            }

            if (isCopilotAvailable())
            {
                Microbot.status = "Following Flipping Copilot";
                handleCopilotSellFlow();
                return;
            }

            if (Rs2GrandExchange.getAvailableSlotsCount() <= 0)
            {
                if (Rs2GrandExchange.hasSoldOffer())
                {
                    Rs2GrandExchange.collectAllToBank();
                    sleepUntil(() -> !Rs2GrandExchange.hasSoldOffer(), 5_000);
                }
                return;
            }

            Rs2ItemModel nextItem = getNextSellableInventoryItem();
            if (nextItem == null)
            {
                Microbot.status = "Waiting at GE";
                return;
            }

            placeFallbackSellOffer(nextItem);
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

    private void prepareSellInventoryFromBank()
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
                sleepUntil(Rs2Bank::isOpen, 3_000);
            }
            else
            {
                Microbot.status = "Walking to Bank";
                Rs2Bank.walkToBankAndUseBank();
            }
            return;
        }

        Microbot.status = "Withdrawing Sell Items";
        if (!Rs2Inventory.isEmpty())
        {
            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty, INVENTORY_WAIT_TIMEOUT_MS);
        }

        if (!Rs2Bank.hasWithdrawAsNote())
        {
            Rs2Bank.setWithdrawAsNote();
            sleepUntil(Rs2Bank::hasWithdrawAsNote, 2_000);
        }

        boolean withdrewAny = false;
        for (SellList sellList : SellList.values())
        {
            if (!shouldSellEntry(sellList))
            {
                continue;
            }

            if (Rs2Inventory.isFull())
            {
                break;
            }

            if (Rs2Bank.count(sellList.getDisplayName()) <= 0)
            {
                continue;
            }

            Rs2Bank.withdrawAll(sellList.getDisplayName(), true);
            boolean withdrew = sleepUntil(() -> Rs2Inventory.hasItem(sellList.getDisplayName(), true), INVENTORY_WAIT_TIMEOUT_MS);
            withdrewAny = withdrewAny || withdrew;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
        Microbot.status = withdrewAny ? "Opening GE" : "No Sell Items";
    }

    private boolean shouldSellEntry(SellList sellList)
    {
        int firemakingLevel = Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING);
        if (sellList == SellList.LOGS)
        {
            return firemakingLevel >= 15;
        }

        if (sellList == SellList.OAK_LOGS)
        {
            return firemakingLevel >= 30;
        }

        return true;
    }

    private boolean hasSellableInventoryItems()
    {
        return getNextSellableInventoryItem() != null;
    }

    private boolean hasSellableBankItems()
    {
        for (SellList sellList : SellList.values())
        {
            if (!shouldSellEntry(sellList))
            {
                continue;
            }

            if (Rs2Bank.count(sellList.getDisplayName()) > 0)
            {
                return true;
            }
        }

        return false;
    }

    private Rs2ItemModel getNextSellableInventoryItem()
    {
        List<String> sellNames = getAllowedSellNames();
        for (Rs2ItemModel item : Rs2Inventory.all())
        {
            if (item == null || item.getName() == null)
            {
                continue;
            }

            if (sellNames.stream().anyMatch(name -> name.equalsIgnoreCase(item.getName())))
            {
                return item;
            }
        }

        return null;
    }

    private List<String> getAllowedSellNames()
    {
        List<String> names = new ArrayList<>();
        for (SellList sellList : SellList.values())
        {
            if (shouldSellEntry(sellList))
            {
                names.add(sellList.getDisplayName());
            }
        }
        return names;
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

        String suggestionType = getSuggestionType(getSuggestion());
        Widget highlightedWidget = getWidgetFromOverlay(suggestionType);
        if (highlightedWidget == null || !Rs2Widget.isWidgetVisible(highlightedWidget.getId()))
        {
            return false;
        }

        if (shouldCollectToBank(suggestionType, highlightedWidget))
        {
            Rs2GrandExchange.collectAllToBank();
            sleepUntil(() -> !Rs2GrandExchange.hasSoldOffer(), 5_000);
            lastActionAtMs = System.currentTimeMillis();
            return true;
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

    private boolean shouldCollectToBank(String suggestionType, Widget highlightedWidget)
    {
        if (Objects.equals(suggestionType, "collect"))
        {
            return true;
        }

        if (highlightedWidget.getText() != null
                && highlightedWidget.getText().toLowerCase(Locale.ENGLISH).contains("collect"))
        {
            return true;
        }

        String[] actions = highlightedWidget.getActions();
        if (actions == null)
        {
            return false;
        }

        for (String action : actions)
        {
            if (action != null && action.toLowerCase(Locale.ENGLISH).contains("collect"))
            {
                return true;
            }
        }

        return false;
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
