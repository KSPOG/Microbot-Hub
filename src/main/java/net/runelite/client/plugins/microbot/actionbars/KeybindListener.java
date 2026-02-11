package net.runelite.client.plugins.microbot.actionbars;

import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;
import net.runelite.client.plugins.microbot.actionbars.bar.ActionBarManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

public class KeybindListener implements KeyListener {
    private static final Logger log = LoggerFactory.getLogger(KeybindListener.class);

    @Inject
    private ActionBarManager actionBarManager;

    private final Map<Action, Boolean> isPressed = new HashMap<>();
    private final Map<Action, Boolean> isConsumingTyped = new HashMap<>();

    @Override
    public boolean isEnabledOnLoginScreen() {
        return false;
    }

    @Override
    public void keyTyped(KeyEvent e) {
        if (isConsumingTyped.containsValue(Boolean.TRUE)) {
            e.consume();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        Action[] actions = actionBarManager.getActiveActions();

        if (actions == null) {
            return;
        }

        for (Action action : actions) {
            if (action == null) {
                continue;
            }
            if (!action.getKeybind().matches(e)) {
                continue;
            }
            boolean alreadyPressed = isPressed.getOrDefault(action, Boolean.FALSE);
            isPressed.put(action, Boolean.TRUE);
            if (!alreadyPressed) {
                action.invoke();
            }
            Integer modifier = Keybind.getModifierForKeyCode(e.getKeyCode());
            if (modifier == null) {
                isConsumingTyped.put(action, Boolean.TRUE);
                e.consume();
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        Action[] actions = actionBarManager.getActiveActions();

        if (actions == null) {
            return;
        }
        for (Action action : actions) {
            if (action == null) {
                continue;
            }
            if (!action.getKeybind().matches(e)) {
                continue;
            }
            isPressed.remove(action);
            isConsumingTyped.remove(action);
        }
    }
}
