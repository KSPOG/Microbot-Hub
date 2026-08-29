package net.runelite.client.plugins.microbot.f2pfishing;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.time.Duration;

public class F2pFishingOverlay extends OverlayPanel {
    @Inject
    F2pFishingOverlay(F2pFishingPlugin plugin) {
        panelComponent.setPreferredSize(new java.awt.Dimension(220, 0));
    }

    @Override
    public java.awt.Dimension render(java.awt.Graphics2D graphics) {
        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("F2P Fishing")
                .leftColor(Color.CYAN)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status")
                .right(F2pFishingScript.status)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Mode")
                .right(F2pFishingScript.modeLabel)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Runtime")
                .right(formatDuration(F2pFishingScript.getRuntime()))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Fish caught")
                .right(Integer.toString(F2pFishingScript.fishCaught))
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
