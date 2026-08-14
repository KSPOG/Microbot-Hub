package net.runelite.client.plugins.microbot.kspautorepair;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.KSP + "Auto Repair Agent",
        description = "Autonomously observes KSP intent, actions and game-state results and validates self-healing source repairs",
        tags = {"ksp", "agent", "repair", "debug", "autonomous", "development", "observer"},
        version = KspAutoRepairPlugin.VERSION,
        cardUrl = "",
        iconUrl = "",
        minClientVersion = "2.6.19",
        enabledByDefault = false,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspAutoRepairPlugin extends Plugin
{
    public static final String VERSION = "0.2.0";

    @Inject
    private Client client;

    @Inject
    private PluginManager pluginManager;

    @Inject
    private KspAutoRepairConfig config;

    private KspAutoRepairCoordinator coordinator;

    @Provides
    KspAutoRepairConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspAutoRepairConfig.class);
    }

    @Override
    protected void startUp()
    {
        coordinator = new KspAutoRepairCoordinator(client, pluginManager, config);
        coordinator.start();
    }

    @Override
    protected void shutDown()
    {
        if (coordinator != null)
        {
            coordinator.stop();
            coordinator = null;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onGameTick();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onGameStateChanged(event);
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onItemContainerChanged(event);
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onVarbitChanged(event);
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onWidgetLoaded(event);
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onWidgetClosed(event);
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onMenuOptionClicked(event);
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onNpcSpawned(event);
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onNpcDespawned(event);
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onGameObjectSpawned(event);
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onGameObjectDespawned(event);
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onAnimationChanged(event);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onChatMessage(event);
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onStatChanged(event);
    }
}
