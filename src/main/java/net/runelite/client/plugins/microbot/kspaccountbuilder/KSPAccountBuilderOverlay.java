package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class KSPAccountBuilderOverlay extends OverlayPanel {

    private final KSPAccountBuilderPlugin plugin;
    private final KSPAccountBuilderScript script;

    @Inject
    KSPAccountBuilderOverlay(KSPAccountBuilderPlugin plugin, KSPAccountBuilderScript script) {
        super(plugin);
        this.plugin = plugin;
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(260, 140));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text(plugin.getClass().getAnnotation(PluginDescriptor.class).name())
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder().build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Current Task:")
                .right(script.getCurrentTask())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(script.getStatus())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time Running:")
                .right(script.getRunningTime())
                .build());

        return super.render(graphics);
    }
}
