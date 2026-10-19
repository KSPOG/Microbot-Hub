package net.runelite.client.plugins.microbot.flippilot.ui;

import net.runelite.client.plugins.microbot.flippilot.data.PricePoint;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class PriceChartPanel extends JPanel
{
    private volatile List<PricePoint> points = Collections.emptyList();
    private volatile int hoverIndex = -1;

    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    public PriceChartPanel()
    {
        setPreferredSize(new Dimension(10, 180));
        ToolTipManager.sharedInstance().registerComponent(this);

        addMouseMotionListener(new MouseMotionAdapter()
        {
            @Override
            public void mouseMoved(MouseEvent e)
            {
                List<PricePoint> pts = points;
                if (pts.size() < 2)
                {
                    hoverIndex = -1;
                    repaint();
                    return;
                }

                int w = getWidth();
                int padL = 48, padR = 12;
                int plotW = Math.max(1, w - padL - padR);

                double t = (e.getX() - padL) / (double) plotW;
                t = Math.max(0, Math.min(1, t));

                int idx = (int) Math.round(t * (pts.size() - 1));
                hoverIndex = Math.max(0, Math.min(pts.size() - 1, idx));
                repaint();
            }
        });
    }

    public void setPoints(List<PricePoint> pts)
    {
        points = (pts == null) ? Collections.emptyList() : pts;
        hoverIndex = -1;
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent event)
    {
        List<PricePoint> pts = points;
        if (hoverIndex < 0 || hoverIndex >= pts.size()) return null;

        PricePoint p = pts.get(hoverIndex);
        int mid = (p.high + p.low) / 2;
        return sdf.format(new Date(p.t)) + "  mid=" + mid + " (H " + p.high + " / L " + p.low + ")";
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        List<PricePoint> pts = points;
        if (pts.size() < 2) return;

        int w = getWidth();
        int h = getHeight();

        int padL = 48, padR = 12, padT = 10, padB = 28;

        int plotX = padL;
        int plotY = padT;
        int plotW = Math.max(1, w - padL - padR);
        int plotH = Math.max(1, h - padT - padB);

        // compute min/max on mid
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (PricePoint p : pts)
        {
            int mid = (p.high + p.low) / 2;
            min = Math.min(min, mid);
            max = Math.max(max, mid);
        }
        if (min == max) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // axes
        g2.drawRect(plotX, plotY, plotW, plotH);

        // y labels (min/max)
        g2.drawString(String.valueOf(max), 6, plotY + 12);
        g2.drawString(String.valueOf(min), 6, plotY + plotH);

        // line
        int n = pts.size();
        int prevX = -1, prevY = -1;

        int minIdx = 0, maxIdx = 0;
        for (int i = 0; i < n; i++)
        {
            int mid = (pts.get(i).high + pts.get(i).low) / 2;
            if (mid == min) minIdx = i;
            if (mid == max) maxIdx = i;
        }

        for (int i = 0; i < n; i++)
        {
            PricePoint p = pts.get(i);
            int mid = (p.high + p.low) / 2;

            int x = plotX + (int) Math.round(plotW * (i / (double)(n - 1)));
            double norm = (mid - min) / (double)(max - min);
            int y = plotY + plotH - (int) Math.round(plotH * norm);

            if (prevX != -1)
            {
                g2.drawLine(prevX, prevY, x, y);
            }
            prevX = x;
            prevY = y;
        }

        // min/max markers
        drawMarker(g2, pts, minIdx, plotX, plotY, plotW, plotH, min, max, min, "MIN");
        drawMarker(g2, pts, maxIdx, plotX, plotY, plotW, plotH, min, max, max, "MAX");

        // hover crosshair
        if (hoverIndex >= 0 && hoverIndex < n)
        {
            int x = plotX + (int) Math.round(plotW * (hoverIndex / (double)(n - 1)));
            g2.drawLine(x, plotY, x, plotY + plotH);
        }

        // time labels (first/last)
        long t0 = pts.get(0).t;
        long t1 = pts.get(n - 1).t;
        g2.drawString(sdf.format(new Date(t0)), plotX, plotY + plotH + 18);
        String end = sdf.format(new Date(t1));
        int endW = g2.getFontMetrics().stringWidth(end);
        g2.drawString(end, plotX + plotW - endW, plotY + plotH + 18);

        g2.dispose();
    }

    private void drawMarker(Graphics2D g2, List<PricePoint> pts, int idx,
                            int plotX, int plotY, int plotW, int plotH,
                            int min, int max, int value, String label)
    {
        int n = pts.size();
        int x = plotX + (int) Math.round(plotW * (idx / (double)(n - 1)));
        double norm = (value - min) / (double)(max - min);
        int y = plotY + plotH - (int) Math.round(plotH * norm);

        g2.fillOval(x - 3, y - 3, 6, 6);
        g2.drawString(label, x + 6, y - 6);
    }
}
