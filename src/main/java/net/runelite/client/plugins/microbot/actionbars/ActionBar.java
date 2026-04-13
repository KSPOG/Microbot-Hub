package net.runelite.client.plugins.microbot.actionbars;

public class ActionBar {
    public static final int NUM_ACTIONS = 10;

    private final int id;
    private Action[] actions;

    public ActionBar(int id, Action[] actions) {
        this.id = id;
        this.actions = actions;
    }

    public int getNextEmptyActionIndex() {
        for (int i = 0; i < NUM_ACTIONS; i++) {
            if (actions[i] == null) {
                return i;
            }
        }
        return -1;
    }

    public void add(Action action) {
        int index = getNextEmptyActionIndex();
        if (index == -1) {
            throw new IllegalArgumentException("Cannot insert new action");
        }
        actions[index] = action;
    }

    public void remove(Action action) {
        for (int i = 0; i < NUM_ACTIONS; i++) {
            if (actions[i] == action) {
                actions[i] = null;
            }
        }
    }

    public void swapRight(Action action) {
        for (int i = 0; i < NUM_ACTIONS - 1; i++) {
            if (actions[i] == action) {
                actions[i] = actions[i + 1];
                actions[i + 1] = action;
                return;
            }
        }
    }

    public void swapLeft(Action action) {
        for (int i = 1; i < NUM_ACTIONS; i++) {
            if (actions[i] == action) {
                actions[i] = actions[i - 1];
                actions[i - 1] = action;
                return;
            }
        }
    }

    public void clear() {
        actions = new Action[NUM_ACTIONS];
    }

    public int getId() {
        return id;
    }

    public Action[] getActions() {
        return actions;
    }
}
