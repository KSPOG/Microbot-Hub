package net.runelite.client.plugins.microbot.f2pfishing;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.time.Duration;

public class F2pFishingOverlay extends OverlayPanel {
    @Inject
    F2pFishingOverlay(F2pFishingPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_CENTER);
        panelComponent.setPreferredSize(new Dimension(220, 0));
    }

    @Override
    public Dimension render(java.awt.Graphics2D graphics) {
        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("F2P Fishing")
                .color(Color.CYAN)
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
