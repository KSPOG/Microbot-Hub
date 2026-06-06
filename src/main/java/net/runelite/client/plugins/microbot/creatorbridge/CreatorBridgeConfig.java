package net.runelite.client.plugins.microbot.creatorbridge;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(CreatorBridgeConfig.GROUP)
public interface CreatorBridgeConfig extends Config
{
    String GROUP = "creatorbridge";

    @ConfigItem(
            keyName = "enabled",
            name = "Enable Bridge",
            description = "Enable sending local telemetry to the creator app.",
            position = 0
    )
    default boolean enabled()
    {
        return true;
    }

    @ConfigItem(
            keyName = "creatorPort",
            name = "Creator API Port",
            description = "Localhost port used by the creator telemetry API. Default: 8765.",
            position = 1
    )
    default String creatorPort()
    {
        return "8765";
    }

    @ConfigItem(
            keyName = "launchToken",
            name = "Launch Token",
            description = "Optional local-only token used to pair this Microbot client with the creator app.",
            secret = true,
            position = 2
    )
    default String launchToken()
    {
        return "";
    }

    @ConfigItem(
            keyName = "profileName",
            name = "Profile Name Override",
            description = "Optional fallback/override. Leave empty to use the active RuneLite/Microbot profile automatically.",
            position = 3
    )
    default String profileName()
    {
        return "";
    }

    @Range(min = 1, max = 60)
    @ConfigItem(
            keyName = "heartbeatIntervalSeconds",
            name = "Heartbeat Interval",
            description = "How often the plugin sends telemetry to the local creator app, in seconds.",
            position = 4
    )
    default int heartbeatIntervalSeconds()
    {
        return 3;
    }

    @ConfigItem(
            keyName = "sendPlayerName",
            name = "Send Player Name",
            description = "Include the local player's display name in telemetry.",
            position = 10
    )
    default boolean sendPlayerName()
    {
        return true;
    }

    @ConfigItem(
            keyName = "sendLocation",
            name = "Send Location",
            description = "Include the local player's world location in telemetry.",
            position = 11
    )
    default boolean sendLocation()
    {
        return true;
    }

    @ConfigItem(
            keyName = "sendLevels",
            name = "Send Levels",
            description = "Include all real skill levels in telemetry.",
            position = 12
    )
    default boolean sendLevels()
    {
        return true;
    }

    @ConfigItem(
            keyName = "sendXp",
            name = "Send XP",
            description = "Include all skill XP values in telemetry.",
            position = 13
    )
    default boolean sendXp()
    {
        return true;
    }

    @ConfigItem(
            keyName = "sendQuestStates",
            name = "Send Quest States",
            description = "Include quest completion summary and optionally full quest states.",
            position = 20
    )
    default boolean sendQuestStates()
    {
        return true;
    }

    @ConfigItem(
            keyName = "sendFullQuestStateMap",
            name = "Send Full Quest Map",
            description = "Include every quest state in the payload. Disable this if you only need completed/total counts and recent quest events.",
            position = 21
    )
    default boolean sendFullQuestStateMap()
    {
        return false;
    }

    @ConfigItem(
            keyName = "trackInventoryGains",
            name = "Track Inventory Gains",
            description = "Detect item acquisition by comparing inventory item counts between snapshots.",
            position = 30
    )
    default boolean trackInventoryGains()
    {
        return true;
    }

    @ConfigItem(
            keyName = "trackNearbyGroundItems",
            name = "Track Nearby Ground Items",
            description = "Use the Microbot Queryable API to report nearby lootable ground items.",
            position = 31
    )
    default boolean trackNearbyGroundItems()
    {
        return true;
    }

    @Range(min = 1, max = 50)
    @ConfigItem(
            keyName = "nearbyGroundItemRadius",
            name = "Ground Item Radius",
            description = "Maximum tile distance for nearby ground item telemetry.",
            position = 32
    )
    default int nearbyGroundItemRadius()
    {
        return 15;
    }

    @Range(min = 0, max = 2_000_000_000)
    @ConfigItem(
            keyName = "groundItemMinGeValue",
            name = "Ground Item Min GE Value",
            description = "Only report nearby ground items with at least this total GE value. Use 0 to report all lootable items.",
            position = 33
    )
    default int groundItemMinGeValue()
    {
        return 0;
    }

    @ConfigItem(
            keyName = "trackDropMessages",
            name = "Track Drop Messages",
            description = "Capture loot/drop style game messages as recent item events.",
            position = 34
    )
    default boolean trackDropMessages()
    {
        return true;
    }

    @Range(min = 5, max = 600)
    @ConfigItem(
            keyName = "stationaryThresholdSeconds",
            name = "Stationary Threshold Seconds",
            description = "Mark the player as stationary too long after this many seconds on the same tile.",
            position = 40
    )
    default int stationaryThresholdSeconds()
    {
        return 45;
    }

    @Range(min = 1, max = 100)
    @ConfigItem(
            keyName = "maxRecentEvents",
            name = "Max Recent Events",
            description = "Maximum recent quest/item events retained and sent in telemetry.",
            position = 50
    )
    default int maxRecentEvents()
    {
        return 25;
    }

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug Logging",
            description = "Print detailed bridge logs to the client log.",
            position = 99
    )
    default boolean debugLogging()
    {
        return false;
    }
}
