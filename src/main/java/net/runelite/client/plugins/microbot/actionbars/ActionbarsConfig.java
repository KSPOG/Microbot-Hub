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


    @ConfigSection(
            name = "Slot Keybinds",
            description = "Customize hotkeys for each action bar slot",
            position = 1
    )
    String slotKeysSection = "slotKeysSection";


    @ConfigSection(
            name = "Slot Icons",
            description = "Bind inventory items to show as slot icons",
            position = 2
    )
    String slotIconsSection = "slotIconsSection";


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


    @ConfigItem(
            keyName = "slot1Key",
            name = "Slot 1 key",
            description = "Keybind for slot 1",
            position = 0,
            section = slotKeysSection
    )
    default Keybind slot1Key() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "slot2Key",
            name = "Slot 2 key",
            description = "Keybind for slot 2",
            position = 1,
            section = slotKeysSection
    )
    default Keybind slot2Key() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "slot3Key",
            name = "Slot 3 key",
            description = "Keybind for slot 3",
            position = 2,
            section = slotKeysSection
    )
    default Keybind slot3Key() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "slot4Key",
            name = "Slot 4 key",
            description = "Keybind for slot 4",
            position = 3,
            section = slotKeysSection
    )
    default Keybind slot4Key() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "slot5Key",
            name = "Slot 5 key",
            description = "Keybind for slot 5",
            position = 4,
            section = slotKeysSection
    )
    default Keybind slot5Key() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "slot6Key",
            name = "Slot 6 key",
            description = "Keybind for slot 6",
            position = 5,
            section = slotKeysSection
    )
    default Keybind slot6Key() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "slot7Key",
            name = "Slot 7 key",
            description = "Keybind for slot 7",
            position = 6,
            section = slotKeysSection
    )
    default Keybind slot7Key() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "slot8Key",
            name = "Slot 8 key",
            description = "Keybind for slot 8",
            position = 7,
            section = slotKeysSection
    )
    default Keybind slot8Key() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "slot9Key",
            name = "Slot 9 key",
            description = "Keybind for slot 9",
            position = 8,
            section = slotKeysSection
    )
    default Keybind slot9Key() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "slot10Key",
            name = "Slot 10 key",
            description = "Keybind for slot 10",
            position = 9,
            section = slotKeysSection
    )
    default Keybind slot10Key() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "slot11Key",
            name = "Slot 11 key",
            description = "Keybind for slot 11",
            position = 10,
            section = slotKeysSection
    )
    default Keybind slot11Key() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "slot12Key",
            name = "Slot 12 key",
            description = "Keybind for slot 12",
            position = 11,
            section = slotKeysSection
    )
    default Keybind slot12Key() {
        return Keybind.NOT_SET;
    }


    @ConfigItem(
            keyName = "slot1ItemId",
            name = "Slot 1 item",
            description = "Item id for slot 1",
            position = 0,
            section = slotIconsSection
    )
    default int slot1ItemId() {
        return -1;
    }

    @ConfigItem(
            keyName = "slot2ItemId",
            name = "Slot 2 item",
            description = "Item id for slot 2",
            position = 1,
            section = slotIconsSection
    )
    default int slot2ItemId() {
        return -1;
    }

    @ConfigItem(
            keyName = "slot3ItemId",
            name = "Slot 3 item",
            description = "Item id for slot 3",
            position = 2,
            section = slotIconsSection
    )
    default int slot3ItemId() {
        return -1;
    }

    @ConfigItem(
            keyName = "slot4ItemId",
            name = "Slot 4 item",
            description = "Item id for slot 4",
            position = 3,
            section = slotIconsSection
    )
    default int slot4ItemId() {
        return -1;
    }

    @ConfigItem(
            keyName = "slot5ItemId",
            name = "Slot 5 item",
            description = "Item id for slot 5",
            position = 4,
            section = slotIconsSection
    )
    default int slot5ItemId() {
        return -1;
    }

    @ConfigItem(
            keyName = "slot6ItemId",
            name = "Slot 6 item",
            description = "Item id for slot 6",
            position = 5,
            section = slotIconsSection
    )
    default int slot6ItemId() {
        return -1;
    }

    @ConfigItem(
            keyName = "slot7ItemId",
            name = "Slot 7 item",
            description = "Item id for slot 7",
            position = 6,
            section = slotIconsSection
    )
    default int slot7ItemId() {
        return -1;
    }

    @ConfigItem(
            keyName = "slot8ItemId",
            name = "Slot 8 item",
            description = "Item id for slot 8",
            position = 7,
            section = slotIconsSection
    )
    default int slot8ItemId() {
        return -1;
    }

    @ConfigItem(
            keyName = "slot9ItemId",
            name = "Slot 9 item",
            description = "Item id for slot 9",
            position = 8,
            section = slotIconsSection
    )
    default int slot9ItemId() {
        return -1;
    }

    @ConfigItem(
            keyName = "slot10ItemId",
            name = "Slot 10 item",
            description = "Item id for slot 10",
            position = 9,
            section = slotIconsSection
    )
    default int slot10ItemId() {
        return -1;
    }

    @ConfigItem(
            keyName = "slot11ItemId",
            name = "Slot 11 item",
            description = "Item id for slot 11",
            position = 10,
            section = slotIconsSection
    )
    default int slot11ItemId() {
        return -1;
    }

    @ConfigItem(
            keyName = "slot12ItemId",
            name = "Slot 12 item",
            description = "Item id for slot 12",
            position = 11,
            section = slotIconsSection
    )
    default int slot12ItemId() {
        return -1;
    }
}
