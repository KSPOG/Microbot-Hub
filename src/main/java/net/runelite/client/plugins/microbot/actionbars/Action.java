package net.runelite.client.plugins.microbot.actionbars;

import net.runelite.api.SpritePixels;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class Action {
    private final SavedAction savedAction;
    private final net.runelite.client.config.Keybind keybind;

    public Action(SavedAction savedAction, net.runelite.client.config.Keybind keybind) {
        if (savedAction == null) {
            throw new NullPointerException("savedAction is marked non-null but is null");
        }
        if (keybind == null) {
            throw new NullPointerException("keybind is marked non-null but is null");
        }
        this.savedAction = savedAction;
        this.keybind = keybind;
    }

    public Widget getWidget() {
        return Microbot.getClientThread()
                .invoke(() -> Microbot.getClient().getWidget(savedAction.getWidgetPackedId()));
    }

    public BufferedImage getImage() {
        if (savedAction.isItemAction()) {
            Widget itemWidget = getWidget();
            if (itemWidget == null) {
                return null;
            }
            Widget child = Microbot.getClientThread()
                    .invoke(() -> itemWidget.getChild(savedAction.getInventoryIndex()));
            if (child == null) {
                return null;
            }
            ItemManager itemManager = Microbot.getItemManager();
            BufferedImage image = itemManager.getImage(
                    savedAction.getItemId(),
                    child.getItemQuantity(),
                    child.getItemQuantityMode() != 2
            );
            if (savedAction.getInventoryIndex() != -1 && child.getItemQuantity() < 1) {
                return SpriteHelper.darkenImage(image);
            }
            return image;
        }

        Widget widget = getWidget();
        if (widget == null) {
            return null;
        }
        try {
            List<SpritePixels> sprites = getChildSprites(widget);
            return SpriteHelper.combineSprites(sprites);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException ex) {
            return null;
        }
    }

    public List<SpritePixels> getChildSprites(Widget widget) {
        List<SpritePixels> sprites = new ArrayList<>();
        if (widget == null || widget.isSelfHidden()) {
            return sprites;
        }
        int spriteId = widget.getSpriteId();
        if (spriteId != -1) {
            BufferedImage spriteImage = Microbot.getSpriteManager().getSprite(spriteId, 0);
            if (spriteImage != null) {
                sprites.add(ImageUtil.getImageSpritePixels(spriteImage, Microbot.getClient()));
            }
        }
        Widget[] children = widget.getChildren();
        if (children != null) {
            for (Widget child : children) {
                sprites.addAll(getChildSprites(child));
            }
        }
        return sprites;
    }

    public void invoke() {
        savedAction.invoke();
    }

    public String getName() {
        return savedAction.getName();
    }

    public SavedAction getSavedAction() {
        return savedAction;
    }

    public net.runelite.client.config.Keybind getKeybind() {
        return keybind;
    }
}
