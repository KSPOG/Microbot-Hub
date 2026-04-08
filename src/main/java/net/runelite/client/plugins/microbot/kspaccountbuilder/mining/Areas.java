package net.runelite.client.plugins.microbot.kspaccountbuilder.mining;

public final class Areas {
    public static final Area COPPER_TIN_AREA = new Area(3280, 3371, 3291, 3360);
    public static final Area IRON_AREA = new Area(3284, 3370, 3289, 3366);
    public static final Area CLAY_AREA = new Area(3184, 3363, 3171, 3379);
    public static final Area SILVER_ORE_AREA = new Area(3170, 3370, 3178, 3364);
    public static final Area COAL_AREA = new Area(3077, 3422, 3084, 3417);
    public static final Area GOLD_ORE_AREA = new Area(3292, 3289, 3295, 3285);

    private Areas() {
    }

    public static final class Area {
        private final int x1;
        private final int y1;
        private final int x2;
        private final int y2;

        public Area(int x1, int y1, int x2, int y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }


        public int getX1() { return x1; }
        public int getY1() { return y1; }
        public int getX2() { return x2; }
        public int getY2() { return y2; }

        public int getX1() {
            return x1;
        }

        public int getY1() {
            return y1;
        }

        public int getX2() {
            return x2;
        }

        public int getY2() {
            return y2;
        }

    }
}
