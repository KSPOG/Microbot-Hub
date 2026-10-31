package net.runelite.client.plugins.microbot.ksppetnames;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = PluginConstants.KSP + "Pet Names",
    description = "Give your pets account-bound custom names and display the name above your active pet.",
    tags = {"pet", "pets", "name", "nickname", "follower", "overlay", "ksp"},
    authors = {"KSP"},
    version = KspPetNamesPlugin.VERSION,
    minClientVersion = "2.0.61",
    iconUrl = "",
    cardUrl = "",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KspPetNamesPlugin extends Plugin
{
    public static final String VERSION = "0.1.0";

    @Inject private KspPetNamesService petNamesService;
    @Inject private KspPetNamesOverlay overlay;
    @Inject private OverlayManager overlayManager;
    @Inject private ClientToolbar clientToolbar;

    private KspPetNamesPanel panel;
    private NavigationButton navButton;

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        addSidePanel();
        log.info("KSP Pet Names enabled. Pet names are stored in the active RuneScape profile.");
    }

    @Override
    protected void shutDown()
    {
        removeSidePanel();
        overlayManager.remove(overlay);
        log.info("KSP Pet Names disabled.");
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
    {
        petNamesService.onProfileChanged();

        KspPetNamesPanel currentPanel = panel;
        if (currentPanel != null)
        {
            SwingUtilities.invokeLater(currentPanel::onProfileChanged);
        }
    }

    private void addSidePanel()
    {
        SwingUtilities.invokeLater(() -> {
            if (panel != null)
            {
                return;
            }

            panel = new KspPetNamesPanel(petNamesService);
            navButton = NavigationButton.builder()
                .tooltip("KSP Pet Names")
                .icon(createSidebarIcon())
                .priority(6)
                .panel(panel)
                .build();

            clientToolbar.addNavigation(navButton);
        });
    }

    private void removeSidePanel()
    {
        SwingUtilities.invokeLater(() -> {
            if (navButton != null)
            {
                clientToolbar.removeNavigation(navButton);
                navButton = null;
            }

            if (panel != null)
            {
                panel.dispose();
                panel = null;
            }
        });
    }

    private static BufferedImage createSidebarIcon()
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(new Color(80, 220, 120));
            g.fillOval(5, 6, 7, 7);

            g.fillOval(2, 3, 4, 4);
            g.fillOval(6, 1, 4, 4);
            g.fillOval(11, 3, 4, 4);

            g.setColor(new Color(30, 30, 30));
            g.fillOval(7, 8, 1, 1);
            g.fillOval(10, 8, 1, 1);
        }
        finally
        {
            g.dispose();
        }

        return image;
    }

    public void prepareHotUnload() throws Exception
    {
        shutDown();
    }
}
