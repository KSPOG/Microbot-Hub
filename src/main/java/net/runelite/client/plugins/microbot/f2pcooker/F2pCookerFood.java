package net.runelite.client.plugins.microbot.f2pcooker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum F2pCookerFood {
    SHRIMP("Shrimp", 317, 1),
    ANCHOVIES("Anchovies", 321, 1),
    SARDINE("Sardine", 327, 1),
    HERRING("Herring", 345, 5),
    TROUT("Trout", 335, 15),
    PIKE("Pike", 349, 20),
    SALMON("Salmon", 331, 25),
    TUNA("Tuna", 359, 30),
    LOBSTER("Lobster", 377, 40),
    SWORDFISH("Swordfish", 371, 45);

    private final String name;
    private final int rawItemId;
    private final int requiredLevel;

    private static final List<F2pCookerFood> LEVEL_ORDERED = Arrays.stream(values())
            .sorted(Comparator.comparingInt(F2pCookerFood::getRequiredLevel))
            .collect(Collectors.toList());

    public static List<F2pCookerFood> getLevelOrdered() {
        return LEVEL_ORDERED;
    }

    @Override
    public String toString() {
        return String.format("%s (lvl %d)", name, requiredLevel);
    }
}
