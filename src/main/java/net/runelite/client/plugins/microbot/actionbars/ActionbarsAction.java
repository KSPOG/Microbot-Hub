package net.runelite.client.plugins.microbot.actionbars;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

public class ActionbarsAction {
    private final ActionbarsActionType type;
    private final String payload;

    public ActionbarsAction(ActionbarsActionType type, String payload) {
        this.type = type == null ? ActionbarsActionType.NONE : type;
        this.payload = payload == null ? "" : payload.trim();
    }

    public ActionbarsActionType getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public String getFallbackLabel() {
        switch (type) {
            case EAT_FOOD:
                return "Eat";
            case EAT_FAST_FOOD:
                return "Fast Eat";
            case DRINK_PRAYER_POTION:
                return "Prayer";
            case TOGGLE_SPEC:
                return "Spec";
            case TOGGLE_PRAYER:
                return payload.isBlank() ? "Prayer" : payload.replace('_', ' ');
            default:
                return "";
        }
    }

    public void execute() {
        switch (type) {
            case EAT_FOOD:
                Microbot.getClientThread().runOnSeperateThread(() -> {
                    Rs2Player.useFood();
                    return null;
                });
                break;
            case EAT_FAST_FOOD:
                Microbot.getClientThread().runOnSeperateThread(() -> {
                    Rs2Player.useFastFood();
                    return null;
                });
                break;
            case DRINK_PRAYER_POTION:
                Microbot.getClientThread().runOnSeperateThread(() -> {
                    Rs2Player.drinkPrayerPotion();
                    return null;
                });
                break;
            case TOGGLE_SPEC:
                Microbot.getClientThread().runOnSeperateThread(() -> {
                    Rs2Combat.setSpecState(!Rs2Combat.getSpecState());
                    return null;
                });
                break;
            case TOGGLE_PRAYER:
                Rs2PrayerEnum prayer = resolvePrayer();
                if (prayer != null) {
                    Rs2Prayer.toggle(prayer);
                }
                break;
            case NONE:
            default:
                break;
        }
    }

    private Rs2PrayerEnum resolvePrayer() {
        if (payload.isBlank()) {
            return null;
        }
        String normalized = payload.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        try {
            return Rs2PrayerEnum.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
