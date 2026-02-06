package net.runelite.client.plugins.microbot.actionbars.overlay;

import net.runelite.client.plugins.microbot.actionbars.Action;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.TextComponent;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public class ActionButtonComponent implements LayoutableRenderableEntity {
    private final Action action;
    private final Rectangle bounds = new Rectangle();
    private Point preferredLocation = new Point();

    public ActionButtonComponent(Action action) {
        this.action = action;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        BufferedImage image = action.getImage();
        if (image != null) {
            graphics.drawImage(image, preferredLocation.x, preferredLocation.y, null);
        }

        TextComponent text = new TextComponent();
        FontMetrics metrics = graphics.getFontMetrics();
        String[] lines = lineBreak(action.getKeybind().toString(), metrics);
        int lineHeight = metrics.getHeight();
        int totalHeight = lines.length * lineHeight;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            text.setText(line);
            text.setPosition(new Point(
                    preferredLocation.x + 36 - metrics.stringWidth(line),
                    preferredLocation.y + 32 - totalHeight + (i + 1) * lineHeight
            ));
            text.render(graphics);
        }

        Dimension size = new Dimension(36, 32);
        bounds.setLocation(preferredLocation);
        bounds.setSize(size);
        return size;
    }

    @Override
    public void setPreferredSize(Dimension dimension) {
    }

    private String[] lineBreak(String text, FontMetrics metrics) {
        String[] lines = new String[]{text};
        int wraps = 0;
        while (mustWrap(lines, metrics) && wraps < 3) {
            String[] previous = lines.clone();
            String last = previous[previous.length - 1];
            String[] split = last.split("(?!^)\\+", 2);
            if (split.length < 2) {
                return lines;
            }
            lines = new String[previous.length + 1];
            System.arraycopy(previous, 0, lines, 0, previous.length - 1);
            lines[lines.length - 2] = split[0];
            lines[lines.length - 1] = split[1] + "+";
            wraps++;
        }
        return lines;
    }

    private boolean mustWrap(String[] lines, FontMetrics metrics) {
        return Arrays.stream(lines)
                .anyMatch(line -> metrics.stringWidth(line) >= 36);
    }

    @Override
    public void setPreferredLocation(Point point) {
        this.preferredLocation = point;
    }

    public Action getAction() {
        return action;
    }

    public Rectangle getBounds() {
        return bounds;
    }
}
