package net.runelite.client.plugins.microbot.KSPAutoWoodcutter;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.time.Duration;

public class KSPAutoWoodcutterOverlay extends OverlayPanel {
    @Inject
    KSPAutoWoodcutterOverlay(KSPAutoWoodcutterPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_CENTER);
    }

    @Override
    public Dimension render(java.awt.Graphics2D graphics) {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(230, 150));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSPAutoWoodcutter")
                .color(Color.ORANGE)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Current Status:")
                .right(KSPAutoWoodcutterScript.status)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Mode:")
                .right(KSPAutoWoodcutterScript.modeLabel)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time running:")
                .right(formatDuration(KSPAutoWoodcutterScript.getRuntime()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Logs Cut:")
                .right(Integer.toString(KSPAutoWoodcutterScript.logsCut))
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
