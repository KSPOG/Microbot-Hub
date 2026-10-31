package net.runelite.client.plugins.microbot.f2pbossfarmer;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class F2PBossFarmerOverlay extends OverlayPanel {

    private final F2PBossFarmerScript script;

    @Inject
    F2PBossFarmerOverlay(F2PBossFarmerPlugin plugin, F2PBossFarmerScript script) {
        super(plugin);
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(230, 0));
            panelComponent.getChildren().add(
                    TitleComponent.builder()
                            .text("KSP F2P Boss Farmer v" + F2PBossFarmerPlugin.version)
                            .color(Color.WHITE)
                            .build()
            );

            panelComponent.getChildren().add(
                    LineComponent.builder()
                            .left("State")
                            .right(script.getState().toString())
                            .build()
            );

            panelComponent.getChildren().add(
                    LineComponent.builder()
                            .left("Target")
                            .right(script.getActiveTarget().toString())
                            .build()
            );

            panelComponent.getChildren().add(
                    LineComponent.builder()
                            .left("Giant key (inv)")
                            .right(String.valueOf(script.getSessionGiantKeys()))
                            .build()
            );

            panelComponent.getChildren().add(
                    LineComponent.builder()
                            .left("Mossy key (inv)")
                            .right(String.valueOf(script.getSessionMossyKeys()))
                            .build()
            );

            panelComponent.getChildren().add(
                    LineComponent.builder()
                            .left("Obor chests")
                            .right(String.valueOf(script.getOborChests()))
                            .build()
            );

            panelComponent.getChildren().add(
                    LineComponent.builder()
                            .left("Bryophyta chests")
                            .right(String.valueOf(script.getBryophytaChests()))
                            .build()
            );
        } catch (Exception ex) {
            Microbot.logStackTrace(getClass().getSimpleName(), ex);
        }

        return super.render(graphics);
    }
}
