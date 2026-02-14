package net.runelite.client.plugins.microbot.flippilot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.flippilot.engine.Suggestion;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
public class FlipPilotAutoFlipScript extends Script
{
    private Supplier<Suggestion> suggestionSupplier;
    private FlipPilotConfig config;
    private long lastActionTimeMs;

    public boolean run(FlipPilotConfig config, Supplier<Suggestion> suggestionSupplier)
    {
        this.config = config;
        this.suggestionSupplier = suggestionSupplier;
        lastActionTimeMs = 0;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run() || !isRunning()) return;
                if (!config.autoFlipEnabled()) return;

                Suggestion suggestion = suggestionSupplier.get();
                if (suggestion == null) return;

                if (!Rs2GrandExchange.isOpen())
                {
                    Rs2GrandExchange.openExchange();
                    return;
                }

                if (Rs2GrandExchange.getAvailableSlotsCount() == 0)
                {
                    if (Rs2GrandExchange.hasSoldOffer() || Rs2GrandExchange.hasBoughtOffer())
                    {
                        Rs2GrandExchange.collectAllToBank();
                    }
                    return;
                }

                long now = System.currentTimeMillis();
                long cooldownMs = Math.max(1000L, config.autoFlipCooldownSeconds() * 1000L);
                if (now - lastActionTimeMs < cooldownMs)
                {
                    return;
                }

                int quantity = Math.max(1, config.autoFlipQuantity());
                if (suggestion.limit > 0)
                {
                    quantity = Math.min(quantity, suggestion.limit);
                }

                if (Rs2Inventory.hasItem(suggestion.itemId))
                {
                    int inventoryQty = Rs2Inventory.count(suggestion.itemId);
                    int sellQty = Math.min(inventoryQty, quantity);
                    if (sellQty <= 0)
                    {
                        return;
                    }

                    int sellPrice = Math.max(1, suggestion.high);
                    GrandExchangeRequest request = GrandExchangeRequest.builder()
                            .action(GrandExchangeAction.SELL)
                            .itemName(suggestion.name)
                            .quantity(sellQty)
                            .price(sellPrice)
                            .closeAfterCompletion(false)
                            .build();
                    if (Rs2GrandExchange.processOffer(request))
                    {
                        lastActionTimeMs = now;
                    }
                }
                else
                {
                    int buyPrice = Math.max(1, suggestion.low);
                    GrandExchangeRequest request = GrandExchangeRequest.builder()
                            .action(GrandExchangeAction.BUY)
                            .itemName(suggestion.name)
                            .quantity(quantity)
                            .price(buyPrice)
                            .closeAfterCompletion(false)
                            .build();
                    if (Rs2GrandExchange.processOffer(request))
                    {
                        lastActionTimeMs = now;
                    }
                }
            }
            catch (Exception ex)
            {
                log.error("Error in FlipPilot auto flip: {} - ", ex.getMessage(), ex);
            }
        }, 0, 800, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown()
    {
        suggestionSupplier = null;
        config = null;
        lastActionTimeMs = 0;
        super.shutdown();
    }
}
