package net.runelite.client.plugins.microbot.objectidexaminer;

import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;

public class ObjectIdExaminerOverlay extends OverlayPanel
{
    private static final int OVERLAY_WIDTH = 260;

    private static final Color OBJECT_BORDER = new Color(0, 255, 255, 230);
    private static final Color OBJECT_FILL = new Color(0, 255, 255, 45);

    private final ObjectIdExaminerPlugin plugin;

    @Inject
    public ObjectIdExaminerOverlay(ObjectIdExaminerPlugin plugin)
    {
        this.plugin = plugin;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setResizable(true);

        panelComponent.setPreferredSize(new Dimension(OVERLAY_WIDTH, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        drawObjectHighlight(graphics);
        renderInfoPanel();

        return super.render(graphics);
    }

    private void renderInfoPanel()
    {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(OVERLAY_WIDTH, 0));

        panelComponent.getChildren().add(
                TitleComponent.builder()
                        .text("Object Examiner")
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Last Clicked Object:")
                        .right(plugin.getLastClickedObject())
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Object ID:")
                        .right(plugin.getLastObjectId() == -1 ? "None" : String.valueOf(plugin.getLastObjectId()))
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Object Location:")
                        .right(plugin.getLastObjectLocationText())
                        .build()
        );
    }

    private void drawObjectHighlight(Graphics2D graphics)
    {
        TileObject object = plugin.getLastExaminedObject();

        if (object == null)
        {
            return;
        }

        Shape clickbox = object.getClickbox();

        if (clickbox == null)
        {
            return;
        }

        graphics.setColor(OBJECT_FILL);
        graphics.fill(clickbox);

        graphics.setColor(OBJECT_BORDER);
        graphics.setStroke(new BasicStroke(2));
        graphics.draw(clickbox);
    }
}