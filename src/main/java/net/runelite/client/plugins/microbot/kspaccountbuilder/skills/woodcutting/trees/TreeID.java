package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.trees;

/**
 * Common woodcutting tree object ids grouped by tree type.
 */
public final class TreeID {
    private TreeID() {
    }

    public static final int[] NORMAL = {
            1276, 1277, 1278, 1279, 1280, 1282, 1283, 1284, 1285, 1286,
            3033, 3881, 3882, 3883, 5902, 10041, 10042
    };

    public static final int[] OAK = {
            4540, 10820, 10821, 4541
    };

    public static final int[] WILLOW = {
            10829, 10830, 10831, 10832, 10833, 10834, 10835, 38616
    };

    public static final int[] TEAK = {
            9036, 9037, 9038, 36686
    };

    public static final int[] MAPLE = {
            10832, 36681
    };

    public static final int[] MAHOGANY = {
            9034, 9035, 36688
    };

    public static final int[] YEW = {
            10822, 10823, 36683
    };

    public static final int[] MAGIC = {
            10834, 10835, 36686
    };

    public static final int[] REDWOOD = {
            29668, 29670
    };

    public static boolean contains(int[] ids, int id) {
        for (int treeId : ids) {
            if (treeId == id) {
                return true;
            }
        }
        return false;
    }
}
