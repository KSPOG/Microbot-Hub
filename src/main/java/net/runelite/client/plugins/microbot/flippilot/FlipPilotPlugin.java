package net.runelite.client.plugins.microbot.flippilot;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import net.runelite.client.plugins.microbot.flippilot.alerts.AlertManager;
import net.runelite.client.plugins.microbot.flippilot.data.*;
import net.runelite.client.plugins.microbot.flippilot.engine.*;
import net.runelite.client.plugins.microbot.flippilot.microbot.*;
import net.runelite.client.plugins.microbot.flippilot.storage.WatchlistStore;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@PluginDescriptor(
        name = "FlipPilot",
        description = "Original flipping assistant: suggestions, charts, watchlist alerts (no auth). Members-aware universe.",
        tags = {"microbot", "flipping", "ge", "prices"}
)
public class FlipPilotPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientToolbar clientToolbar;
    @Inject private OverlayManager overlayManager;
    @Inject private Notifier notifier;

    @Inject private FlipPilotConfig config;

    @Inject private ItemRepository itemRepository;
    @Inject private MembersDetector membersDetector;
    @Inject private WikiPriceService wikiPriceService;
    @Inject private Wiki5mService wiki5mService;
    @Inject private HistoryStore historyStore;

    @Inject private SuggestionEngine suggestionEngine;

    @Inject private FlipEventBus flipEventBus;

    @Inject private WatchlistStore watchlistStore;
    @Inject private AlertManager alertManager;

    @Inject private FlipPilotOverlay overlay;

    private ScheduledExecutorService executor;
    private FlipPilotPanel panel;
    private NavigationButton navButton;

    @Provides
    FlipPilotConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(FlipPilotConfig.class);
    }

    @Override
    protected void startUp()
    {
        if (!config.enabled())
        {
            return;
        }

        itemRepository.ensureLoaded();

        panel = new FlipPilotPanel(flipEventBus, historyStore, watchlistStore);

        BufferedImage icon = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

        navButton = NavigationButton.builder()
                .tooltip("FlipPilot")
                .icon(icon)
                .priority(6)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
        overlayManager.add(overlay);

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FlipPilot-Worker");
            t.setDaemon(true);
            return t;
        });

        int refresh = Math.max(5, config.refreshSeconds());
        executor.scheduleAtFixedRate(this::refreshLoop, 0, refresh, TimeUnit.SECONDS);

        // lightweight UI tick
        executor.scheduleAtFixedRate(() -> SwingUtilities.invokeLater(() -> {
            if (panel != null) panel.tickUi();
        }), 1, 1, TimeUnit.SECONDS);

        log.info("FlipPilot started");
    }

    @Override
    protected void shutDown()
    {
        try
        {
            overlayManager.remove(overlay);
            if (navButton != null) clientToolbar.removeNavigation(navButton);
        }
        catch (Exception ignored) {}

        if (executor != null)
        {
            executor.shutdownNow();
            executor = null;
        }

        historyStore.save();
        log.info("FlipPilot stopped");
    }

    private void refreshLoop()
    {
        try
        {
            boolean isMembers = membersDetector.isMembers();

            if (panel != null)
            {
                SwingUtilities.invokeLater(() -> panel.setMembers(isMembers));
            }

            overlay.setStatus("fetching prices");
            if (panel != null) SwingUtilities.invokeLater(() -> panel.setStatus("fetching prices"));

            Map<Integer, LivePrice> latest = wikiPriceService.fetchLatest();
            Map<Integer, Integer> vol5m = wiki5mService.fetchVolumes();

            // append history (cap write volume)
            long now = System.currentTimeMillis();
            int wrote = 0;
            for (Map.Entry<Integer, LivePrice> e : latest.entrySet())
            {
                if (wrote > 2000) break;
                LivePrice p = e.getValue();
                historyStore.append(e.getKey(), new PricePoint(now, p.high, p.low), config.historyMaxPoints());
                wrote++;
            }

            List<ItemDef> universe = itemRepository.getUniverse(isMembers);
            List<Suggestion> suggestions = suggestionEngine.build(universe, latest, vol5m);

            // Alerts for watchlist items
            for (Suggestion s : suggestions)
            {
                if (!watchlistStore.isWatched(s.itemId)) continue;

                if (alertManager.shouldAlert(s))
                {
                    String msg = String.format("FlipPilot: %s margin=%d roi=%.2f%% vol5m=%d risk=%.2f",
                            s.name, s.margin, s.roiPct, s.vol5m, s.risk);
                    notifier.notify(msg);
                }
            }

            overlay.setStatus("ready");
            if (panel != null)
            {
                SwingUtilities.invokeLater(() -> {
                    panel.setStatus("ready");
                    panel.setSuggestions(suggestions);
                });
            }

            historyStore.save();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            overlay.setStatus("error");
            if (panel != null) SwingUtilities.invokeLater(() -> panel.setStatus("error: " + ex.getMessage()));
        }
    }

    /**
     * Safe hook: your own logic can call this to report completed flips for tracking UI.
     */
    public void reportFlip(int itemId, String itemName, int qty, long profit)
    {
        flipEventBus.publish(new FlipEvent(System.currentTimeMillis(), itemId, itemName, qty, profit));
    }
}
