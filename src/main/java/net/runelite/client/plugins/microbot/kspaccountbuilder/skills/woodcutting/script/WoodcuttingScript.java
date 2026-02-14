package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.script;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.needed.ItemReqs;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Skeleton woodcutting workflow for KSPAccountBuilder.
 */
@Slf4j
@Getter
public class WoodcuttingScript {

    private WoodcuttingState state = WoodcuttingState.NOT_STARTED;

    public void initialize() {
        state = WoodcuttingState.INITIALIZING;
        log.info("WoodcuttingScript skeleton initialized.");
    }

    public boolean hasRequiredTools() {
        final int axeCount = getUsableAxeCount();
        return axeCount >= ItemReqs.MIN_AXE_COUNT;
    }

    private int getUsableAxeCount() {
        int count = 0;
        for (int axeId : ItemReqs.ACCEPTED_AXE_IDS) {
            if (Rs2Inventory.hasItem(axeId) || Rs2Equipment.isWearing(axeId)) {
                count++;
            }
        }
        return count;
    }

    public void execute() {
        state = WoodcuttingState.RUNNING;
        // TODO: Add tree selection and cutting workflow.
    }

    public void shutdown() {
        state = WoodcuttingState.STOPPED;
    }

    public enum WoodcuttingState {
        NOT_STARTED,
        INITIALIZING,
        RUNNING,
        STOPPED
    }
}
