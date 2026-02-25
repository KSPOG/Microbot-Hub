package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;

public class KSPAccountBuilderOverlay extends OverlayPanel {
    @Inject
    KSPAccountBuilderOverlay(KSPAccountBuilderPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_CENTER);
    }

    @Override
    public Dimension render(java.awt.Graphics2D graphics) {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(260, 120));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSPAccountBuilder")
                .color(Color.ORANGE)
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
                .right(KSPAccountBuilderScript.getRunningTime())
                .build());

        return super.render(graphics);
    }
}
