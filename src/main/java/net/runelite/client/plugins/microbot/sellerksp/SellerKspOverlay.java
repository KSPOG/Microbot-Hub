package net.runelite.client.plugins.microbot.sellerksp;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class SellerKspOverlay extends OverlayPanel
{
    private final SellerKspScript script;

    @Inject
    public SellerKspOverlay(SellerKspPlugin plugin, SellerKspScript script)
    {
        super(plugin);
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(220, 0));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Seller KSP")
                .color(Color.ORANGE)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(script.getState().name())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(script.getStatusMessage())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Sellable inv:")
                .right(String.valueOf(script.getInventoryCount()))
                .build());

        return super.render(graphics);
    }
}
