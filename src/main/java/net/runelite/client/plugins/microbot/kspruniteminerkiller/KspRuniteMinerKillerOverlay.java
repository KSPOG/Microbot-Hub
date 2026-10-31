package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A Java2D-only overlay. No Lombok builders or component factories are invoked,
 * avoiding binary linkage failures across RuneLite/Microbot client builds.
 */
public class KspRuniteMinerKillerOverlay extends OverlayPanel
{
    private static final int WIDTH = 278;
    private static final int HEIGHT = 259;
    private static final Color LABEL = new Color(177, 189, 207);
    private static final Color VALUE = new Color(243, 246, 253);
    private static final Color ACCENT = new Color(207, 161, 244);
    private static final Font HEADING_FONT = new Font("SansSerif", Font.BOLD, 12);
    private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font VALUE_FONT = new Font("SansSerif", Font.BOLD, 12);

    private final KspRuniteMinerKillerPlugin plugin;

    @Inject
    KspRuniteMinerKillerOverlay(KspRuniteMinerKillerPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        KspRuniteMinerKillerScript script = plugin.getScript();
        if (script == null)
        {
            return null;
        }

        // Do not alter the caller's transform, clip, font, paint, or stroke.
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Paint background = new GradientPaint(0, 0, new Color(11, 17, 31, 245),
                    WIDTH, HEIGHT, new Color(19, 12, 32, 245));
            g.setPaint(background);
            g.fillRoundRect(0, 0, WIDTH - 1, HEIGHT - 1, 12, 12);
            g.setPaint(new Color(130, 96, 169, 215));
            g.setStroke(new BasicStroke(1.0f));
            g.drawRoundRect(0, 0, WIDTH - 1, HEIGHT - 1, 12, 12);
            g.setColor(ACCENT);
            g.fillRoundRect(12, 13, 4, 20, 3, 3);

            g.setFont(HEADING_FONT);
            g.setColor(new Color(241, 219, 255));
            g.drawString("KSP RUNITE HUNTER", 24, 28);
            g.setFont(LABEL_FONT);
            g.setColor(new Color(172, 149, 196));
            g.drawString("v0.1.17", WIDTH - 55, 28);
            g.setColor(new Color(82, 73, 103));
            g.drawLine(12, 42, WIDTH - 13, 42);

            int y = 63;
            y = line(g, y, "Status", script.status());
            y = line(g, y, "Target", script.target());
            y = line(g, y, "Spell", script.spellName());
            y = line(g, y, "Deaths seen", Integer.toString(script.confirmedDeaths()));
            y = line(g, y, "Looted", script.lootItems() + " stacks");
            y = line(g, y, "Loot value", gp(script.lootValue()) + " GP");
            y = line(g, y, "Loot GP/h", gp(script.lootValuePerHour()) + " GP");
            y = line(g, y, "Banked", gp(script.bankedValue()) + " GP");
            y = line(g, y, "Bank trips", Integer.toString(script.bankTrips()));
            line(g, y, "Runtime", runtime(script.runtimeMs()));
        }
        finally
        {
            g.dispose();
        }
        return new Dimension(WIDTH, HEIGHT);
    }

    private static int line(Graphics2D g, int y, String left, String right)
    {
        String label = left == null ? "" : left;
        String value = right == null ? "—" : right;
        g.setFont(LABEL_FONT);
        g.setColor(LABEL);
        g.drawString(label, 14, y);

        g.setFont(VALUE_FONT);
        // Do not let long states or player names spill out of the overlay.
        int maxWidth = WIDTH - 118;
        while (value.length() > 1 && g.getFontMetrics().stringWidth(value) > maxWidth)
        {
            value = value.substring(0, value.length() - 2) + "…";
        }
        g.setColor(VALUE);
        int x = Math.max(115, WIDTH - 14 - g.getFontMetrics().stringWidth(value));
        g.drawString(value, x, y);
        return y + 19;
    }

    private static String gp(long value)
    {
        return String.format("%,d", value);
    }

    private static String runtime(long ms)
    {
        long sec = Math.max(0L, ms) / 1000L;
        return String.format("%02d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60);
    }
}
