package net.runelite.client.plugins.microbot.actionbars.bar;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.actionbars.Action;
import net.runelite.client.plugins.microbot.actionbars.ActionBar;
import net.runelite.client.plugins.microbot.actionbars.SavedAction;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ActionBarManager {
    public static final int NUM_ACTION_BARS = 1;

    @Inject
    private ConfigManager configManager;

    @Inject
    private Gson gson;

    private int activeBarIndex;
    private List<ActionBar> actionBars;

    public void startUp() {
        actionBars = load();
    }

    public void shutDown() {
        save(actionBars);
    }

    public ActionBar getActiveBar() {
        if (activeBarIndex < 0 || activeBarIndex >= actionBars.size()) {
            activeBarIndex = 0;
        }
        return actionBars.get(activeBarIndex);
    }

    public Action[] getActiveActions() {
        return getActiveBar().getActions();
    }

    public Action addAction(SavedAction savedAction, net.runelite.client.config.Keybind keybind) {
        ActionBar activeBar = getActiveBar();
        Action action = new Action(savedAction, keybind);
        activeBar.add(action);
        return action;
    }

    public void clearCurrentActionBar() {
        getActiveBar().clear();
    }

    public void nextActionBar() {
        activeBarIndex++;
        if (activeBarIndex >= actionBars.size()) {
            activeBarIndex = 0;
        }
    }

    public void previousActionBar() {
        activeBarIndex--;
        if (activeBarIndex < 0) {
            activeBarIndex = actionBars.size() - 1;
        }
    }

    public void createActionBar() {
        actionBars.add(new ActionBar(actionBars.size(), new Action[ActionBar.NUM_ACTIONS]));
        activeBarIndex = actionBars.size() - 1;
    }

    private void save(List<ActionBar> bars) {
        if (bars == null || bars.isEmpty()) {
            configManager.unsetConfiguration("action-bars", "storage");
            return;
        }
        String payload = gson.toJson(bars);
        configManager.setConfiguration("action-bars", "storage", payload);
    }

    private List<ActionBar> load() {
        String payload = configManager.getConfiguration("action-bars", "storage");
        if (Strings.isNullOrEmpty(payload)) {
            return List.of(new ActionBar(0, new Action[ActionBar.NUM_ACTIONS]));
        }
        ActionBar[] loaded = gson.fromJson(payload, ActionBar[].class);
        return new ArrayList<>(Arrays.asList(loaded));
    }

    public void setActiveBarIndex(int index) {
        this.activeBarIndex = index;
    }

    public int getActiveBarIndex() {
        return activeBarIndex;
    }

    public List<ActionBar> getActionBars() {
        return actionBars;
    }
}
