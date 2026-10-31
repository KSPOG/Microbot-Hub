package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import java.util.Arrays;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.api.Actor;
import net.runelite.api.Player;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.settings.Rs2Settings;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** Configure settings at Ferox, and attack using the already-selected autocast.
 * Never mutate skull state through a local varbit setter: the game needs a UI action.
 */
final class KspRuniteMinerKillerCombatSetup {
    private static final int SETTINGS_CLICKABLE = 8781844; // Rs2Settings SETTINGS_CLICKABLE
    private KspRuniteMinerKillerCombatSetup() { }

    static boolean skullPreventionDisabled() {
        return Microbot.getVarbitValue(VarbitID.SKULL_PREVENT_ENABLED) == 0;
    }

    static boolean autocastReady(Rs2CombatSpells spell) {
        return spell != null && Rs2Magic.getCurrentAutoCastSpell() == spell
                && Microbot.getVarbitValue(VarbitID.AUTOCAST_DEFMODE) == 0;
    }

    static boolean ready(Rs2CombatSpells spell) {
        return skullPreventionDisabled() && autocastReady(spell);
    }

    static boolean selectAutocast(Rs2CombatSpells spell) {
        return spell != null && Rs2Combat.setAutoCastSpell(spell, false);
    }

    static boolean requestDisableSkullPrevention() {
        // All widget tree inspection is performed on the Microbot client thread.
        return KspRuniteMinerKillerSkullToggle.requestDisable();
    }

    static void closeSettings() {
        if (Rs2Widget.isWidgetVisible(InterfaceID.Settings.UNIVERSE))
            Rs2Widget.clickWidget(InterfaceID.Settings.CLOSE);
    }

    /** Called only on a normal attack attempt; never opens the spellbook. */
    static boolean attack(net.runelite.client.plugins.microbot.util.magic.Spell spell, Actor target) {
        if (!(spell instanceof Rs2CombatSpells) || !(target instanceof Player)) return false;
        if (!ready((Rs2CombatSpells) spell)) return false;
        Actor interacting = Rs2Player.getInteracting();
        if (interacting == target) return true; // Already autocasting this target: no spam clicks.
        return Rs2Player.attack(new Rs2PlayerModel((Player) target));
    }
}
