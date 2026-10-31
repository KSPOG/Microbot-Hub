package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.config.ConfigManager;

/** Per-profile, per-equipment-slot loadout. Does not change saved slots implicitly. */
final class KspRuniteMinerKillerGearStore {
    static final String GROUP="KspRuniteMinerKiller";
    /** The fastest permitted setting is a burst rate, not a guaranteed lockout-safe rate. */
    static final int MIN_HOP_DELAY_SECONDS = 10;
    static final int MAX_HOP_DELAY_SECONDS = 600;
    /** 90 hops/hour at this setting, just below the community-estimated 93/hour recovery. */
    static final int RECOMMENDED_HOP_DELAY_SECONDS = 40;
    static final EquipmentInventorySlot[] SLOTS={
        EquipmentInventorySlot.HEAD,EquipmentInventorySlot.CAPE,EquipmentInventorySlot.AMULET,
        EquipmentInventorySlot.AMMO,EquipmentInventorySlot.WEAPON,EquipmentInventorySlot.BODY,
        EquipmentInventorySlot.SHIELD,EquipmentInventorySlot.LEGS,EquipmentInventorySlot.GLOVES,
        EquipmentInventorySlot.BOOTS,EquipmentInventorySlot.RING};
    private final ConfigManager config;
    KspRuniteMinerKillerGearStore(ConfigManager config) {this.config=config;}
    String get(EquipmentInventorySlot slot) {
        String s=config.getConfiguration(GROUP,"gear."+slot.name().toLowerCase());
        return s==null?"":s.trim();
    }
    int itemId(EquipmentInventorySlot slot) {
        String s=config.getConfiguration(GROUP,"gearId."+slot.name().toLowerCase());
        if (s==null)return -1;
        try{return Integer.parseInt(s);}catch(NumberFormatException e){return -1;}
    }
    int itemQuantity(EquipmentInventorySlot slot) {
        String value=config.getConfiguration(GROUP,"gearQty."+slot.name().toLowerCase());
        if(value==null)return 1;
        try{return Math.max(1,Math.min(1000000,Integer.parseInt(value)));}
        catch(NumberFormatException e){return 1;}
    }
    void set(EquipmentInventorySlot slot,String name,int id) {
        set(slot,name,id,1);
    }
    void set(EquipmentInventorySlot slot,String name,int id,int quantity) {
        String qtyKey="gearQty."+slot.name().toLowerCase();
        String key="gear."+slot.name().toLowerCase();
        String idKey="gearId."+slot.name().toLowerCase();
        if(name==null || name.isBlank()){
            config.unsetConfiguration(GROUP,key);config.unsetConfiguration(GROUP,idKey);
            config.unsetConfiguration(GROUP,qtyKey);
        }else{
            config.setConfiguration(GROUP,key,name.trim());
            if(id>0) config.setConfiguration(GROUP,idKey,id);
            else config.unsetConfiguration(GROUP,idKey);
            if(slot==EquipmentInventorySlot.AMMO && quantity>1)
                config.setConfiguration(GROUP,qtyKey,Math.min(1000000,quantity));
            else config.unsetConfiguration(GROUP,qtyKey);
        }
    }
    void replace(Map<EquipmentInventorySlot,GearItem> equipped) {
        if(equipped==null) throw new IllegalArgumentException("Equipment snapshot missing");
        for(EquipmentInventorySlot slot:SLOTS){
            if(!equipped.containsKey(slot))
                throw new IllegalArgumentException("Incomplete equipment snapshot: " + slot.name());
            GearItem item=equipped.get(slot);
            if(item==null || item.name==null || (!item.name.isBlank() && item.id<=0))
                throw new IllegalArgumentException("Invalid equipment snapshot: " + slot.name());
        }
        for(EquipmentInventorySlot slot:SLOTS){
            GearItem item=equipped.get(slot);
            set(slot,item.name,item.id,item.quantity);
        }
    }
    boolean hopEnabled(){return Boolean.parseBoolean(getOrDefault("hop.enabled","true"));}
    void hopEnabled(boolean enabled){config.setConfiguration(GROUP,"hop.enabled",enabled);}
    int hopDelay(){
        try {return clampHopDelay(Integer.parseInt(getOrDefault("hop.waitSeconds","90")));}
        catch(NumberFormatException ignored){return 90;}
    }
    void hopDelay(int seconds){config.setConfiguration(GROUP,"hop.waitSeconds",clampHopDelay(seconds));}
    static int clampHopDelay(int seconds){
        return Math.max(MIN_HOP_DELAY_SECONDS,Math.min(MAX_HOP_DELAY_SECONDS,seconds));
    }
    private String getOrDefault(String key,String fallback){String s=config.getConfiguration(GROUP,key);return s==null?fallback:s;}
    static final class GearItem {
        final String name; final int id; final int quantity;
        GearItem(String n,int i){this(n,i,1);}
        GearItem(String n,int i,int q){name=n;id=i;quantity=Math.max(1,q);}
    }
}
