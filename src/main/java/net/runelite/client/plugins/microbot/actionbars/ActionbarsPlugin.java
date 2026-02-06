package net.runelite.client.plugins.microbot.actionbars;

import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.PostMenuSort;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.actionbars.bar.ActionBarManager;
import net.runelite.client.plugins.microbot.actionbars.overlay.ActionBarOverlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.awt.AWTException;
import java.awt.event.KeyEvent;
import java.util.Set;

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
public class ActionbarsPlugin extends Plugin {

    public static final String version = "1.2.0";

    private static final Logger log = LoggerFactory.getLogger(ActionbarsPlugin.class);
    private static final Set<String> IGNORED_ENTRIES = Set.of("Cancel", "Examine");

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KeyManager keyManager;

    @Inject
    private ActionBarManager actionBarManager;

    @Inject
    private MenuEntryManager menuEntryManager;

    @Inject
    private KeybindListener keybindListener;

    @Inject
    private ActionBarOverlay actionBarOverlay;

    @Override
    protected void startUp() throws AWTException {
        actionBarManager.startUp();
        overlayManager.add(actionBarOverlay);
        keyManager.registerKeyListener(keybindListener);
    }

    @Override
    protected void shutDown() {
        actionBarManager.shutDown();
        overlayManager.remove(actionBarOverlay);
        keyManager.unregisterKeyListener(keybindListener);
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (client.isKeyPressed(KeyCode.KC_SHIFT) && !IGNORED_ENTRIES.contains(event.getOption())) {
            MenuEntry menuEntry = event.getMenuEntry();
            client.createMenuEntry(-1)
                    .setOption("Bind action bar: " + menuEntry.getOption())
                    .setTarget(event.getTarget())
                    .setType(MenuAction.RUNELITE)
                    .onClick(clicked -> {
                        log.info("Binding action bar entry {} {}", menuEntry.getOption(), menuEntry.getTarget());
                        createAction(SavedAction.fromMenuEntry(menuEntry));
                    });
        }

        menuEntryManager.createMenuEntries();
    }

    @Subscribe
    public void onPostMenuSort(PostMenuSort event) {
        menuEntryManager.setHasCreatedEntries(false);
    }

    private void createAction(SavedAction savedAction) {
        sendChatMessage("Binding " + savedAction.getName() + ".");
        sendChatMessage("Press esc to cancel.");
        keyManager.registerKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    keyManager.unregisterKeyListener(this);
                    sendChatMessage("Cancelled.");
                    return;
                }

                if (e.getKeyCode() == KeyEvent.VK_ALT
                        || e.getKeyCode() == KeyEvent.VK_CONTROL
                        || e.getKeyCode() == KeyEvent.VK_META
                        || e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    return;
                }

                Action action = actionBarManager.addAction(savedAction, new net.runelite.client.config.Keybind(e));
                sendChatMessage("Bound " + action.getName() + " to " + action.getKeybind() + ".");
                keyManager.unregisterKeyListener(this);
            }

            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }
        });
    }

    public void sendChatMessage(String message) {
        if (!client.isClientThread()) {
            clientThread.invokeLater(() -> sendChatMessage(message));
            return;
        }
        client.addChatMessage(
                net.runelite.api.ChatMessageType.GAMEMESSAGE,
                "Action bars",
                "[Action bars] " + message,
                null
        );
    }

}
