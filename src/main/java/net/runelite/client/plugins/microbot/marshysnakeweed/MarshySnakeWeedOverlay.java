package net.runelite.client.plugins.microbot.marshysnakeweed;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class MarshySnakeWeedOverlay extends OverlayPanel {
    private final MarshySnakeWeedScript script;

    @Inject
    MarshySnakeWeedOverlay(MarshySnakeWeedPlugin plugin, MarshySnakeWeedScript script) {
        super(plugin);
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(210, 120));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Snake Weed V" + MarshySnakeWeedPlugin.version)
                .color(Color.GREEN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("State")
                .right(script.getState().name())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status")
                .right(script.getStatus())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Banked")
                .right(String.valueOf(script.getBankedThisRun()))
                .build());

        return super.render(graphics);
    }
}
