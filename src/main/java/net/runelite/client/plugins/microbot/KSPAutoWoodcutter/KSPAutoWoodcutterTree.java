package net.runelite.client.plugins.microbot.KSPAutoWoodcutter;

import net.runelite.api.gameval.ItemID;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public enum KSPAutoWoodcutterTree {
    TREE("Tree", "Logs", ItemID.LOGS, 1),
    OAK("Oak", "Oak logs", ItemID.OAK_LOGS, 15),
    WILLOW("Willow", "Willow logs", ItemID.WILLOW_LOGS, 30),
    TEAK("Teak", "Teak logs", ItemID.TEAK_LOGS, 35),
    MAPLE("Maple tree", "Maple logs", ItemID.MAPLE_LOGS, 45),
    MAHOGANY("Mahogany", "Mahogany logs", ItemID.MAHOGANY_LOGS, 50),
    YEW("Yew", "Yew logs", ItemID.YEW_LOGS, 60),
    MAGIC("Magic tree", "Magic logs", ItemID.MAGIC_LOGS, 75),
    REDWOOD("Redwood", "Redwood logs", ItemID.REDWOOD_LOGS, 90);

    private static final List<KSPAutoWoodcutterTree> PROGRESSIVE_ORDER = Arrays.asList(
            TREE,
            OAK,
            WILLOW,
            TEAK,
            MAPLE,
            MAHOGANY,
            YEW,
            MAGIC,
            REDWOOD
    );

    private final String objectName;
    private final String logName;
    private final int logId;
    private final int woodcuttingLevel;

    KSPAutoWoodcutterTree(String objectName, String logName, int logId, int woodcuttingLevel) {
        this.objectName = objectName;
        this.logName = logName;
        this.logId = logId;
        this.woodcuttingLevel = woodcuttingLevel;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getLogName() {
        return logName;
    }

    public int getLogId() {
        return logId;
    }

    public int getWoodcuttingLevel() {
        return woodcuttingLevel;
    }

    public static KSPAutoWoodcutterTree resolveForLevel(int woodcuttingLevel) {
        return PROGRESSIVE_ORDER.stream()
                .filter(tree -> woodcuttingLevel >= tree.woodcuttingLevel)
                .max(Comparator.comparingInt(KSPAutoWoodcutterTree::getWoodcuttingLevel))
                .orElse(TREE);
    }

    @Override
    public String toString() {
        return objectName.replace(" tree", "");
    }
}
