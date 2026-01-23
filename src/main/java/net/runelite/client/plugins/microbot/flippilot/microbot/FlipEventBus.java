package net.runelite.client.plugins.microbot.flippilot.microbot;

import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
public class FlipEventBus
{
    private final List<FlipEvent> events = new CopyOnWriteArrayList<>();
    private volatile long sessionProfit = 0;

    public void publish(FlipEvent e)
    {
        events.add(e);
        sessionProfit += e.profit;

        if (events.size() > 2000)
        {
            events.subList(0, 500).clear();
        }
    }

    public long getSessionProfit()
    {
        return sessionProfit;
    }

    public List<FlipEvent> getRecent(int max)
    {
        int size = events.size();
        if (size <= max) return new ArrayList<>(events);
        return new ArrayList<>(events.subList(size - max, size));
    }

    public void resetSession()
    {
        events.clear();
        sessionProfit = 0;
    }
}
