package net.runelite.client.plugins.microbot.flippilot.engine;

import net.runelite.client.plugins.microbot.flippilot.FlipPilotConfig;
import net.runelite.client.plugins.microbot.flippilot.data.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
public class SuggestionEngine
{
    private final FlipPilotConfig config;
    private final HistoryStore historyStore;

    @Inject
    public SuggestionEngine(FlipPilotConfig config, HistoryStore historyStore)
    {
        this.config = config;
        this.historyStore = historyStore;
    }

    public List<Suggestion> build(List<ItemDef> universe,
                                  Map<Integer, LivePrice> latest,
                                  Map<Integer, Integer> vol5m)
    {
        int minMargin = config.minMargin();
        int minRoiPct = config.minRoiPct();
        int maxPrice = config.maxItemPrice();

        List<Suggestion> out = new ArrayList<>(config.topN() * 4);

        for (ItemDef d : universe)
        {
            LivePrice p = latest.get(d.id);
            if (p == null) continue;

            int high = p.high;
            int low = p.low;

            if (high <= 0 || low <= 0) continue;
            if (high > maxPrice) continue;

            int margin = Math.max(0, high - low);
            if (margin < minMargin) continue;

            double roi = (low > 0) ? (margin * 100.0 / Math.max(1, low)) : 0.0;
            if (roi < minRoiPct) continue;

            int volume = vol5m.getOrDefault(d.id, 0);

            double risk = computeRisk01(historyStore.get(d.id));

            double limitPenalty = (d.limit > 0 && d.limit < 100) ? (100 - d.limit) * 2.0 : 0.0;
            double volScore = Math.log1p(volume) * 25.0;

            double score = (margin * 1.0)
                    + (roi * 50.0)
                    + volScore
                    - (risk * 200.0)
                    - limitPenalty;

            out.add(new Suggestion(d.id, d.name, high, low, margin, roi, d.limit, volume, risk, score));
        }

        out.sort((a, b) -> Double.compare(b.score, a.score));
        if (out.size() > config.topN()) return new ArrayList<>(out.subList(0, config.topN()));
        return out;
    }

    private double computeRisk01(List<PricePoint> pts)
    {
        int n = pts.size();
        if (n < 20) return 0.25;

        int start = Math.max(0, n - 120);
        double sum = 0;
        int count = 0;
        double[] arr = new double[n - start];

        for (int i = start; i < n; i++)
        {
            PricePoint p = pts.get(i);
            double mid = (p.high + p.low) / 2.0;
            arr[i - start] = mid;
            sum += mid;
            count++;
        }

        double mean = sum / Math.max(1, count);
        if (mean <= 0) return 0.5;

        double var = 0;
        for (double v : arr)
        {
            double d = v - mean;
            var += d * d;
        }
        var /= Math.max(1, arr.length);
        double std = Math.sqrt(var);

        double cv = std / mean;
        double risk = Math.min(1.0, cv * 10.0);
        return Math.max(0.0, risk);
    }
}
