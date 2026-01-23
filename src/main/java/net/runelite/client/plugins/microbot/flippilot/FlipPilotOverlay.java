package net.runelite.client.plugins.microbot.flippilot;

import net.runelite.client.plugins.microbot.flippilot.microbot.FlipEventBus;
import net.runelite.client.ui.overlay.*;

import javax.inject.Inject;
import java.awt.*;

public class FlipPilotOverlay extends Overlay
{
    private final FlipPilotConfig config;
    private final FlipEventBus eventBus;
    private volatile String status = "idle";

    @Inject
    public FlipPilotOverlay(FlipPilotConfig config, FlipEventBus eventBus)
    {
        this.config = config;
        this.eventBus = eventBus;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.MED);
    }

    public void setStatus(String s) { status = s; }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay()) return null;

        PanelComponent panel = new PanelComponent();
        panel.getChildren().add(LineComponent.builder().left("FlipPilot").right("").build());
        panel.getChildren().add(LineComponent.builder().left("Status").right(status).build());
        panel.getChildren().add(LineComponent.builder().left("Profit").right(eventBus.getSessionProfit() + " gp").build());

        return panel.render(graphics);
    }
}
