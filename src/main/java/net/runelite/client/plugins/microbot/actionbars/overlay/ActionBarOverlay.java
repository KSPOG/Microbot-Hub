package net.runelite.client.plugins.microbot.actionbars.overlay;

import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.actionbars.Action;
import net.runelite.client.plugins.microbot.actionbars.ActionBar;
import net.runelite.client.plugins.microbot.actionbars.MenuEntryManager;
import net.runelite.client.plugins.microbot.actionbars.bar.ActionBarManager;
import net.runelite.client.ui.FontManager;

import net.runelite.client.plugins.microbot.actionbars.ActionbarsPlugin;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;


import net.runelite.client.ui.overlay.OverlayPanel;

import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class ActionBarOverlay extends OverlayPanel {
    private static final ImageComponent PLACEHOLDER_IMAGE =
            new ImageComponent(new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB));

    @Inject

    public ActionBarOverlay(ActionbarsPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.LOW);
        setMovable(true);
    }

    @Inject
    private ActionBarManager actionBarManager;

    @Inject
    private MenuEntryManager menuEntryManager;

    private List<ActionButtonComponent> actionButtons = new ArrayList<>();

    @Override
    public Dimension render(java.awt.Graphics2D graphics) {

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(ActionBar.NUM_ACTIONS * 36, 32));

        Action[] actions = actionBarManager.getActiveBar().getActions();
        actionButtons = new ArrayList<>();

        FontMetrics metrics = graphics.getFontMetrics(FontManager.getRunescapeBoldFont());
        String label = "Bar " + (actionBarManager.getActiveBarIndex() + 1)
                + "/" + actionBarManager.getActionBars().size();
        panelComponent.getChildren().add(LineComponent.builder()
                .left(label)
                .leftColor(Color.CYAN)
                .leftFont(FontManager.getRunescapeBoldFont())
                .preferredSize(new Dimension(metrics.stringWidth(label), 0))
                .build());

        for (int i = 0; i < ActionBar.NUM_ACTIONS; i++) {
            Action action = actions[i];
            if (action == null) {
                panelComponent.getChildren().add(PLACEHOLDER_IMAGE);
                continue;
            }
            ActionButtonComponent component = new ActionButtonComponent(action);
            panelComponent.getChildren().add(component);
            actionButtons.add(component);
        }

        return super.render(graphics);
    }

    public void onMouseOver() {
        Point mouse = Microbot.getClient().getMouseCanvasPosition();
        for (ActionButtonComponent button : actionButtons) {
            Rectangle bounds = button.getBounds();
            int x = bounds.x + getBounds().x;
            int y = bounds.y + getBounds().y;
            if (mouse.getX() <= x) {
                continue;
            }
            if (mouse.getX() >= x + bounds.getWidth()) {
                continue;
            }
            if (mouse.getY() <= y) {
                continue;
            }
            if (mouse.getY() >= y + bounds.getHeight()) {
                continue;
            }
            menuEntryManager.setHoveredAction(button.getAction());
            return;
        }
        menuEntryManager.setHoveredAction(null);
    }
}
