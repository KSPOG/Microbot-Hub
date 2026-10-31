package net.runelite.client.plugins.microbot.kspauburnvaleplankmaker;

import lombok.Getter;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;

/**
 * Explicit sawmill recipe mapping.
 *
 * INPUT  = logs consumed by the sawmill.
 * OUTPUT = planks produced by the sawmill.
 *
 * Keep the names explicit to prevent log/plank role inversion in state logic.
 */
@Getter
public enum PlankType {
    REGULAR("Logs", ItemID.LOGS, "Plank", ItemID.WOODPLANK, 100, InterfaceID.Skillmulti.A),
    OAK("Oak logs", ItemID.OAK_LOGS, "Oak plank", ItemID.PLANK_OAK, 250, InterfaceID.Skillmulti.B),
    TEAK("Teak logs", ItemID.TEAK_LOGS, "Teak plank", ItemID.PLANK_TEAK, 500, InterfaceID.Skillmulti.C),
    MAHOGANY("Mahogany logs", ItemID.MAHOGANY_LOGS, "Mahogany plank", ItemID.PLANK_MAHOGANY, 1_500, InterfaceID.Skillmulti.D),
    CAMPHOR("Camphor logs", ItemID.CAMPHOR_LOGS, "Camphor plank", ItemID.PLANK_CAMPHOR, 2_500, InterfaceID.Skillmulti.E),
    IRONWOOD("Ironwood logs", ItemID.IRONWOOD_LOGS, "Ironwood plank", ItemID.PLANK_IRONWOOD, 5_000, InterfaceID.Skillmulti.F),
    ROSEWOOD("Rosewood logs", ItemID.ROSEWOOD_LOGS, "Rosewood plank", ItemID.PLANK_ROSEWOOD, 7_500, InterfaceID.Skillmulti.G);

    private final String inputLogName;
    private final int inputLogId;
    private final String outputPlankName;
    private final int outputPlankId;
    private final int sawmillFee;
    private final int productionComponentId;

    PlankType(String inputLogName, int inputLogId,
              String outputPlankName, int outputPlankId,
              int sawmillFee, int productionComponentId) {
        this.inputLogName = inputLogName;
        this.inputLogId = inputLogId;
        this.outputPlankName = outputPlankName;
        this.outputPlankId = outputPlankId;
        this.sawmillFee = sawmillFee;
        this.productionComponentId = productionComponentId;
    }
}
