package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.concurrent.TimeUnit;

public class KSPAccountBuilderOverlay extends OverlayPanel
{
    private final KspAccountBuilderScript script;
    private final KspAccountBuilderConfig config;

    @Inject
    public KSPAccountBuilderOverlay(KspAccountBuilderPlugin plugin, KspAccountBuilderScript script, KspAccountBuilderConfig config)
    {
        super(plugin);
        this.script = script;
        this.config = config;

        setPosition(OverlayPosition.TOP_CENTER);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(260, 0));

        String title = "KCP Account Builder";
        if (script.isBreakActive())
        {
            long breakRemaining = script.getBreakTimeRemainingSeconds();
            title = title + " | Break " + formatDuration(breakRemaining);
        }

        panelComponent.getChildren().add(TitleComponent.builder()
            .text(title)
            .color(Color.CYAN)
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Current Task:")
            .right(script.getCurrentTaskName())
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Status:")
            .right(Microbot.status == null || Microbot.status.isEmpty() ? "Idle" : Microbot.status)
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Time Running:")
            .right(formatDuration(script.getRuntimeSeconds()))
            .build());

        if (config.doBreaks())
        {
            if (script.isBreakActive())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Break Remaining:")
                    .right(formatDuration(script.getBreakTimeRemainingSeconds()))
                    .build());
            }
            else
            {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time till break:")
                    .right(formatDuration(script.getTimeUntilBreakSeconds()))
                    .build());
            }
        }

        if (config.enableActivitySwitchRandomization())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Time till activity change:")
                .right(formatDuration(script.getTimeUntilActivitySwitchSeconds()))
                .build());
        }

        return super.render(graphics);
    }

    private String formatDuration(long totalSeconds)
    {
        if (totalSeconds < 0)
        {
            return "--";
        }

        long hours = TimeUnit.SECONDS.toHours(totalSeconds);
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
        long seconds = totalSeconds % 60;

        if (hours > 0)
        {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}
