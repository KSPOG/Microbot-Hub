package net.runelite.client.plugins.microbot.actionbars;

import java.util.Collections;
import java.util.List;

public class ActionbarsBar {
    private final List<ActionbarsSlot> slots;

    public ActionbarsBar(List<ActionbarsSlot> slots) {
        this.slots = slots == null ? Collections.emptyList() : Collections.unmodifiableList(slots);
    }

    public List<ActionbarsSlot> getSlots() {
        return slots;
    }

    public ActionbarsSlot getSlot(int index) {
        if (index < 0 || index >= slots.size()) {
            return null;
        }
        return slots.get(index);
    }
}
