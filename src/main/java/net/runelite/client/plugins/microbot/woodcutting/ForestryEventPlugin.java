package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.api.GameObject;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;

import java.util.List;

public interface ForestryEventPlugin {
    boolean isEnabled();

    void setCurrentForestryEvent(ForestryEvents event);

    ForestryEvents getCurrentForestryEvent();

    WoodcuttingTree getSelectedTree();

    boolean ensureInventorySpace(int requiredSlots);

    void incrementForestryEventCompleted();

    List<Rs2NpcModel> getRitualCircles();

    GameObject[] getSaplingOrder();

    List<GameObject> getSaplingIngredients();

    Rs2TileObjectCache getTileObjectCache();
}
