package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntFunction;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;

/** Complete, live WORN-container snapshot. Both occupied and empty configured slots are represented. */
final class KspRuniteMinerKillerEquipmentSnapshot {
    private KspRuniteMinerKillerEquipmentSnapshot() {}

    static Map<EquipmentInventorySlot,KspRuniteMinerKillerGearStore.GearItem> read(
            Item[] worn, IntFunction<ItemComposition> definitions) {
        if (worn == null) throw new IllegalStateException("Worn equipment container is unavailable");
        EnumMap<EquipmentInventorySlot,KspRuniteMinerKillerGearStore.GearItem> result =
                new EnumMap<>(EquipmentInventorySlot.class);
        for (EquipmentInventorySlot slot : KspRuniteMinerKillerGearStore.SLOTS) {
            int index=slot.getSlotIdx();
            // OSRS WORN containers can omit trailing empty slots. For example an
            // 8-entry array means slots 8..13 are not worn, NOT an error.
            // The slot index is stable even when the array is short.
            if (index < 0) throw new IllegalArgumentException("Invalid worn slot index: " + slot);
            Item item = index >= worn.length ? null : worn[index];
            if(item==null || item.getId()<0){
                result.put(slot,new KspRuniteMinerKillerGearStore.GearItem("",-1));
                continue;
            }
            int id=item.getId();
            ItemComposition composition=definitions.apply(id);
            String name=composition==null?null:composition.getName();
            if(name==null || name.trim().isEmpty() || name.equalsIgnoreCase("null"))
                throw new IllegalStateException("Unable to resolve " + slot.name()+ " equipment item ID " + id);
            result.put(slot,new KspRuniteMinerKillerGearStore.GearItem(name.trim(),id,
                    slot==EquipmentInventorySlot.AMMO ? Math.max(1,item.getQuantity()) : 1));
        }
        return result;
    }

    static int occupiedCount(Map<EquipmentInventorySlot,KspRuniteMinerKillerGearStore.GearItem> equipment){
        int n=0;
        for(EquipmentInventorySlot slot:KspRuniteMinerKillerGearStore.SLOTS){
            KspRuniteMinerKillerGearStore.GearItem g=equipment.get(slot);
            if(g!=null && g.id>0 && !g.name.isBlank())n++;
        }
        return n;
    }
}
