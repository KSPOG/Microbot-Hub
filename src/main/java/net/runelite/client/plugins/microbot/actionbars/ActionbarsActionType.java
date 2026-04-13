package net.runelite.client.plugins.microbot.actionbars;

public enum ActionbarsActionType {
    NONE,
    EAT_FOOD,
    EAT_FAST_FOOD,
    DRINK_PRAYER_POTION,
    TOGGLE_SPEC,
    TOGGLE_PRAYER;

    public static ActionbarsActionType from(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return ActionbarsActionType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }
}
