package net.runelite.client.plugins.microbot.actionbars;

import net.runelite.api.SpritePixels;

import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.util.List;

public class SpriteHelper {
    private static final RescaleOp DARKEN_OP = new RescaleOp(0.5f, 0f, null);

    public static BufferedImage darkenImage(BufferedImage image) {
        BufferedImage output = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        DARKEN_OP.filter(image, output);
        return output;
    }

    public static BufferedImage combineSprites(List<SpritePixels> sprites) {
        int maxWidth = 0;
        int maxHeight = 0;
        for (SpritePixels sprite : sprites) {
            maxWidth = Math.max(maxWidth, sprite.getWidth());
            maxHeight = Math.max(maxHeight, sprite.getHeight());
        }
        BufferedImage output = new BufferedImage(maxWidth, maxHeight, BufferedImage.TYPE_INT_ARGB);
        for (SpritePixels sprite : sprites) {
            writeSprite(sprite, output);
        }
        return output;
    }

    public static void writeSprite(SpritePixels sprite, BufferedImage output) {
        if (output.getWidth() < sprite.getWidth() || output.getHeight() < sprite.getHeight()) {
            throw new IllegalArgumentException("Cannot draw sprite onto a smaller image");
        }
        int[] pixels = sprite.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            if (pixels[i] != 0) {
                int value = pixels[i] | 0xFF000000;
                output.setRGB(
                        sprite.getOffsetX() + (i % sprite.getWidth()),
                        sprite.getOffsetY() + (i / sprite.getWidth()),
                        value
                );
            }
        }
    }
}
