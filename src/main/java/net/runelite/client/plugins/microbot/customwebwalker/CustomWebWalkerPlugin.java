package net.runelite.client.plugins.microbot.customwebwalker;

import com.google.inject.Provides;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

@PluginDescriptor(
        name = PluginConstants.DEFAULT_PREFIX + "Custom WebWalker",
        description = "Example plugin that uses CustomWebWalker to navigate to a target tile.",
        tags = {"walking", "navigation"},
        authors = {"KSP"},
        version = CustomWebWalkerPlugin.version,
        minClientVersion = "1.9.8",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class CustomWebWalkerPlugin extends Plugin {

    private static final String WALK_HERE = "Walk here";
    private static final String SET_DESTINATION = "Set destination";
    private static final String DESTINATION_TARGET = "Custom WebWalker";

    static final String version = "1.0.8";

    @Inject
    private CustomWebWalkerScript script;

    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    @Provides
    CustomWebWalkerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(CustomWebWalkerConfig.class);
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (!WALK_HERE.equals(event.getOption()) || !event.getTarget().isEmpty()) {
            return;
        }

        addMenuEntry(event, SET_DESTINATION, DESTINATION_TARGET, 1);
    }

    @Override
    protected void startUp() {
        script.run();
    }

    @Override
    protected void shutDown() {
        script.shutdown();
    }

    private void onMenuOptionClicked(MenuEntry entry) {
        if (!SET_DESTINATION.equals(entry.getOption()) || !DESTINATION_TARGET.equals(entry.getTarget())) {
            return;
        }

        WorldPoint destination = WorldPoint.fromScene(
                client.getTopLevelWorldView(),
                entry.getParam0(),
                entry.getParam1(),
                client.getTopLevelWorldView().getPlane()
        );

        if (destination == null) {
            return;
        }

        configManager.setConfiguration(CustomWebWalkerConfig.configGroup, "targetX", Integer.toString(destination.getX()));
        configManager.setConfiguration(CustomWebWalkerConfig.configGroup, "targetY", Integer.toString(destination.getY()));
        configManager.setConfiguration(CustomWebWalkerConfig.configGroup, "targetPlane", destination.getPlane());
    }

    private void addMenuEntry(MenuEntryAdded event, String option, String target, int position) {
        List<MenuEntry> entries = new LinkedList<>(Arrays.asList(client.getMenu().getMenuEntries()));

        if (entries.stream().anyMatch(entry -> option.equals(entry.getOption()) && target.equals(entry.getTarget()))) {
            return;
        }

        client.createMenuEntry(position)
                .setOption(option)
                .setTarget(target)
                .setParam0(event.getActionParam0())
                .setParam1(event.getActionParam1())
                .setIdentifier(event.getIdentifier())
                .setType(MenuAction.RUNELITE)
                .onClick(this::onMenuOptionClicked);
    }
}
