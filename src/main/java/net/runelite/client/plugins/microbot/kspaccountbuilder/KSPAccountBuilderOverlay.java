package net.runelite.client.plugins.microbot.kspaccountbuilder;

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
        panelComponent.setPreferredSize(new Dimension(230, 0));
    }

    @Override
    public Dimension render(java.awt.Graphics2D graphics) {
        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Account Builder")
                .color(Color.GREEN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Current Task:")
                .right(KSPAccountBuilderScript.getCurrentTask())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(KSPAccountBuilderScript.getStatus())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time Running:")
                .right(formatDuration(KSPAccountBuilderScript.getRuntime()))
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
