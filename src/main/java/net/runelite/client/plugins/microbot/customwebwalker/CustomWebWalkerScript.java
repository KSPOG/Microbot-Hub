package net.runelite.client.plugins.microbot.customwebwalker;

import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;


import net.runelite.client.plugins.microbot.util.walker.CustomWebWalker;


import net.runelite.client.plugins.microbot.util.walker.WalkerState;

@Slf4j
public class CustomWebWalkerScript extends Script {

    private final CustomWebWalkerConfig config;

    @Inject
    public CustomWebWalkerScript(CustomWebWalkerConfig config) {
        this.config = config;
    }

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }
                if (!super.run()) {
                    return;
                }

                WorldPoint destination = new WorldPoint(
                        config.targetX(),
                        config.targetY(),
                        config.targetPlane()
                );

                if (destination.distanceTo(Rs2Player.getWorldLocation()) <= config.reachedDistance()) {
                    return;
                }

                WalkerState result = CustomWebWalker.walkTo(
                        destination,
                        config.reachedDistance(),
                        config.timeoutMs()
                );
                log.info("Custom web walker finished with state {}", result);
            } catch (Exception ex) {
                log.trace("Exception in CustomWebWalkerScript loop: ", ex);
            }
        }, 0, 1, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
