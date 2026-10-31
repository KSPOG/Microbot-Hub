package net.runelite.client.plugins.microbot.marshysnakeweed;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("marshysnakeweed")
@ConfigInformation(
        "<h2>Marshy Snake Weed</h2>" +
        "<h3>Version: " + MarshySnakeWeedPlugin.version + "</h3>" +
        "<p>Searches marshy jungle vines south of Tai Bwo Wannai for grimy snake weed, then banks it.</p>" +
        "<p>Recommended: start with an empty inventory near Tai Bwo Wannai, or enable walking to the vine area.</p>" +
        "<p>Optional: carry Tai Bwo Wannai Teleports and Varrock Teleports to skip most of the walking.</p>"
)
public interface MarshySnakeWeedConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General behaviour",
            position = 0
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Banking",
            description = "Banking behaviour",
            position = 1
    )
    String bankingSection = "banking";

    @ConfigSection(
            name = "Teleports",
            description = "Teleport items used to skip walking",
            position = 2
    )
    String teleportSection = "teleports";

    @ConfigSection(
            name = "Anti-ban",
            description = "Human-like behaviour to reduce detection risk",
            position = 3
    )
    String antibanSection = "antiban";

    @ConfigItem(
            keyName = "walkToVineArea",
            name = "Walk to vine area",
            description = "Walk to the known marshy jungle vine area if no vine is visible.",
            position = 0,
            section = generalSection
    )
    default boolean walkToVineArea() {
        return true;
    }

    @Range(min = 5, max = 60)
    @ConfigItem(
            keyName = "vineSearchRadius",
            name = "Vine search radius",
            description = "How far away to look for marshy jungle vines.",
            position = 1,
            section = generalSection
    )
    default int vineSearchRadius() {
        return 25;
    }

    @Range(min = 600, max = 6000)
    @ConfigItem(
            keyName = "searchWaitMsMin",
            name = "Search wait ms (min)",
            description = "Minimum time to wait after searching a vine before trying again.",
            position = 2,
            section = generalSection
    )
    default int searchWaitMsMin() {
        return 1400;
    }

    @Range(min = 600, max = 6000)
    @ConfigItem(
            keyName = "searchWaitMsMax",
            name = "Search wait ms (max)",
            description = "Maximum time to wait after searching a vine before trying again. A random value between min and max is picked each time.",
            position = 3,
            section = generalSection
    )
    default int searchWaitMsMax() {
        return 2400;
    }

    @Range(min = 1, max = 28)
    @ConfigItem(
            keyName = "bankAtSnakeWeedCount",
            name = "Bank at amount",
            description = "Bank once this many grimy snake weeds are in the inventory. The script also banks when the inventory is full.",
            position = 0,
            section = bankingSection
    )
    default int bankAtSnakeWeedCount() {
        return 28;
    }

    @ConfigItem(
            keyName = "itemsToKeep",
            name = "Items to keep",
            description = "Comma-separated partial item names to keep when banking. Leave empty to deposit the whole inventory.",
            position = 1,
            section = bankingSection
    )
    default String itemsToKeep() {
        return "";
    }

    @ConfigItem(
            keyName = "useTaiBwoWannaiTeleport",
            name = "Use Tai Bwo Wannai Teleport",
            description = "Before walking to the vine area, use a Tai Bwo Wannai Teleport if one is in the inventory.",
            position = 0,
            section = teleportSection
    )
    default boolean useTaiBwoWannaiTeleport() {
        return true;
    }

    @ConfigItem(
            keyName = "useVarrockTeleport",
            name = "Use Varrock Teleport",
            description = "Before walking to the bank, use a Varrock Teleport if one is in the inventory.",
            position = 1,
            section = teleportSection
    )
    default boolean useVarrockTeleport() {
        return true;
    }

    @ConfigItem(
            keyName = "enableAntiban",
            name = "Enable anti-ban",
            description = "Adds humanized delays, occasional miss-clicks and micro-breaks to the script.",
            position = 0,
            section = antibanSection
    )
    default boolean enableAntiban() {
        return true;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "missClickChance",
            name = "Miss-click chance %",
            description = "Chance per action that the script simulates a missed click instead of clicking immediately.",
            position = 1,
            section = antibanSection
    )
    default int missClickChance() {
        return 8;
    }

    @ConfigItem(
            keyName = "humanizedClicks",
            name = "Humanized click delay",
            description = "Adds a small randomized delay before clicks to avoid instant, robotic actions.",
            position = 2,
            section = antibanSection
    )
    default boolean humanizedClicks() {
        return true;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "microBreakChance",
            name = "Micro-break chance %",
            description = "Chance after a successful search to take a short randomized break.",
            position = 3,
            section = antibanSection
    )
    default int microBreakChance() {
        return 3;
    }

    @Range(min = 500, max = 15000)
    @ConfigItem(
            keyName = "microBreakMinMs",
            name = "Micro-break min ms",
            description = "Minimum length of a micro-break.",
            position = 4,
            section = antibanSection
    )
    default int microBreakMinMs() {
        return 1500;
    }

    @Range(min = 500, max = 15000)
    @ConfigItem(
            keyName = "microBreakMaxMs",
            name = "Micro-break max ms",
            description = "Maximum length of a micro-break.",
            position = 5,
            section = antibanSection
    )
    default int microBreakMaxMs() {
        return 5000;
    }
}
