package net.runelite.client.plugins.microbot.actionbars;

import net.runelite.client.plugins.microbot.Microbot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ActionbarsDefinitions {
    public static final int SLOT_COUNT = 12;

    private ActionbarsDefinitions() {
    }

    public static List<ActionbarsBar> parseBars(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.singletonList(defaultBar());
        }

        String[] lines = raw.split("\\r?\\n");
        List<ActionbarsBar> bars = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            bars.add(parseBar(line));
        }
        if (bars.isEmpty()) {
            bars.add(defaultBar());
        }
        return bars;
    }

    public static ActionbarsBar resolveActiveBar(String raw, int activeIndex) {
        List<ActionbarsBar> bars = parseBars(raw);
        int clampedIndex = clampActiveIndex(bars, activeIndex);
        return bars.get(clampedIndex - 1);
    }

    public static int clampActiveIndex(List<ActionbarsBar> bars, int activeIndex) {
        int totalBars = bars == null || bars.isEmpty() ? 1 : bars.size();
        int index = activeIndex < 1 ? 1 : Math.min(activeIndex, totalBars);
        return index;
    }

    public static void updateActiveBarIndex(int nextIndex) {
        Microbot.getConfigManager().setConfiguration("actionbars", "activeBarIndex", nextIndex);
    }

    private static ActionbarsBar parseBar(String line) {
        String[] slots = line.split("\\|");
        List<ActionbarsSlot> parsedSlots = new ArrayList<>();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (i < slots.length) {
                parsedSlots.add(parseSlot(slots[i]));
            } else {
                parsedSlots.add(new ActionbarsSlot("", new ActionbarsAction(ActionbarsActionType.NONE, "")));
            }
        }
        return new ActionbarsBar(parsedSlots);
    }

    private static ActionbarsSlot parseSlot(String slotText) {
        if (slotText == null || slotText.trim().isEmpty()) {
            return new ActionbarsSlot("", new ActionbarsAction(ActionbarsActionType.NONE, ""));
        }
        String trimmed = slotText.trim();
        String label = "";
        String actionDefinition = trimmed;
        int labelIndex = trimmed.indexOf('=');
        if (labelIndex >= 0) {
            label = trimmed.substring(0, labelIndex).trim();
            actionDefinition = trimmed.substring(labelIndex + 1).trim();
        }

        String actionName = actionDefinition;
        String payload = "";
        int payloadIndex = actionDefinition.indexOf(':');
        if (payloadIndex >= 0) {
            actionName = actionDefinition.substring(0, payloadIndex);
            payload = actionDefinition.substring(payloadIndex + 1);
        }

        ActionbarsActionType type = ActionbarsActionType.from(actionName);
        return new ActionbarsSlot(label, new ActionbarsAction(type, payload));
    }

    private static ActionbarsBar defaultBar() {
        List<ActionbarsSlot> slots = new ArrayList<>();
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots.add(new ActionbarsSlot("", new ActionbarsAction(ActionbarsActionType.NONE, "")));
        }
        return new ActionbarsBar(slots);
    }
}
