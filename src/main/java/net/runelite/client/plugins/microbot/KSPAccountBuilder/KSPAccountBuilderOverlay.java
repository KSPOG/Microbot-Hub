package net.runelite.client.plugins.microbot.KSPAccountBuilder;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.time.Duration;

public class KSPAccountBuilderOverlay extends OverlayPanel {
    @Inject
    KSPAccountBuilderOverlay(KSPAccountBuilderPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_CENTER);
    }

    @Override
    public Dimension render(java.awt.Graphics2D graphics) {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(240, 150));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Account Builder")
                .color(Color.ORANGE)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Stage:")
                .right(KSPAccountBuilderScript.stageLabel)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(KSPAccountBuilderScript.status)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time running:")
                .right(formatDuration(KSPAccountBuilderScript.getRuntime()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Change Task in:")
                .right(formatDuration(KSPAccountBuilderScript.getTimeUntilSwitch()))
                .build());

        return super.render(graphics);
    }

    private String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
