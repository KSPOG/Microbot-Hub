package net.runelite.client.plugins.microbot.flippilot.alerts;

import net.runelite.client.plugins.microbot.flippilot.FlipPilotConfig;
import net.runelite.client.plugins.microbot.flippilot.engine.Suggestion;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class AlertManager
{
    private final FlipPilotConfig config;
    private final ConcurrentHashMap<Integer, Long> lastAlertMs = new ConcurrentHashMap<>();

    @Inject
    public AlertManager(FlipPilotConfig config)
    {
        this.config = config;
    }

    public boolean shouldAlert(Suggestion s)
    {
        if (!config.alertsEnabled()) return false;

        if (s.margin < config.alertMinMargin()) return false;
        if (s.roiPct < config.alertMinRoiPct()) return false;
        if (s.risk > config.alertMaxRisk()) return false;
        if (s.vol5m < config.alertMinVol5m()) return false;

        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(10, config.alertCooldownSeconds()) * 1000L;
        Long prev = lastAlertMs.get(s.itemId);
        if (prev != null && (now - prev) < cooldownMs) return false;

        lastAlertMs.put(s.itemId, now);
        return true;
    }
}
