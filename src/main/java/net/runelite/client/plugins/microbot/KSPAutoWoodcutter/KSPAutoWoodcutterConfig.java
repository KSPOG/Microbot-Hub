package net.runelite.client.plugins.microbot.KSPAutoWoodcutter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("KSPAutoWoodcutter")
public interface KSPAutoWoodcutterConfig extends Config {
    String configGroup = "KSPAutoWoodcutter";

    @ConfigSection(
            name = "General",
            description = "General settings",
            position = 0
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Forestry",
            description = "Forestry events",
            position = 1,
            closedByDefault = true
    )
    String forestrySection = "forestry";

    @ConfigItem(
            keyName = "mode",
            name = "Mode",
            description = "Select woodcutting mode",
            position = 0,
            section = generalSection
    )
    default KSPAutoWoodcutterMode mode() {
        return KSPAutoWoodcutterMode.CHOP_DROP;
    }

    @ConfigItem(
            keyName = "tree",
            name = "Tree",
            description = "Select which tree to chop (non-progressive modes)",
            position = 1,
            section = generalSection
    )
    default KSPAutoWoodcutterTree tree() {
        return KSPAutoWoodcutterTree.TREE;
    }

    @ConfigItem(
            keyName = "enableAntiban",
            name = "Enable Antiban",
            description = "Use universal antiban settings",
            position = 2,
            section = generalSection
    )
    default boolean enableAntiban() {
        return true;
    }

    @ConfigItem(
            keyName = "enableForestry",
            name = "Enable forestry",
            description = "Enable forestry features",
            position = 0,
            section = forestrySection
    )
    default boolean enableForestry() {
        return false;
    }

    @ConfigItem(
            keyName = "eggEvent",
            name = "Enable Egg Event",
            description = "Enable the Egg forestry event",
            position = 1,
            section = forestrySection
    )
    default boolean eggEvent() {
        return true;
    }

    @ConfigItem(
            keyName = "entlingsEvent",
            name = "Enable Entlings Event",
            description = "Enable the Entlings forestry event",
            position = 2,
            section = forestrySection
    )
    default boolean entlingsEvent() {
        return true;
    }

    @ConfigItem(
            keyName = "flowersEvent",
            name = "Enable Flowers Event",
            description = "Enable the Flowers forestry event",
            position = 3,
            section = forestrySection,
            hidden = false
    )
    default boolean flowersEvent() {
        return false;
    }

    @ConfigItem(
            keyName = "foxEvent",
            name = "Enable Fox Event",
            description = "Enable the Fox forestry event",
            position = 4,
            section = forestrySection
    )
    default boolean foxEvent() {
        return true;
    }

    @ConfigItem(
            keyName = "hivesEvent",
            name = "Enable Hives Event",
            description = "Enable the Hives forestry event",
            position = 5,
            section = forestrySection
    )
    default boolean hivesEvent() {
        return true;
    }

    @ConfigItem(
            keyName = "leprechaunEvent",
            name = "Enable Leprechaun Event",
            description = "Enable the Leprechaun forestry event",
            position = 6,
            section = forestrySection
    )
    default boolean leprechaunEvent() {
        return true;
    }

    @ConfigItem(
            keyName = "ritualEvent",
            name = "Enable Ritual Event",
            description = "Enable the Ritual forestry event",
            position = 7,
            section = forestrySection
    )
    default boolean ritualEvent() {
        return true;
    }

    @ConfigItem(
            keyName = "rootEvent",
            name = "Enable Root Event",
            description = "Enable the Root forestry event",
            position = 8,
            section = forestrySection
    )
    default boolean rootEvent() {
        return true;
    }

    @ConfigItem(
            keyName = "saplingEvent",
            name = "Enable Struggling Sapling Event",
            description = "Enable the Struggling Sapling forestry event",
            position = 9,
            section = forestrySection
    )
    default boolean saplingEvent() {
        return true;
    }
}
