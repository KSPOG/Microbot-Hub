package net.runelite.client.plugins.microbot.kfires;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.*;

@ConfigGroup("kspfiremaking")
public interface KspFiremakingConfig extends Config {
    @ConfigItem(keyName="log", name="Log", description="Log type to burn", position=0)
    default Log log() { return Log.LOGS; }

    enum Log {
        LOGS(ItemID.LOGS,"Logs"), ACHEY(ItemID.ACHEY_TREE_LOGS,"Achey tree logs"),
        OAK(ItemID.OAK_LOGS,"Oak logs"), WILLOW(ItemID.WILLOW_LOGS,"Willow logs"),
        TEAK(ItemID.TEAK_LOGS,"Teak logs"), JATOBA(ItemID.JATOBA_LOGS,"Jatoba logs"),
        ARCTIC_PINE(ItemID.ARCTIC_PINE_LOG,"Arctic pine logs"), MAPLE(ItemID.MAPLE_LOGS,"Maple logs"),
        MAHOGANY(ItemID.MAHOGANY_LOGS,"Mahogany logs"), YEW(ItemID.YEW_LOGS,"Yew logs"),
        BLISTERWOOD(ItemID.BLISTERWOOD_LOGS,"Blisterwood logs"), CAMPHOR(ItemID.CAMPHOR_LOGS,"Camphor logs"),
        MAGIC(ItemID.MAGIC_LOGS,"Magic logs"), IRONWOOD(ItemID.IRONWOOD_LOGS,"Ironwood logs"),
        REDWOOD(ItemID.REDWOOD_LOGS,"Redwood logs"), ROSEWOOD(ItemID.ROSEWOOD_LOGS,"Rosewood logs");

        final int id; final String name;
        Log(int id, String name) { this.id=id; this.name=name; }
        @Override public String toString() { return name; }
    }
}
