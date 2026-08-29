package net.runelite.client.plugins.microbot.actionbars;

public class ActionbarsSlot {
    private final String label;
    private final ActionbarsAction action;

    public ActionbarsSlot(String label, ActionbarsAction action) {
        this.label = label == null ? "" : label.trim();
        this.action = action == null ? new ActionbarsAction(ActionbarsActionType.NONE, "") : action;
    }

    public String getLabel() {
        return label;
    }

    public ActionbarsAction getAction() {
        return action;
    }
}
