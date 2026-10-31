package net.runelite.client.plugins.microbot.kspauburnvaleplankmaker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("kspAuburnvalePlankMaker")
public interface KspAuburnvalePlankMakerConfig extends Config {
    @ConfigSection(
            name = "Production",
            description = "Plank type and production quantities",
            position = 0
    )
    String productionSection = "production";

    @ConfigSection(
            name = "Travel",
            description = "Teleport and stamina settings",
            position = 1
    )
    String travelSection = "travel";

    @ConfigSection(
            name = "Grand Exchange",
            description = "Restocking and offer thresholds",
            position = 2
    )
    String geSection = "ge";

    @ConfigSection(
            name = "Break Handler",
            description = "Account Builder-compatible randomized logout breaks",
            position = 3
    )
    String breakHandlerSection = "breakHandler";

    @ConfigItem(
            keyName = "doBreaks",
            name = "Do Breaks",
            description = "Perform randomized logout breaks",
            position = 0,
            section = breakHandlerSection
    )
    default boolean doBreaks() {
        return true;
    }

    @Range(min = 5, max = 300)
    @ConfigItem(
            keyName = "breakAfterMinMinutes",
            name = "Break After Min (min)",
            description = "Minimum minutes of runtime before a break",
            position = 1,
            section = breakHandlerSection
    )
    default int breakAfterMinMinutes() {
        return 45;
    }

    @Range(min = 5, max = 300)
    @ConfigItem(
            keyName = "breakAfterMaxMinutes",
            name = "Break After Max (min)",
            description = "Maximum minutes of runtime before a break",
            position = 2,
            section = breakHandlerSection
    )
    default int breakAfterMaxMinutes() {
        return 90;
    }

    @Range(min = 1, max = 180)
    @ConfigItem(
            keyName = "breakDurationMinMinutes",
            name = "Break Duration Min (min)",
            description = "Minimum break duration in minutes",
            position = 3,
            section = breakHandlerSection
    )
    default int breakDurationMinMinutes() {
        return 5;
    }

    @Range(min = 1, max = 180)
    @ConfigItem(
            keyName = "breakDurationMaxMinutes",
            name = "Break Duration Max (min)",
            description = "Maximum break duration in minutes",
            position = 4,
            section = breakHandlerSection
    )
    default int breakDurationMaxMinutes() {
        return 15;
    }

    @ConfigSection(
            name = "Custom World Hopper",
            description = "Randomized members-world hopping with PvP and total-level eligibility filtering",
            position = 4
    )
    String worldHopperSection = "worldHopper";

    @ConfigSection(
            name = "Debug",
            description = "Diagnostic logging for Fast Planker",
            position = 5
    )
    String debugSection = "debug";

    @ConfigItem(
            keyName = "plankType",
            name = "Plank",
            description = "Plank type to make at the Auburnvale sawmill",
            position = 0,
            section = productionSection
    )
    default PlankType plankType() {
        return PlankType.MAHOGANY;
    }

    @Range(min = 27, max = 50_000)
    @ConfigItem(
            keyName = "logBuyAmount",
            name = "Logs to buy",
            description = "Exact number of selected logs to buy each time a GE log restock is triggered",
            position = 1,
            section = productionSection
    )
    default int logBuyAmount() {
        return 2_000;
    }

    @Range(min = 27, max = 50_000)
    @ConfigItem(
            keyName = "sellPlanksAt",
            name = "Sell planks at",
            description = "Return to the Grand Exchange and sell the configured plank once banked plank stock reaches this amount",
            position = 2,
            section = productionSection
    )
    default int sellPlanksAt() {
        return 2_000;
    }

    @ConfigItem(
            keyName = "useStaminaPotions",
            name = "Use Stamina potions",
            description = "Automatically buy and use Stamina potions. Lower-dose potions are always consumed before 4-dose potions.",
            position = 0,
            section = travelSection
    )
    default boolean useStaminaPotions() {
        return true;
    }

    @Range(min = 1, max = 100)
    @ConfigItem(
            keyName = "staminaRestockAmount",
            name = "Stamina restock",
            description = "Target number of Stamina potion stacks. New purchases are 4-dose potions.",
            position = 1,
            section = travelSection
    )
    default int staminaRestockAmount() {
        return 10;
    }

    @Range(min = 1, max = 90)
    @ConfigItem(
            keyName = "drinkStaminaBelow",
            name = "Drink below run %",
            description = "Drink a Stamina potion at or below this run-energy percentage when no stamina effect is active",
            position = 2,
            section = travelSection
    )
    default int drinkStaminaBelow() {
        return 30;
    }

    @Range(min = 1, max = 100)
    @ConfigItem(
            keyName = "ringRestockAmount",
            name = "Ring of wealth restock",
            description = "Target number of charged Rings of wealth. Missing rings are bought as Ring of wealth (5).",
            position = 3,
            section = travelSection
    )
    default int ringRestockAmount() {
        return 10;
    }

    @Range(min = 1, max = 250)
    @ConfigItem(
            keyName = "civitasTabletRestockAmount",
            name = "Civitas tablet restock",
            description = "Target number of Civitas illa Fortis teleport tablets",
            position = 4,
            section = travelSection
    )
    default int civitasTabletRestockAmount() {
        return 25;
    }

    @Range(min = 0, max = 50)
    @ConfigItem(
            keyName = "buyThresholdPercent",
            name = "Buy threshold %",
            description = "Maximum premium above the reference GE price used for input buy offers. Example: 5 = up to 105%.",
            position = 0,
            section = geSection
    )
    default int buyThresholdPercent() {
        return 5;
    }

    @Range(min = 0, max = 50)
    @ConfigItem(
            keyName = "sellThresholdPercent",
            name = "Sell threshold %",
            description = "Maximum discount below the reference GE price used for output sell offers. Example: 5 = down to 95%.",
            position = 1,
            section = geSection
    )
    default int sellThresholdPercent() {
        return 5;
    }

    @Range(min = 1, max = 1_000)
    @ConfigItem(
            keyName = "sellUnchargedRingsAt",
            name = "Sell uncharged rings at",
            description = "Sell uncharged Rings of wealth once banked stock reaches this amount",
            position = 2,
            section = geSection
    )
    default int sellUnchargedRingsAt() {
        return 10;
    }

    @Range(min = 5, max = 180)
    @ConfigItem(
            keyName = "offerWaitSeconds",
            name = "Offer wait (sec)",
            description = "Time before a slow sell offer is repriced toward the configured floor. Buy offers remain active at the configured ceiling.",
            position = 3,
            section = geSection
    )
    default int offerWaitSeconds() {
        return 30;
    }


    @ConfigItem(
            keyName = "enableWorldHopper",
            name = "Enable world hopper",
            description = "Hop to a random eligible members world after a randomized interval",
            position = 0,
            section = worldHopperSection
    )
    default boolean enableWorldHopper() {
        return true;
    }

    @Range(min = 5, max = 300)
    @ConfigItem(
            keyName = "worldHopMinMinutes",
            name = "X Minutes",
            description = "Minimum minutes before the next randomized world hop",
            position = 1,
            section = worldHopperSection
    )
    default int worldHopMinMinutes() {
        return 30;
    }

    @Range(min = 5, max = 300)
    @ConfigItem(
            keyName = "worldHopMaxMinutes",
            name = "Y Minutes",
            description = "Maximum minutes before the next randomized world hop",
            position = 2,
            section = worldHopperSection
    )
    default int worldHopMaxMinutes() {
        return 60;
    }

    @ConfigItem(
            keyName = "showOverlay",
            name = "Overlay",
            description = "Show the Fast Planker overlay",
            position = 4,
            section = geSection
    )
    default boolean showOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug logging",
            description = "Write detailed Fast Planker state/action diagnostics to client.log",
            position = 0,
            section = debugSection
    )
    default boolean debugLogging() {
        return true;
    }

    @Range(min = 1, max = 30)
    @ConfigItem(
            keyName = "debugHeartbeatSeconds",
            name = "Debug heartbeat (sec)",
            description = "Interval between full state snapshots while debug logging is enabled",
            position = 1,
            section = debugSection
    )
    default int debugHeartbeatSeconds() {
        return 3;
    }
}
