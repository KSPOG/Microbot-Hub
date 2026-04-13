package net.runelite.client.plugins.microbot.kspmelee;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class KSPMeleeOverlay extends OverlayPanel {

    private final KSPMeleeScript script;

    @Inject
    KSPMeleeOverlay(KSPMeleePlugin plugin, KSPMeleeScript script) {
        super(plugin);
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(220, 120));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Melee")
                .color(Color.GREEN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder().build());

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
