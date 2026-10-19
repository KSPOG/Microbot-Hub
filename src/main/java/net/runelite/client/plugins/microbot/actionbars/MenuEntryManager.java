package net.runelite.client.plugins.microbot.actionbars;

import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.client.plugins.microbot.actionbars.bar.ActionBarManager;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import java.awt.Color;

public class MenuEntryManager {
    @Inject
    private Client client;

    @Inject
    private ActionBarManager actionBarManager;

    private boolean hasCreatedEntries;
    private Action hoveredAction;

    public void createMenuEntries() {
        if (hoveredAction == null || hasCreatedEntries) {
            return;
        }
        if (client.isKeyPressed(81)) {
            return;
        }
        SavedAction savedAction = hoveredAction.getSavedAction();
        String target = ColorUtil.wrapWithColorTag(
                savedAction.getOption() + " " + savedAction.getTarget(),
                Color.WHITE
        );

        ActionBar activeBar = actionBarManager.getActiveBar();
        client.createMenuEntry(-1)
                .setOption("Swap right")
                .setTarget(target)
                .onClick(entry -> activeBar.swapRight(hoveredAction));
        client.createMenuEntry(-1)
                .setOption("Swap left")
                .setTarget(target)
                .onClick(entry -> activeBar.swapLeft(hoveredAction));
        client.createMenuEntry(-1)
                .setOption("Unbind")
                .setTarget(target)
                .onClick(entry -> activeBar.remove(hoveredAction));
        client.createMenuEntry(-1)
                .setOption("Activate")
                .setTarget(target)
                .onClick(entry -> savedAction.invoke());
        hasCreatedEntries = true;
    }

    public void setHoveredAction(Action action) {
        this.hoveredAction = action;
    }

    public void setHasCreatedEntries(boolean hasCreatedEntries) {
        this.hasCreatedEntries = hasCreatedEntries;
    }
}
