package net.runelite.client.plugins.microbot.dashboardlink;

import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

final class DashboardLinkOverlay extends OverlayPanel {
    private final DashboardLinkPlugin plugin;
    DashboardLinkOverlay(DashboardLinkPlugin plugin) {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setPreferredSize(new Dimension(245, 0));
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().add(TitleComponent.builder().text("Dashboard Link").build());
        panelComponent.getChildren().add(LineComponent.builder().left(plugin.getConnectionStatus()).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Manual mule transfers")
            .right(Integer.toString(plugin.getPendingTransfers())).build());
        return super.render(graphics);
    }
}
