package net.runelite.client.plugins.microbot.f2pfishing;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.autofishing.enums.FishingMethod;

import java.util.List;

@Getter
public enum F2pFishingFish {
    SHRIMP_AND_ANCHOVIES("Shrimp + Anchovies", Fish.SHRIMP_AND_ANCHOVIES),
    SARDINE("Sardine", Fish.SARDINE),
    HERRING("Herring", Fish.HERRING),
    TROUT_AND_SALMON("Trout + Salmon", Fish.TROUT_AND_SALMON),
    PIKE("Pike", Fish.PIKE),
    LOBSTER("Lobster", Fish.LOBSTER),
    TUNA_AND_SWORDFISH("Tuna + Swordfish", Fish.TUNA_AND_SWORDFISH);

    private final String displayName;
    private final Fish fish;

    F2pFishingFish(String displayName, Fish fish) {
        this.displayName = displayName;
        this.fish = fish;
    }

    public List<String> getItemNames() {
        return fish.getItemNames();
    }

    public List<String> getActions() {
        return fish.getActions();
    }

    public List<String> getRequiredItems() {
        return fish.getRequiredItems();
    }

    public int[] getFishingSpotIds() {
        return fish.getFishingSpot();
    }

    public WorldPoint getClosestLocation(WorldPoint playerLocation) {
        return fish.getClosestLocation(playerLocation);
    }

    public int getLevelRequired() {
        FishingMethod method = fish.getMethod();
        return method != null ? method.getLevelRequired() : 1;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
