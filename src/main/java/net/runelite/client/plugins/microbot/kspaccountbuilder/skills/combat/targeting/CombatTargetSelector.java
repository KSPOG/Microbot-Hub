package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.targeting;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;

public class CombatTargetSelector {

    public Rs2NpcModel getBestAttackableNpc(int radius, BooleanSupplier inTrainingAreaSupplier, String... names) {
        List<String> targetNames = Arrays.asList(names);

        return Rs2Npc.getAttackableNpcs(false)
                .filter(npc -> npc != null && npc.getName() != null)
                .filter(npc -> targetNames.stream().anyMatch(name -> name.equalsIgnoreCase(npc.getName())))
                .filter(npc -> npc.getWorldLocation() != null)
                .filter(npc -> npc.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= radius
                        || inTrainingAreaSupplier.getAsBoolean())
                .sorted(Comparator
                        .comparingInt((Rs2NpcModel npc) -> npc.getInteracting() == Microbot.getClient().getLocalPlayer() ? 0 : 1)
                        .thenComparingInt(npc -> npc.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                .findFirst()
                .orElse(null);
    }
}
