package net.runelite.client.plugins.microbot.actionbars;

import com.google.inject.Provides;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.AWTException;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

@PluginDescriptor(
        name = PluginConstants.DEFAULT_PREFIX + "Action Bars",
        description = "Displays an RS3-style action bar overlay.",
        authors = { "Microbot" },
        version = ActionbarsPlugin.version,
        minClientVersion = "1.9.9.1",
        tags = { "overlay", "ui", "action bar" },
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class ActionbarsPlugin extends Plugin implements KeyListener {
    public static final String version = "1.1.2";

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ActionbarsOverlay actionbarsOverlay;

    @Inject
    private ActionbarsConfig config;

    @Inject
    private KeyManager keyManager;

    @Provides
    ActionbarsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ActionbarsConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(actionbarsOverlay);
        }
        keyManager.registerKeyListener(this);
    }

    @Override
    protected void shutDown() {
        keyManager.unregisterKeyListener(this);
        if (overlayManager != null) {
            overlayManager.remove(actionbarsOverlay);
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (config.nextBarHotkey().matches(e)) {
            e.consume();
            shiftActiveBar(1);
            return;
        }

        if (config.previousBarHotkey().matches(e)) {
            e.consume();
            shiftActiveBar(-1);
            return;
        }

        if (!Microbot.isLoggedIn()) {
            return;
        }

        Integer slotIndex = getSlotIndex(e);
        if (slotIndex == null) {
            return;
        }

        ActionbarsBar activeBar = ActionbarsDefinitions.resolveActiveBar(
                config.actionBars(),
                config.activeBarIndex()
        );
        ActionbarsSlot slot = activeBar.getSlot(slotIndex);
        if (slot == null || slot.getAction().getType() == ActionbarsActionType.NONE) {
            return;
        }

        e.consume();
        slot.getAction().execute();
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (!Microbot.getClient().isKeyPressed(KeyCode.KC_SHIFT)) {
            return;
        }

        if (!isItemMenu(event.getType())) {
            return;
        }

        for (int slot = 1; slot <= ActionbarsDefinitions.SLOT_COUNT; slot++) {
            String option = "Bind Action Bar Slot " + slot;
            Microbot.getClient().createMenuEntry(1)
                    .setOption(option)
                    .setTarget(event.getTarget())
                    .setParam0(event.getActionParam0())
                    .setParam1(event.getActionParam1())
                    .setIdentifier(event.getIdentifier())
                    .setType(MenuAction.RUNELITE)
                    .onClick(this::onMenuOptionClicked);
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!event.getMenuOption().startsWith("Bind Action Bar Slot ")) {
            return;
        }

        int slotNumber = parseSlotNumber(event.getMenuOption());
        if (slotNumber < 1 || slotNumber > ActionbarsDefinitions.SLOT_COUNT) {
            return;
        }

        int itemId = event.getId();
        if (itemId <= 0) {
            return;
        }

        Microbot.getConfigManager().setConfiguration(
                "actionbars",
                "slot" + slotNumber + "ItemId",
                itemId
        );
    }

    private void shiftActiveBar(int delta) {
        List<ActionbarsBar> bars = ActionbarsDefinitions.parseBars(config.actionBars());
        int currentIndex = ActionbarsDefinitions.clampActiveIndex(bars, config.activeBarIndex());
        int totalBars = bars.size();
        int nextIndex = currentIndex + delta;
        if (nextIndex < 1) {
            nextIndex = totalBars;
        } else if (nextIndex > totalBars) {
            nextIndex = 1;
        }
        ActionbarsDefinitions.updateActiveBarIndex(nextIndex);
    }

    private Integer getSlotIndex(KeyEvent e) {
        List<Keybind> keybinds = getSlotKeybinds();
        for (int i = 0; i < keybinds.size(); i++) {
            Keybind keybind = keybinds.get(i);
            if (keybind != null && keybind != Keybind.NOT_SET && keybind.matches(e)) {
                return i;
            }
        }

        switch (e.getKeyCode()) {
            case KeyEvent.VK_1:
                return 0;
            case KeyEvent.VK_2:
                return 1;
            case KeyEvent.VK_3:
                return 2;
            case KeyEvent.VK_4:
                return 3;
            case KeyEvent.VK_5:
                return 4;
            case KeyEvent.VK_6:
                return 5;
            case KeyEvent.VK_7:
                return 6;
            case KeyEvent.VK_8:
                return 7;
            case KeyEvent.VK_9:
                return 8;
            case KeyEvent.VK_0:
                return 9;
            case KeyEvent.VK_MINUS:
                return 10;
            case KeyEvent.VK_EQUALS:
                return 11;
            default:
                return null;
        }
    }

    private List<Keybind> getSlotKeybinds() {
        List<Keybind> keybinds = new ArrayList<>();
        keybinds.add(config.slot1Key());
        keybinds.add(config.slot2Key());
        keybinds.add(config.slot3Key());
        keybinds.add(config.slot4Key());
        keybinds.add(config.slot5Key());
        keybinds.add(config.slot6Key());
        keybinds.add(config.slot7Key());
        keybinds.add(config.slot8Key());
        keybinds.add(config.slot9Key());
        keybinds.add(config.slot10Key());
        keybinds.add(config.slot11Key());
        keybinds.add(config.slot12Key());
        return keybinds;
    }

    private boolean isItemMenu(MenuAction action) {
        return action == MenuAction.ITEM_FIRST_OPTION;
    }

    private int parseSlotNumber(String option) {
        String[] parts = option.split(" ");
        if (parts.length == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
