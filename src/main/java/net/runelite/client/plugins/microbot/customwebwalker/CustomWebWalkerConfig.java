package net.runelite.client.plugins.microbot.customwebwalker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(CustomWebWalkerConfig.configGroup)
public interface CustomWebWalkerConfig extends Config {
    String configGroup = "micro-customwebwalker";

    @ConfigSection(
            name = "Destination",
            description = "Target tile settings",
            position = 0
    )
    String destinationSection = "destination";

    @ConfigItem(
            keyName = "targetX",
            name = "Target X",
            description = "WorldPoint X coordinate",
            position = 0,
            section = destinationSection
    )
    default int targetX() {
        return 3208;
    }

    @ConfigItem(
            keyName = "targetY",
            name = "Target Y",
            description = "WorldPoint Y coordinate",
            position = 1,
            section = destinationSection
    )
    default int targetY() {
        return 3220;
    }

    @ConfigItem(
            keyName = "targetPlane",
            name = "Target plane",
            description = "WorldPoint plane",
            position = 2,
            section = destinationSection
    )
    default int targetPlane() {
        return 0;
    }

    @ConfigSection(
            name = "Walker",
            description = "Walker behavior",
            position = 1
    )
    String walkerSection = "walker";

    @ConfigItem(
            keyName = "reachedDistance",
            name = "Reached distance",
            description = "Distance considered arrival",
            position = 0,
            section = walkerSection
    )
    default int reachedDistance() {
        return 3;
    }

    @ConfigItem(
            keyName = "timeoutMs",
            name = "Timeout (ms)",
            description = "Maximum time to attempt walking",
            position = 1,
            section = walkerSection
    )
    default int timeoutMs() {
        return 90_000;
    }
}