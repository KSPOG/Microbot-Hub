package net.runelite.client.plugins.microbot.flippilot;

import net.runelite.client.config.*;

@ConfigGroup("flippilot")
public interface FlipPilotConfig extends Config
{
    @ConfigItem(
            keyName = "enabled",
            name = "Enabled",
            description = "Enable FlipPilot"
    )
    default boolean enabled() { return true; }

    @ConfigItem(
            keyName = "refreshSeconds",
            name = "Refresh (seconds)",
            description = "How often to refresh prices/suggestions"
    )
    @Range(min = 5, max = 300)
    default int refreshSeconds() { return 20; }

    @ConfigItem(
            keyName = "topN",
            name = "Top suggestions",
            description = "How many items to show"
    )
    @Range(min = 5, max = 200)
    default int topN() { return 30; }

    @ConfigItem(
            keyName = "minMargin",
            name = "Min margin (gp)",
            description = "Minimum per-item margin (estimated) to show"
    )
    @Range(min = 0, max = 100000)
    default int minMargin() { return 50; }

    @ConfigItem(
            keyName = "minRoiPct",
            name = "Min ROI (%)",
            description = "Minimum ROI percent to show"
    )
    @Range(min = 0, max = 100)
    default int minRoiPct() { return 1; }

    @ConfigItem(
            keyName = "maxItemPrice",
            name = "Max item price (gp)",
            description = "Ignore items with high price above this"
    )
    @Range(min = 0, max = 2_147_000_000)
    default int maxItemPrice() { return 50_000_000; }

    @ConfigItem(
            keyName = "historyMaxPoints",
            name = "History points per item",
            description = "Max stored history points per item"
    )
    @Range(min = 20, max = 2000)
    default int historyMaxPoints() { return 300; }

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show overlay",
            description = "Show overlay with status and profit"
    )
    default boolean showOverlay() { return true; }

    @ConfigItem(
            keyName = "autoFlipEnabled",
            name = "Enable auto flip",
            description = "Automatically place GE offers for the top suggestion"
    )
    default boolean autoFlipEnabled() { return false; }

    @ConfigItem(
            keyName = "autoFlipQuantity",
            name = "Auto flip quantity",
            description = "Quantity to buy/sell per automated offer"
    )
    @Range(min = 1, max = 1000)
    default int autoFlipQuantity() { return 10; }

    @ConfigItem(
            keyName = "autoFlipCooldownSeconds",
            name = "Auto flip cooldown (seconds)",
            description = "Delay between automated GE actions"
    )
    @Range(min = 1, max = 60)
    default int autoFlipCooldownSeconds() { return 4; }

    // Alerts
    @ConfigItem(
            keyName = "alertsEnabled",
            name = "Enable alerts",
            description = "Notify when watchlist items match alert rules"
    )
    default boolean alertsEnabled() { return true; }

    @ConfigItem(
            keyName = "alertCooldownSeconds",
            name = "Alert cooldown (seconds)",
            description = "Minimum time between alerts for the same item"
    )
    @Range(min = 10, max = 3600)
    default int alertCooldownSeconds() { return 120; }

    @ConfigItem(
            keyName = "alertMinMargin",
            name = "Alert min margin (gp)",
            description = "Only alert if margin >= this"
    )
    @Range(min = 0, max = 1_000_000)
    default int alertMinMargin() { return 200; }

    @ConfigItem(
            keyName = "alertMinRoiPct",
            name = "Alert min ROI (%)",
            description = "Only alert if ROI% >= this"
    )
    @Range(min = 0, max = 100)
    default int alertMinRoiPct() { return 2; }

    @ConfigItem(
            keyName = "alertMaxRisk",
            name = "Alert max risk (0..1)",
            description = "Only alert if risk <= this (lower is safer)"
    )
    @Range(min = 0, max = 1)
    default double alertMaxRisk() { return 0.45; }

    @ConfigItem(
            keyName = "alertMinVol5m",
            name = "Alert min volume (5m)",
            description = "Only alert if 5m volume >= this"
    )
    @Range(min = 0, max = 1_000_000)
    default int alertMinVol5m() { return 50; }
}
