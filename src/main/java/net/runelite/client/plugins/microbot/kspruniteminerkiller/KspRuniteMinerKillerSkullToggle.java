package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import java.awt.Rectangle;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

/**
 * Toggle only the PK Skull Prevention checkbox in the compact Settings tab.
 * The RuneLite widget tree and its bounds must be inspected on the client thread.
 * This intentionally does not write the setting's varbit locally: the game
 * setting changes only after a real widget click.
 */
final class KspRuniteMinerKillerSkullToggle {
    private static final int SETTINGS_GROUP = 116;
    private static final int SKULL_SETTING_CHILD = 5;
    private static final int CHECKBOX_INDEX = 1; // SettingsSide.SKULL_PREVENTION [1]

    private KspRuniteMinerKillerSkullToggle() { }

    static boolean requestDisable() {
        if (Microbot.getVarbitValue(VarbitID.SKULL_PREVENT_ENABLED) == 0) return true;
        if (!Rs2Tab.switchTo(InterfaceTab.SETTINGS)) return false;

        Rectangle checkboxBounds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget setting = Microbot.getClient().getWidget(SETTINGS_GROUP, SKULL_SETTING_CHILD);
            if (setting == null || setting.isHidden()) return null;

            Widget[] children = setting.getDynamicChildren();
            if (children == null || children.length <= CHECKBOX_INDEX) return null;
            Widget checkbox = children[CHECKBOX_INDEX];
            if (checkbox == null || checkbox.isHidden()) return null;

            Rectangle bounds = checkbox.getBounds();
            return bounds != null && bounds.width > 0 && bounds.height > 0
                    ? new Rectangle(bounds) : null;
        }).orElse(null);
        if (checkboxBounds == null) return false;

        // The mouse API receives a value snapshot, never a live Widget instance.
        Microbot.click(checkboxBounds);
        // FeroxPrep.WAIT_SKULL_OFF independently verifies the authoritative varbit.
        return true;
    }
}
