package net.runelite.client.plugins.microbot.ksppetnames;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Draws the active account's saved nickname directly above its follower model. */
public class KspPetNamesOverlay extends Overlay
{
    private static final Color NAME_COLOR = new Color(255, 220, 90);
    private static final int HEIGHT_PADDING = 40;

    private final Client client;
    private final KspPetNamesService petNamesService;

    @Inject
    public KspPetNamesOverlay(Client client, KspPetNamesService petNamesService)
    {
        this.client = client;
        this.petNamesService = petNamesService;
        setPosition(OverlayPosition.DYNAMIC);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        NPC follower = client.getFollower();
        if (follower == null)
        {
            return null;
        }

        String nickname = petNamesService.getNicknameFor(follower);
        if (nickname == null || nickname.trim().isEmpty())
        {
            return null;
        }

        Font previousFont = graphics.getFont();
        try
        {
            graphics.setFont(FontManager.getRunescapeBoldFont());

            Point textLocation = follower.getCanvasTextLocation(
                graphics,
                nickname,
                follower.getLogicalHeight() + HEIGHT_PADDING
            );

            if (textLocation != null)
            {
                OverlayUtil.renderTextLocation(graphics, textLocation, nickname, NAME_COLOR);
            }
        }
        finally
        {
            graphics.setFont(previousFont);
        }

        return null;
    }
}
