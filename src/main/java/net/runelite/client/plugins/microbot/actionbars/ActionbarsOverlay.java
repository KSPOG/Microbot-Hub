package net.runelite.client.plugins.microbot.actionbars;

import net.runelite.api.Client;

import net.runelite.client.config.Keybind;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

public class ActionbarsOverlay extends Overlay {
    private static final int SLOT_SIZE = 32;
    private static final int SLOT_GAP = 6;
    private static final int BAR_PADDING = 10;
    private static final int BAR_CORNER_RADIUS = 16;
    private static final int LABEL_HEIGHT = 12;
    private static final int BOTTOM_MARGIN = 48;
    private static final String[] KEY_LABELS = {
            "1", "2", "3", "4", "5", "6",
            "7", "8", "9", "0", "-", "="
    };

    private final Client client;
    private final ActionbarsConfig config;

    @Inject
    public ActionbarsOverlay(Client client, ActionbarsConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.LOW);
        setMovable(true);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        int canvasWidth = client.getCanvasWidth();
        int canvasHeight = client.getCanvasHeight();
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            return null;
        }

        int barWidth = ActionbarsDefinitions.SLOT_COUNT * SLOT_SIZE
                + (ActionbarsDefinitions.SLOT_COUNT - 1) * SLOT_GAP + BAR_PADDING * 2;
        int barHeight = SLOT_SIZE + BAR_PADDING * 2 + LABEL_HEIGHT;
        int x = Math.max(0, (canvasWidth - barWidth) / 2);
        int y = Math.max(0, canvasHeight - barHeight - BOTTOM_MARGIN);

        List<ActionbarsBar> bars = ActionbarsDefinitions.parseBars(config.actionBars());
        int activeIndex = ActionbarsDefinitions.clampActiveIndex(bars, config.activeBarIndex());
        ActionbarsBar activeBar = bars.get(activeIndex - 1);

        Object antialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object textAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Shape barShape = new RoundRectangle2D.Float(x, y, barWidth, barHeight, BAR_CORNER_RADIUS, BAR_CORNER_RADIUS);
        GradientPaint barGradient = new GradientPaint(
                x,
                y,
                new Color(20, 23, 28, 230),
                x,
                y + barHeight,
                new Color(6, 8, 12, 230)
        );
        graphics.setPaint(barGradient);
        graphics.fill(barShape);
        graphics.setColor(new Color(90, 96, 104, 200));
        graphics.setStroke(new BasicStroke(1.4f));
        graphics.draw(barShape);

        int slotY = y + BAR_PADDING;
        int slotX = x + BAR_PADDING;
        Font prevFont = graphics.getFont();
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 10));
        for (int i = 0; i < ActionbarsDefinitions.SLOT_COUNT; i++) {
            int currentX = slotX + i * (SLOT_SIZE + SLOT_GAP);
            drawSlot(graphics, currentX, slotY, SLOT_SIZE, SLOT_SIZE);
            ActionbarsSlot slot = activeBar.getSlot(i);
            String label = slot == null ? "" : slot.getLabel();
            if (label.isBlank() && slot != null) {
                label = slot.getAction().getFallbackLabel();
            }
            drawSlotLabel(graphics, currentX, slotY + SLOT_SIZE - 10, label);

            drawKeyLabel(graphics, currentX, slotY + SLOT_SIZE + LABEL_HEIGHT - 2, getKeyLabel(i));

            drawKeyLabel(graphics, currentX, slotY + SLOT_SIZE + LABEL_HEIGHT - 2, KEY_LABELS[i]);

        }
        drawBarIndicator(graphics, x + 8, y + 14, activeIndex, bars.size());
        graphics.setFont(prevFont);

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, textAntialiasing);
        return new Dimension(barWidth, barHeight);
    }

    private void drawSlot(Graphics2D graphics, int x, int y, int width, int height) {
        Shape slotShape = new RoundRectangle2D.Float(x, y, width, height, 8, 8);
        GradientPaint slotGradient = new GradientPaint(
                x,
                y,
                new Color(54, 60, 70, 220),
                x,
                y + height,
                new Color(24, 28, 34, 220)
        );
        graphics.setPaint(slotGradient);
        graphics.fill(slotShape);

        graphics.setColor(new Color(140, 148, 160, 200));
        graphics.setStroke(new BasicStroke(1.1f));
        graphics.draw(slotShape);

        graphics.setColor(new Color(255, 255, 255, 25));
        graphics.drawLine(x + 3, y + 4, x + width - 4, y + 4);
    }

    private void drawKeyLabel(Graphics2D graphics, int centerX, int baselineY, String label) {

        if (label == null || label.isBlank()) {
            return;
        }
        int maxWidth = SLOT_SIZE - 4;
        String clipped = label;
        while (!clipped.isEmpty() && graphics.getFontMetrics().stringWidth(clipped) > maxWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        label = clipped;

        int textWidth = graphics.getFontMetrics().stringWidth(label);
        int textX = centerX + (SLOT_SIZE - textWidth) / 2;
        graphics.setColor(new Color(205, 210, 220, 200));
        graphics.drawString(label, textX, baselineY);
    }

    private void drawSlotLabel(Graphics2D graphics, int centerX, int baselineY, String label) {
        if (label == null || label.isBlank()) {
            return;
        }
        int maxWidth = SLOT_SIZE - 6;
        String clipped = label;
        while (!clipped.isEmpty() && graphics.getFontMetrics().stringWidth(clipped) > maxWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        int textWidth = graphics.getFontMetrics().stringWidth(clipped);
        int textX = centerX + (SLOT_SIZE - textWidth) / 2;
        graphics.setColor(new Color(220, 225, 235, 220));
        graphics.drawString(clipped, textX, baselineY);
    }

    private void drawBarIndicator(Graphics2D graphics, int x, int y, int activeIndex, int totalBars) {
        String text = "Bar " + activeIndex + "/" + totalBars;
        graphics.setColor(new Color(180, 188, 200, 200));
        graphics.drawString(text, x, y);
    }


    private String getKeyLabel(int index) {
        Keybind keybind = getKeybindForIndex(index);
        if (keybind != null && keybind != Keybind.NOT_SET) {
            return keybind.toString();
        }
        if (index >= 0 && index < KEY_LABELS.length) {
            return KEY_LABELS[index];
        }
        return "";
    }

    private Keybind getKeybindForIndex(int index) {
        switch (index) {
            case 0:
                return config.slot1Key();
            case 1:
                return config.slot2Key();
            case 2:
                return config.slot3Key();
            case 3:
                return config.slot4Key();
            case 4:
                return config.slot5Key();
            case 5:
                return config.slot6Key();
            case 6:
                return config.slot7Key();
            case 7:
                return config.slot8Key();
            case 8:
                return config.slot9Key();
            case 9:
                return config.slot10Key();
            case 10:
                return config.slot11Key();
            case 11:
                return config.slot12Key();
            default:
                return Keybind.NOT_SET;
        }
    }

}
