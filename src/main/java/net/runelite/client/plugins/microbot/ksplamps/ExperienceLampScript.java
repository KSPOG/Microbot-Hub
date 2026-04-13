package net.runelite.client.plugins.microbot.ksplamps;

import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

@Slf4j
public class ExperienceLampScript extends Script {
    private static final String CONFIRM_TEXT = "Confirm";
    private static final String LAMP_ACTION_RUB = "Rub";
    private static final String LAMP_ACTION_USE = "Use";

    private final ExperienceLampConfig config;

    @Inject
    public ExperienceLampScript(ExperienceLampConfig config) {
        this.config = config;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }
                if (!super.run()) {
                    return;
                }
                handleLamp();
            } catch (Exception ex) {
                log.trace("Exception in experience lamp loop: ", ex);
            }
        }, 0, 1200, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleLamp() {
        if (Rs2Inventory.get(item -> item != null && item.getName() != null && item.getName().toLowerCase().contains("lamp")) == null) {
            return;
        }

        Skill targetSkill = resolveTargetSkill();
        if (targetSkill == null) {
            return;
        }

        if (Rs2Widget.hasWidget(CONFIRM_TEXT)) {
            if (Rs2Widget.hasWidget(targetSkill.getName())) {
                Rs2Widget.clickWidget(targetSkill.getName());
                sleep(200, 400);
            }
            Rs2Widget.clickWidget(CONFIRM_TEXT);
            sleep(600, 900);
            return;
        }

        if (!Rs2Inventory.interact(item -> item != null
                && item.getName() != null
                && item.getName().toLowerCase().contains("lamp"), LAMP_ACTION_RUB)) {
            Rs2Inventory.interact(item -> item != null
                    && item.getName() != null
                    && item.getName().toLowerCase().contains("lamp"), LAMP_ACTION_USE);
        }
        sleepUntil(() -> Rs2Widget.hasWidget(CONFIRM_TEXT), 2000);
    }

    private Skill resolveTargetSkill() {
        LampSkillOption option = config.targetSkill();
        if (option == LampSkillOption.LOWEST) {
            return getLowestSkill();
        }
        return option.getSkill();
    }

    private Skill getLowestSkill() {
        return Microbot.getClientThread().invoke(() -> {
            Client client = Microbot.getClient();
            Skill lowestSkill = null;
            int lowestLevel = Integer.MAX_VALUE;
            for (LampSkillOption option : LampSkillOption.values()) {
                Skill skill = option.getSkill();
                if (skill == null) {
                    continue;
                }
                int level = client.getRealSkillLevel(skill);
                if (level < lowestLevel) {
                    lowestLevel = level;
                    lowestSkill = skill;
                }
            }
            return lowestSkill;
        });
    }
}