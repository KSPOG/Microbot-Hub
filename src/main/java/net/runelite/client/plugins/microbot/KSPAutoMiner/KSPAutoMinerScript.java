package net.runelite.client.plugins.microbot.KSPAutoMiner;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class KSPAutoMinerScript extends Script {
    private static final WorldPoint START_LOCATION = new WorldPoint(3284, 3365, 0);
    private static final WorldPoint COAL_LOCATION = new WorldPoint(3303, 3300, 0);
    private static final WorldPoint GOLD_LOCATION = new WorldPoint(3294, 3286, 0);
    private static final WorldPoint MITHRIL_LOCATION = new WorldPoint(3304, 3304, 0);
    private static final WorldPoint ADAMANT_LOCATION = new WorldPoint(3299, 3317, 0);

    private static final List<String> ALL_ORES = Arrays.asList(
            "Clay",
            "Tin ore",
            "Copper ore",
            "Iron ore",
            "Silver ore",
            "Coal",
            "Gold ore",
            "Mithril ore",
            "Adamantite ore",
            "Runite ore"
    );

    public static String status = "Idle";
    public static String modeLabel = "";
    public static int oresMined = 0;

    private static long startTimeMs;
    private int lastInventoryCount;
    private int tinMined;
    private int copperMined;
    private int lastTinCount;
    private int lastCopperCount;
    private MiningStage stage = MiningStage.TIN_COPPER;

    private Rock targetRock;
    private KSPAutoMinerRock selectedRock;
    private WorldPoint targetLocation;

    public boolean run(KSPAutoMinerConfig config) {
        startTimeMs = System.currentTimeMillis();
        oresMined = 0;
        tinMined = 0;
        copperMined = 0;
        lastTinCount = 0;
        lastCopperCount = 0;
        lastInventoryCount = 0;
        status = "Starting";
        modeLabel = config.mode().toString();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    return;
                }
                if (!Microbot.isLoggedIn()) {
                    return;
                }
                if (Rs2AntibanSettings.actionCooldownActive) {
                    return;
                }

                KSPAutoMinerMode mode = config.mode();
                modeLabel = mode.toString();
                updateTarget(mode, config.rock());
                updateOreCount();

                if (targetRock == null || targetLocation == null) {
                    status = "No target location";
                    return;
                }

                if (!targetRock.hasRequiredLevel()) {
                    status = "Mining level too low";
                    return;
                }

                if (Rs2Inventory.isFull()) {
                    if (mode.isBankingMode()) {
                        status = "Banking";
                        if (!Rs2Bank.bankItemsAndWalkBackToOriginalPosition(ALL_ORES, targetLocation)) {
                            return;
                        }
                    } else {
                        status = "Dropping ores";
                        dropOres();
                    }
                    return;
                }

                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
                    return;
                }

                if (Rs2Player.getWorldLocation().distanceTo(targetLocation) > 6) {
                    status = "Walking to rocks";
                    Rs2Walker.walkTo(targetLocation);
                    return;
                }

                GameObject rock = Rs2GameObject.findReachableObject(targetRock.getName(), true, 12, targetLocation);
                if (rock == null) {
                    status = "No rocks found";
                    return;
                }

                status = "Mining " + targetRock.getName();
                if (Rs2GameObject.interact(rock)) {
                    Rs2Player.waitForXpDrop(Skill.MINING, true);
                }
            } catch (Exception ex) {
                Microbot.log("KSPAutoMiner error: " + ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        status = "Stopped";
    }

    public static Duration getRuntime() {
        if (startTimeMs == 0) {
            return Duration.ZERO;
        }
        long elapsed = System.currentTimeMillis() - startTimeMs;
        return Duration.ofMillis(elapsed);
    }

    private void updateTarget(KSPAutoMinerMode mode, KSPAutoMinerRock rockSelection) {
        int miningLevel = Microbot.getClient().getRealSkillLevel(Skill.MINING);
        if (rockSelection != selectedRock) {
            selectedRock = rockSelection;
            resetOreCounters();
        }

        MiningStage newStage = determineStage(mode, miningLevel);
        if (newStage != stage) {
            stage = newStage;
            resetOreCounters();
        }

        if (!mode.isProgressiveMode()) {
            targetLocation = START_LOCATION;
            targetRock = mapRockSelection(rockSelection);
            return;
        }

        switch (stage) {
            case TIN_COPPER:
                targetLocation = START_LOCATION;
                targetRock = selectBalancedTinCopper();
                break;
            case IRON:
                targetLocation = START_LOCATION;
                targetRock = Rock.IRON;
                break;
            case COAL:
                targetLocation = COAL_LOCATION;
                targetRock = Rock.COAL;
                break;
            case GOLD:
                targetLocation = GOLD_LOCATION;
                targetRock = Rock.GOLD;
                break;
            case MITHRIL:
                targetLocation = MITHRIL_LOCATION;
                targetRock = Rock.MITHRIL;
                break;
            case ADAMANT:
                targetLocation = ADAMANT_LOCATION;
                targetRock = Rock.ADAMANTITE;
                break;
        }
    }

    private MiningStage determineStage(KSPAutoMinerMode mode, int miningLevel) {
        if (!mode.isProgressiveMode()) {
            return miningLevel < 15 ? MiningStage.TIN_COPPER : MiningStage.IRON;
        }

        if (miningLevel < 15) {
            return MiningStage.TIN_COPPER;
        }
        if (miningLevel < 30) {
            return MiningStage.IRON;
        }
        if (miningLevel < 40) {
            return MiningStage.COAL;
        }
        if (miningLevel < 55) {
            return MiningStage.GOLD;
        }
        if (miningLevel < 70) {
            return MiningStage.MITHRIL;
        }
        return MiningStage.ADAMANT;
    }

    private Rock selectBalancedTinCopper() {
        if (tinMined <= copperMined) {
            return Rock.TIN;
        }
        return Rock.COPPER;
    }

    private Rock mapRockSelection(KSPAutoMinerRock rockSelection) {
        if (rockSelection == null) {
            return Rock.TIN;
        }

        switch (rockSelection) {
            case CLAY:
                return Rock.CLAY;
            case COPPER_TIN:
                return selectBalancedTinCopper();
            case IRON:
                return Rock.IRON;
            case SILVER:
                return Rock.SILVER;
            case COAL:
                return Rock.COAL;
            case GOLD:
                return Rock.GOLD;
            case MITHRIL:
                return Rock.MITHRIL;
            case ADAMANT:
                return Rock.ADAMANTITE;
            case RUNE:
                return Rock.RUNITE;
            default:
                return Rock.TIN;
        }
    }

    private void updateOreCount() {
        if (stage == MiningStage.TIN_COPPER || targetRock == Rock.TIN || targetRock == Rock.COPPER) {
            int tinCount = Rs2Inventory.count("Tin ore");
            int copperCount = Rs2Inventory.count("Copper ore");
            int tinDelta = tinCount - lastTinCount;
            int copperDelta = copperCount - lastCopperCount;

            if (tinDelta > 0) {
                tinMined += tinDelta;
                oresMined += tinDelta;
            }
            if (copperDelta > 0) {
                copperMined += copperDelta;
                oresMined += copperDelta;
            }

            lastTinCount = tinCount;
            lastCopperCount = copperCount;
            lastInventoryCount = tinCount + copperCount;
            return;
        }

        int currentCount = countRelevantOres();
        int delta = currentCount - lastInventoryCount;
        if (delta > 0) {
            oresMined += delta;
        }
        lastInventoryCount = currentCount;
    }

    private int countRelevantOres() {
        if (targetRock == null) {
            return 0;
        }

        if (targetRock == Rock.TIN || targetRock == Rock.COPPER) {
            return Rs2Inventory.count("Tin ore") + Rs2Inventory.count("Copper ore");
        }

        return Rs2Inventory.count(targetRock.getOreName());
    }

    private void resetOreCounters() {
        lastInventoryCount = countRelevantOres();
        lastTinCount = Rs2Inventory.count("Tin ore");
        lastCopperCount = Rs2Inventory.count("Copper ore");
    }

    private void dropOres() {
        for (String ore : ALL_ORES) {
            if (Rs2Inventory.hasItem(ore, false)) {
                Rs2Inventory.dropAll(ore);
            }
        }
    }

    private enum MiningStage {
        TIN_COPPER,
        IRON,
        COAL,
        GOLD,
        MITHRIL,
        ADAMANT
    }

    private enum Rock {
        CLAY("Clay rocks", "Clay", 1),
        TIN("Tin rocks", "Tin ore", 1),
        COPPER("Copper rocks", "Copper ore", 1),
        IRON("Iron rocks", "Iron ore", 15),
        SILVER("Silver rocks", "Silver ore", 20),
        COAL("Coal rocks", "Coal", 30),
        GOLD("Gold rocks", "Gold ore", 40),
        MITHRIL("Mithril rocks", "Mithril ore", 55),
        ADAMANTITE("Adamantite rocks", "Adamantite ore", 70),
        RUNITE("Runite rocks", "Runite ore", 85);

        private final String name;
        private final String oreName;
        private final int miningLevel;

        Rock(String name, String oreName, int miningLevel) {
            this.name = name;
            this.oreName = oreName;
            this.miningLevel = miningLevel;
        }

        public String getName() {
            return name;
        }

        public String getOreName() {
            return oreName;
        }

        public boolean hasRequiredLevel() {
            return Microbot.getClient().getRealSkillLevel(Skill.MINING) >= miningLevel;
        }
    }

}