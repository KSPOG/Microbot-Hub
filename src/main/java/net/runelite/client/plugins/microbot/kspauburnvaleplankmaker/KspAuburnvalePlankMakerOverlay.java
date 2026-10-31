package net.runelite.client.plugins.microbot.kspauburnvaleplankmaker;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;

public class KspAuburnvalePlankMakerOverlay extends OverlayPanel {
    private static final int WIDTH = 260;

    private final KspAuburnvalePlankMakerConfig config;
    private final KspAuburnvalePlankMakerScript script;

    @Inject
    public KspAuburnvalePlankMakerOverlay(
            KspAuburnvalePlankMakerPlugin plugin,
            KspAuburnvalePlankMakerConfig config,
            KspAuburnvalePlankMakerScript script
    ) {
        super(plugin);
        this.config = config;
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay()) {
            return null;
        }

        panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
        panelComponent.getChildren().add(
                TitleComponent.builder()
                        .text("Fast Planker v" + KspAuburnvalePlankMakerPlugin.VERSION)
                        .build()
        );

        add("Status", shorten(Microbot.status, 30));
        add("Plank", config.plankType().getOutputPlankName());
        add("Phase", script.getPhaseName());
        add("Runtime", formatDuration(script.getRuntimeMillis()));
        add("Planks made", formatNumber(script.getPlanksMade()));
        add("Planks / h", formatNumber(script.getPlanksPerHour()));
        add("Sawmill trips", formatNumber(script.getSawmillTrips()));
        add("GE cycles", formatNumber(script.getGeCycles()));
        add("Next world hop", formatCountdown(script.getTimeUntilWorldHopSeconds()));
        add("Buy threshold", "+" + config.buyThresholdPercent() + "%");
        add("Sell threshold", "-" + config.sellThresholdPercent() + "%");

        return super.render(graphics);
    }

    private void add(String left, String right) {
        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left(left)
                        .right(right == null ? "" : right)
                        .build()
        );
    }

    private String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private String formatNumber(long value) {
        if (value >= 1_000_000) return String.format("%.2fm", value / 1_000_000.0);
        if (value >= 1_000) return String.format("%.1fk", value / 1_000.0);
        return Long.toString(value);
    }

    private String formatCountdown(long seconds) {
        if (seconds < 0) return "Disabled";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, secs)
                : String.format("%02d:%02d", minutes, secs);
    }

    private String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(Math.max(0, millis));
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        long seconds = duration.minusHours(hours).minusMinutes(minutes).getSeconds();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
