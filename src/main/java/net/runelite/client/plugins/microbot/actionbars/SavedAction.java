package net.runelite.client.plugins.microbot.actionbars;

import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SavedAction {
    private static final Logger log = LoggerFactory.getLogger(SavedAction.class);

    private final String option;
    private final String target;
    private final int identifier;
    private final int opcode;
    private final int param0;
    private final int param1;
    private final int itemId;

    public SavedAction(String option, String target, int identifier, int opcode, int param0, int param1, int itemId) {
        this.option = option;
        this.target = target;
        this.identifier = identifier;
        this.opcode = opcode;
        this.param0 = param0;
        this.param1 = param1;
        this.itemId = itemId;
    }

    public int getWidgetPackedId() {
        return param1;
    }

    public static SavedAction fromMenuEntry(MenuEntry entry) {
        return new SavedAction(
                entry.getOption(),
                entry.getTarget(),
                entry.getIdentifier(),
                entry.getType().getId(),
                entry.getParam0(),
                entry.getParam1(),
                entry.getItemId()
        );
    }

    public String getName() {
        return Text.removeTags(getOption() + " " + getTarget());
    }

    public boolean isItemAction() {
        return itemId > -1;
    }

    public int getInventoryIndex() {
        ItemContainer container = Microbot.getClient().getItemContainer(InventoryID.INVENTORY);
        if (container == null || !container.contains(itemId)) {
            return -1;
        }
        for (int i = 0; i < container.size(); i++) {
            Item item = container.getItem(i);
            if (item != null && item.getId() == itemId) {
                return i;
            }
        }
        return param0;
    }

    public void invoke() {
        log.info("Invoking action {}", getName());
        Microbot.getClientThread().invokeLater(() -> {
            int menuParam0 = isItemAction() ? getInventoryIndex() : param0;
            Rs2Reflection.invokeMenu(
                    menuParam0,
                    param1,
                    opcode,
                    identifier,
                    itemId,
                    option,
                    target,
                    -1,
                    -1
            );
        });
    }

    public String getOption() {
        return option;
    }

    public String getTarget() {
        return target;
    }

    public int getIdentifier() {
        return identifier;
    }

    public int getOpcode() {
        return opcode;
    }

    public int getParam0() {
        return param0;
    }

    public int getParam1() {
        return param1;
    }

    public int getItemId() {
        return itemId;
    }
}
