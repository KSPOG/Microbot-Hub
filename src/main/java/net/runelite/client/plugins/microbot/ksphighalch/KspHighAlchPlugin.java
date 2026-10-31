package net.runelite.client.plugins.microbot.ksphighalch;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@PluginDescriptor(
        name = PluginConstants.KSP + "High Alcher",
        description = "Kills Ogress Warriors on Corsair Cove",
        tags = {"microbot", "ksp", "ogress", "killer", "hotreload"},
        authors = {"KSP"},
        version = KspHighAlchPlugin.VERSION,
        minClientVersion = "2.0.13",
        enabledByDefault = true,
        isExternal = true
)
@Slf4j
public class KspHighAlchPlugin extends Plugin implements KeyListener
{
    public static final String VERSION = "0.0.1";
    private static final String MENU_OPTION = "Set High Alch Item";

    @Inject
    private Client client;

    @Inject
    private KspHighAlchConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private KeyManager keyManager;

    @Inject
    private KspHighAlchScript script;

    private volatile boolean shiftDown;

    @Provides
    KspHighAlchConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspHighAlchConfig.class);
    }

    @Override
    protected void startUp()
    {
        keyManager.registerKeyListener(this);
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        keyManager.unregisterKeyListener(this);
        shiftDown = false;
        script.shutdown();
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (!shiftDown)
        {
            return;
        }

        if (!isInventoryMenuEntry(event.getActionParam1()))
        {
            return;
        }

        if (event.getIdentifier() <= 0)
        {
            return;
        }

        client.createMenuEntry(-1)
                .setOption(MENU_OPTION)
                .setTarget(event.getTarget())
                .setType(MenuAction.RUNELITE)
                .setIdentifier(event.getIdentifier())
                .setParam0(event.getActionParam0())
                .setParam1(event.getActionParam1());
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!MENU_OPTION.equals(event.getMenuOption()))
        {
            return;
        }

        event.consume();

        int itemId = event.getId();

        ItemComposition composition = client.getItemDefinition(itemId);
        if (composition == null)
        {
            Microbot.log("[KspHighAlch] Unable to resolve selected item id: " + itemId);
            return;
        }

        String itemName = composition.getName();
        if (itemName == null || itemName.trim().isEmpty() || "null".equalsIgnoreCase(itemName))
        {
            return;
        }

        Set<String> items = Arrays.stream(config.alchItems().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        boolean added = items.add(itemName.trim());
        String updated = String.join(", ", items);
        configManager.setConfiguration(KspHighAlchConfig.GROUP, "alchItems", updated);
        Microbot.log("[KspHighAlch] " + (added ? "Added" : "Already configured") + " high alch item: " + itemName);
    }

    private boolean isInventoryMenuEntry(int param1)
    {
        return param1 == ComponentID.INVENTORY_CONTAINER;
    }

    @Override
    public void keyTyped(KeyEvent e)
    {
        // Not used.
    }

    @Override
    public void keyPressed(KeyEvent e)
    {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT)
        {
            shiftDown = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e)
    {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT)
        {
            shiftDown = false;
        }
    }
}
