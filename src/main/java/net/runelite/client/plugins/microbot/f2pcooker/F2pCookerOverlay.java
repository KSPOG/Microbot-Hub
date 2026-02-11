package net.runelite.client.plugins.microbot.f2pcooker;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.time.Duration;

public class F2pCookerOverlay extends OverlayPanel {
    @Inject
    F2pCookerOverlay(F2pCookerPlugin plugin) {
        super(plugin);
        panelComponent.setPreferredSize(new java.awt.Dimension(220, 0));
    }

    @Override
    public java.awt.Dimension render(java.awt.Graphics2D graphics) {
        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP F2P Cooker")
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time Status:")
                .right(F2pCookerScript.status)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time Running:")
                .right(formatDuration(F2pCookerScript.getRuntime()))
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
