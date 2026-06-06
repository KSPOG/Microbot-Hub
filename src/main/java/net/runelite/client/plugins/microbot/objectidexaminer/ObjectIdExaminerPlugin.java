package net.runelite.client.plugins.microbot.objectidexaminer;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;

@Slf4j
@PluginDescriptor(
        name = "Object ID Examiner",
        description = "Shows the last examined object ID, location, and highlights the examined object.",
        tags = {"microbot", "object", "id", "examine", "overlay"},
        authors = {"KSP"},
        version = ObjectIdExaminerPlugin.VERSION,
        minClientVersion = "2.0.13",
        enabledByDefault = false,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class ObjectIdExaminerPlugin extends Plugin
{
    public static final String VERSION = "1.0.3";

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ObjectIdExaminerOverlay overlay;

    @Getter
    private String lastClickedObject = "None";

    @Getter
    private int lastObjectId = -1;

    @Getter
    private WorldPoint lastObjectLocation = null;

    @Getter
    private TileObject lastExaminedObject = null;

    @Provides
    ObjectIdExaminerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ObjectIdExaminerConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        resetLastObject();

        log.info("Object ID Examiner started.");
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        resetLastObject();

        log.info("Object ID Examiner stopped.");
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (event == null)
        {
            return;
        }

        if (event.getMenuAction() != MenuAction.EXAMINE_OBJECT)
        {
            return;
        }

        if (client == null)
        {
            return;
        }

        final int objectId = event.getId();
        final int sceneX = event.getParam0();
        final int sceneY = event.getParam1();
        final int plane = client.getPlane();

        lastObjectId = objectId;
        lastClickedObject = cleanTarget(event.getMenuTarget());
        lastObjectLocation = WorldPoint.fromScene(client, sceneX, sceneY, plane);
        lastExaminedObject = findTileObject(objectId, sceneX, sceneY, plane);

        client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                "Object ID: " + lastObjectId + " | Location: " + getLastObjectLocationText(),
                null
        );

        log.info(
                "Examined object: name='{}', id={}, location={}, objectFound={}",
                lastClickedObject,
                lastObjectId,
                getLastObjectLocationText(),
                lastExaminedObject != null
        );
    }

    private TileObject findTileObject(int objectId, int sceneX, int sceneY, int plane)
    {
        Scene scene = client.getScene();

        if (scene == null)
        {
            return null;
        }

        Tile[][][] tiles = scene.getTiles();

        if (tiles == null)
        {
            return null;
        }

        if (plane < 0 || plane >= tiles.length)
        {
            return null;
        }

        if (sceneX < 0 || sceneX >= tiles[plane].length)
        {
            return null;
        }

        if (sceneY < 0 || sceneY >= tiles[plane][sceneX].length)
        {
            return null;
        }

        Tile tile = tiles[plane][sceneX][sceneY];

        if (tile == null)
        {
            return null;
        }

        if (tile.getWallObject() != null && tile.getWallObject().getId() == objectId)
        {
            return tile.getWallObject();
        }

        if (tile.getDecorativeObject() != null && tile.getDecorativeObject().getId() == objectId)
        {
            return tile.getDecorativeObject();
        }

        if (tile.getGroundObject() != null && tile.getGroundObject().getId() == objectId)
        {
            return tile.getGroundObject();
        }

        GameObject[] gameObjects = tile.getGameObjects();

        if (gameObjects != null)
        {
            for (GameObject gameObject : gameObjects)
            {
                if (gameObject != null && gameObject.getId() == objectId)
                {
                    return gameObject;
                }
            }
        }

        return null;
    }

    private String cleanTarget(String target)
    {
        if (target == null || target.isBlank())
        {
            return "Unknown";
        }

        String cleaned = Text.removeTags(target).trim();

        if (cleaned.isBlank())
        {
            return "Unknown";
        }

        return cleaned;
    }

    public String getLastObjectLocationText()
    {
        if (lastObjectLocation == null)
        {
            return "Unknown";
        }

        return lastObjectLocation.getX()
                + ", "
                + lastObjectLocation.getY()
                + ", "
                + lastObjectLocation.getPlane();
    }

    private void resetLastObject()
    {
        lastClickedObject = "None";
        lastObjectId = -1;
        lastObjectLocation = null;
        lastExaminedObject = null;
    }
}