package net.runelite.client.plugins.microbot.actionbars;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup("actionbars")
public interface ActionbarsConfig extends Config {
    @ConfigSection(
            name = "Action Bars",
            description = "Configure action bars and hotkeys",
            position = 0
    )
    String actionBarsSection = "actionBarsSection";

    @ConfigItem(
            keyName = "activeBarIndex",
            name = "Active bar (1-based)",
            description = "Which action bar line is active",
            position = 0,
            section = actionBarsSection
    )
    default int activeBarIndex() {
        return 1;
    }

    @ConfigItem(
            keyName = "nextBarHotkey",
            name = "Next bar hotkey",
            description = "Switch to the next action bar",
            position = 1,
            section = actionBarsSection
    )
    default Keybind nextBarHotkey() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "previousBarHotkey",
            name = "Previous bar hotkey",
            description = "Switch to the previous action bar",
            position = 2,
            section = actionBarsSection
    )
    default Keybind previousBarHotkey() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "actionBars",
            name = "Action bar definitions",
            description = "One bar per line. Each line has 12 slots separated by |. Each slot is label=TYPE "
                    + "or label=TYPE:payload. Types: NONE, EAT_FOOD, EAT_FAST_FOOD, DRINK_PRAYER_POTION, "
                    + "TOGGLE_SPEC, TOGGLE_PRAYER. Example: Eat=EAT_FOOD|Spec=TOGGLE_SPEC|Piety=TOGGLE_PRAYER:PIETY",
            position = 3,
            section = actionBarsSection
    )
    default String actionBars() {
        return "Eat=EAT_FOOD|Spec=TOGGLE_SPEC|Piety=TOGGLE_PRAYER:PIETY|Protect=TOGGLE_PRAYER:PROTECT_MELEE"
                + "|Range=TOGGLE_PRAYER:PROTECT_RANGE|Mage=TOGGLE_PRAYER:PROTECT_MAGIC"
                + "|Quick=TOGGLE_PRAYER:THICK_SKIN|Food=EAT_FAST_FOOD|Drink=DRINK_PRAYER_POTION"
                + "|---=NONE|---=NONE|---=NONE";
    }
}
