package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.mining;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Skeleton mining setup for KSPAccountBuilder.
 * <p>
 * This class is intentionally minimal and only defines the structure
 * needed for future mining progression implementation.
 */
@Slf4j
@Getter
public class MiningSetup {

    private MiningState state = MiningState.NOT_STARTED;

    public void initialize() {
        state = MiningState.INITIALIZING;
        log.info("MiningSetup skeleton initialized.");
    }

    public boolean hasRequiredTools() {
        // TODO: Add validation for pickaxe, inventory space, and transport requirements.
        return false;
    }

    public void execute() {
        state = MiningState.RUNNING;
        // TODO: Add mining route selection and action loop.
    }

    public void shutdown() {
        state = MiningState.STOPPED;
    }

    public enum MiningState {
        NOT_STARTED,
        INITIALIZING,
        RUNNING,
        STOPPED
    }
}
