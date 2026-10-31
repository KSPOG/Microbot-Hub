package net.runelite.client.plugins.microbot.kspconstruction;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class KspConstructionOverlay extends OverlayPanel
{
    private final KspConstructionPlugin plugin;

    @Inject
    public KspConstructionOverlay(KspConstructionPlugin plugin)
    {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Construction")
                .color(Color.ORANGE)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Furniture:")
                .right(plugin.getConfig().furnitureMode().toString())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Plank source:")
                .right(plugin.getConfig().plankSource().toString())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(plugin.getScript().getState().toString())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Built:")
                .right(String.valueOf(plugin.getScript().getFurnitureBuilt()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Planks:")
                .right(String.valueOf(plugin.getScript().getCurrentPlankCount()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(plugin.getScript().getStatus())
                .build());

        return super.render(graphics);
    }
}
