package net.runelite.client.plugins.microbot.ksptutisland;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class KSPTutIslandOverlay extends OverlayPanel
{
    private final KSPTutIslandScript script;
    private final KSPTutIslandConfig config;

    @Inject
    public KSPTutIslandOverlay(KSPTutIslandPlugin plugin, KSPTutIslandScript script, KSPTutIslandConfig config)
    {
        super(plugin);
        this.script = script;
        this.config = config;
        setPosition(OverlayPosition.TOP_CENTER);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(230, 0));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Tutorial Island V0.0.1")
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Start Area:")
                .right(formatBoolean(script.isPlayerInStartArea()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(script.getStatus())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Multi Account:")
                .right(formatBoolean(config.runMultipleAccounts()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Queue Left:")
                .right(String.valueOf(script.getRemainingQueuedAccounts()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Queued Login:")
                .right(script.getQueuedAccountName())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Name UI:")
                .right(formatBoolean(script.isNameCreationOpen()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Creator UI:")
                .right(formatBoolean(script.isCharacterCreationOpen()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Familiar UI:")
                .right(formatBoolean(script.isExperiencePromptVisible()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Last Name:")
                .right(script.getLastGeneratedName())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Character:")
                .right(script.getLastCharacterAction())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Familiar:")
                .right(script.getLastExperienceSelection())
                .build());

        return super.render(graphics);
    }

    private String formatBoolean(boolean value)
    {
        return value ? "Yes" : "No";
    }
}
