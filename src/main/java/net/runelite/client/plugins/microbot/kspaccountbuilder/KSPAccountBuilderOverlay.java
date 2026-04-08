package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class KSPAccountBuilderOverlay extends OverlayPanel {

    @Inject
    KSPAccountBuilderOverlay() {
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Account Builder v" + KSPAccountBuilderPlugin.version)
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status")
                .right("Running")
                .build());

        return super.render(graphics);
    }
}