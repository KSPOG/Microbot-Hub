package net.runelite.client.plugins.microbot.kspconstruction;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class KspConstructionScript extends Script
{
    private static final int LOOP_DELAY_MS = 180;
    private static final int CONTROL_SETTINGS_WIDGET_ID = 7602235;
    private static final int HOUSE_OPTIONS_WIDGET_ID = 7602207;
    private static final int LEAVE_HOUSE_WIDGET_ID = 24248341;
    private static final int CALL_SERVANT_WIDGET_ID = 24248342;
    private static final int DEFAULT_SERVANT_FETCH_AMOUNT = 24;
    private static final long DEFAULT_SERVANT_TRIP_WAIT_MS = 8_500L;

    private static final String[] SERVANT_NAMES = {
            "Demon butler",
            "Butler",
            "Cook",
            "Maid",
            "Rick"
    };

    private enum BankStage
    {
        NONE,
        EXIT_HOUSE,
        WALK_TO_BANK,
        WITHDRAW_PLANKS,
        RETURN_TO_PORTAL,
        ENTER_BUILD_MODE
    }

    private volatile ConstructionState state = ConstructionState.STARTING;
    private volatile String status = "Starting";
    private volatile String lastError = "";
    private volatile int furnitureBuilt;

    private KspConstructionConfig config;
    private WorldPoint workingTile;
    private WorldPoint exteriorPortalTile;
    private BankStage bankStage = BankStage.NONE;
    private long servantTripStartedAt;
    private long servantTripWaitMs = DEFAULT_SERVANT_TRIP_WAIT_MS;
    private int targetMisses;
    private int lastPlankCount;

    public boolean run(KspConstructionConfig config)
    {
        this.config = config;
        this.state = ConstructionState.STARTING;
        this.status = "Start in Build Mode beside the selected hotspot";
        this.lastError = "";
        this.furnitureBuilt = 0;
        this.workingTile = null;
        this.exteriorPortalTile = null;
        this.bankStage = BankStage.NONE;
        this.servantTripStartedAt = 0L;
        this.servantTripWaitMs = DEFAULT_SERVANT_TRIP_WAIT_MS;
        this.targetMisses = 0;
        this.lastPlankCount = 0;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (!Microbot.isLoggedIn())
                {
                    return;
                }
                if (!super.run())
                {
                    return;
                }

                tick();
            }
            catch (Exception ex)
            {
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
                fail("Unexpected error: " + ex.getMessage());
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void tick()
    {
        KspConstructionConfig.FurnitureMode mode = config.furnitureMode();
        int currentPlanks = Rs2Inventory.count(mode.getPlankItemId());
        if (currentPlanks > lastPlankCount)
        {
            servantTripStartedAt = 0L;
        }
        lastPlankCount = currentPlanks;

        if (bankStage != BankStage.NONE)
        {
            handleBankTrip(mode);
            return;
        }

        if (handleAmountInputIfPresent(mode))
        {
            return;
        }

        if (Rs2Dialogue.isInDialogue() && !isRemoveConfirmation())
        {
            state = ConstructionState.SERVANT_DIALOGUE;
            status = "Handling servant dialogue";
            handleServantDialogue(mode);
            return;
        }

        Rs2TileObjectModel target = findWorkingObject(mode);
        if (target == null)
        {
            state = ConstructionState.SEARCHING;
            status = "Finding " + mode + " hotspot";
            targetMisses++;
            if (targetMisses >= 50)
            {
                fail("Could not find the selected furniture or hotspot. Start inside the POH in Build Mode beside it.");
            }
            return;
        }

        targetMisses = 0;

        if (target.getId() == mode.getBuiltObjectId())
        {
            removeFurniture(target, mode);
            return;
        }

        if (target.getId() != mode.getHotspotObjectId())
        {
            workingTile = null;
            return;
        }

        if (currentPlanks >= mode.getPlanksRequired())
        {
            buildFurniture(target, mode);
            return;
        }

        if (config.plankSource() == KspConstructionConfig.PlankSource.CALL_SERVANT)
        {
            resupplyWithServant(mode);
        }
        else
        {
            bankStage = BankStage.EXIT_HOUSE;
            handleBankTrip(mode);
        }
    }

    private void buildFurniture(Rs2TileObjectModel hotspot, KspConstructionConfig.FurnitureMode mode)
    {
        state = ConstructionState.BUILDING;
        status = "Building " + mode;
        Rs2Tab.switchTo(InterfaceTab.INVENTORY);

        if (!hotspot.click("Build"))
        {
            return;
        }

        if (!sleepUntil(() -> Rs2Widget.hasWidget("Furniture"), 2_000))
        {
            return;
        }

        Rs2Keyboard.keyPress(mode.getBuildKey());
        boolean built = sleepUntil(() -> {
            Rs2TileObjectModel object = getObjectOnWorkingTile(mode);
            return object != null && object.getId() == mode.getBuiltObjectId();
        }, 3_000);

        if (built)
        {
            furnitureBuilt++;
        }
    }

    private void removeFurniture(Rs2TileObjectModel furniture, KspConstructionConfig.FurnitureMode mode)
    {
        state = ConstructionState.REMOVING;
        status = "Removing " + mode;
        Rs2Tab.switchTo(InterfaceTab.INVENTORY);

        if (isRemoveConfirmation())
        {
            confirmRemoval(mode);
            return;
        }

        if (!furniture.click("Remove"))
        {
            return;
        }

        if (!sleepUntil(() -> Rs2Dialogue.isInDialogue(), 2_000))
        {
            return;
        }

        if (!isRemoveConfirmation())
        {
            // A returning servant can interrupt the remove click. Handle it, then retry next loop.
            handleServantDialogue(mode);
            return;
        }

        confirmRemoval(mode);
    }

    private void confirmRemoval(KspConstructionConfig.FurnitureMode mode)
    {
        if (!Rs2Dialogue.keyPressForDialogueOption("Yes"))
        {
            Rs2Keyboard.keyPress('1');
        }

        sleepUntil(() -> {
            Rs2TileObjectModel object = getObjectOnWorkingTile(mode);
            return object != null && object.getId() == mode.getHotspotObjectId();
        }, 3_000);
    }

    private boolean isRemoveConfirmation()
    {
        return Rs2Dialogue.hasQuestion("Really remove it?");
    }

    private void resupplyWithServant(KspConstructionConfig.FurnitureMode mode)
    {
        if (servantTripStartedAt > 0L
                && System.currentTimeMillis() - servantTripStartedAt < servantTripWaitMs)
        {
            state = ConstructionState.WAITING_FOR_SERVANT;
            status = "Waiting for servant to return";
            return;
        }

        state = ConstructionState.CALLING_SERVANT;
        status = "Calling servant for " + mode.getPlankName().toLowerCase(Locale.ROOT) + "s";

        Rs2NpcModel servant = getServant();
        if (servant == null || !servant.isWithinDistanceFromPlayer(3))
        {
            if (!openHouseOptionsAndCallServant())
            {
                return;
            }

            sleepUntil(() -> Rs2Dialogue.isInDialogue() || getServant() != null, 3_000);
            servant = getServant();
        }

        if (!Rs2Dialogue.isInDialogue() && servant != null)
        {
            servant.click("Talk-to");
            sleepUntil(Rs2Dialogue::isInDialogue, 2_500);
        }

        if (Rs2Dialogue.isInDialogue())
        {
            handleServantDialogue(mode);
        }
    }

    private boolean openHouseOptionsAndCallServant()
    {
        if (!openHouseOptions())
        {
            return false;
        }

        Widget callServant = Rs2Widget.findWidget("Call Servant", null);
        if (callServant == null)
        {
            callServant = Rs2Widget.getWidget(CALL_SERVANT_WIDGET_ID);
        }
        if (callServant == null)
        {
            status = "Could not find Call Servant";
            return false;
        }

        Rs2Widget.clickWidget(callServant);
        return true;
    }

    private boolean openHouseOptions()
    {
        Rs2Tab.switchTo(InterfaceTab.SETTINGS);

        Widget controls = Rs2Widget.getWidget(CONTROL_SETTINGS_WIDGET_ID);
        if (controls != null)
        {
            String[] actions = controls.getActions();
            boolean controlsInterfaceVisible = actions != null && actions.length == 0;
            if (!controlsInterfaceVisible)
            {
                Rs2Widget.clickWidget(controls);
                sleepUntil(() -> Rs2Widget.isWidgetVisible(HOUSE_OPTIONS_WIDGET_ID), 2_000);
            }
        }

        Widget houseOptions = Rs2Widget.findWidget("House Options", null);
        if (houseOptions == null)
        {
            houseOptions = Rs2Widget.getWidget(HOUSE_OPTIONS_WIDGET_ID);
        }
        if (houseOptions == null)
        {
            status = "Could not find House Options button";
            return false;
        }

        Rs2Widget.clickWidget(houseOptions);
        return sleepUntil(() -> Rs2Widget.findWidget("Call Servant", null) != null
                || Rs2Widget.getWidget(CALL_SERVANT_WIDGET_ID) != null
                || Rs2Widget.findWidget("Leave House", null) != null
                || Rs2Widget.getWidget(LEAVE_HOUSE_WIDGET_ID) != null, 2_000);
    }

    private boolean handleServantDialogue(KspConstructionConfig.FurnitureMode mode)
    {
        if (handleAmountInputIfPresent(mode))
        {
            return true;
        }

        String dialogueText = Rs2Dialogue.getDialogueText();
        String lowerText = dialogueText == null ? "" : dialogueText.toLowerCase(Locale.ROOT);

        if (lowerText.contains("cannot conjure items out of the air")
                || lowerText.contains("do not have any of that item"))
        {
            fail("The bank does not contain enough " + mode.getPlankName().toLowerCase(Locale.ROOT) + "s.");
            return true;
        }

        if (Rs2Dialogue.hasSelectAnOption())
        {
            if (selectOptionContaining("here's", "coins"))
            {
                return true;
            }

            // Reuse a matching remembered fetch task when one is already configured.
            if (selectOptionContaining("fetch from bank", mode.getPlankName()))
            {
                markServantTripStarted(getServant());
                state = ConstructionState.WAITING_FOR_SERVANT;
                status = "Servant fetching " + mode.getPlankName().toLowerCase(Locale.ROOT) + "s";
                return true;
            }

            if (selectOptionContaining("something else"))
            {
                return true;
            }
            if (selectOptionContaining("go to the bank"))
            {
                return true;
            }
            if (selectOptionContaining("bring something from the bank"))
            {
                return true;
            }

            if (selectOptionContaining(mode.getServantOptionName())
                    || selectOptionContaining(mode.getPlankName()))
            {
                sleepUntil(() -> Rs2Widget.hasWidget("Enter amount:"), 1_500);
                return handleAmountInputIfPresent(mode);
            }
        }

        if (lowerText.contains("i have returned")
                || lowerText.contains("what you asked me to"))
        {
            int before = Rs2Inventory.count(mode.getPlankItemId());
            Rs2Dialogue.clickContinue();
            sleepUntil(() -> Rs2Inventory.count(mode.getPlankItemId()) > before, 2_500);
            servantTripStartedAt = 0L;
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            return true;
        }

        if (Rs2Dialogue.hasContinue())
        {
            Rs2Dialogue.clickContinue();
            return true;
        }

        return false;
    }

    private boolean handleAmountInputIfPresent(KspConstructionConfig.FurnitureMode mode)
    {
        if (!Rs2Widget.hasWidget("Enter amount:"))
        {
            return false;
        }

        Rs2NpcModel servant = getServant();
        int capacity = getServantCapacity(servant);
        int amount = Math.min(DEFAULT_SERVANT_FETCH_AMOUNT, capacity);
        if (amount < mode.getPlanksRequired())
        {
            fail("The hired servant cannot carry enough planks for " + mode + ". Use a higher-level servant or Bank yourself.");
            return true;
        }

        Rs2Keyboard.typeString(String.valueOf(amount));
        Rs2Keyboard.enter();
        markServantTripStarted(servant);
        state = ConstructionState.WAITING_FOR_SERVANT;
        status = "Servant fetching " + amount + " " + mode.getPlankName().toLowerCase(Locale.ROOT) + "s";
        return true;
    }

    private boolean selectOptionContaining(String... requiredParts)
    {
        List<Widget> options = Rs2Dialogue.getDialogueOptions();
        for (int index = 0; index < options.size(); index++)
        {
            Widget option = options.get(index);
            String text = option == null || option.getText() == null
                    ? ""
                    : option.getText().toLowerCase(Locale.ROOT);

            boolean matches = true;
            for (String requiredPart : requiredParts)
            {
                if (requiredPart == null || !text.contains(requiredPart.toLowerCase(Locale.ROOT)))
                {
                    matches = false;
                    break;
                }
            }

            if (matches)
            {
                return Rs2Dialogue.keyPressForDialogueOption(index + 1);
            }
        }
        return false;
    }

    private Rs2NpcModel getServant()
    {
        for (String servantName : SERVANT_NAMES)
        {
            Rs2NpcModel servant = Microbot.getRs2NpcCache()
                    .query()
                    .withName(servantName)
                    .nearestOnClientThread();
            if (servant != null)
            {
                return servant;
            }
        }
        return null;
    }


    private void markServantTripStarted(Rs2NpcModel servant)
    {
        servantTripStartedAt = System.currentTimeMillis();
        servantTripWaitMs = getServantTripWaitMs(servant);
    }

    private long getServantTripWaitMs(Rs2NpcModel servant)
    {
        if (servant == null || servant.getName() == null)
        {
            return DEFAULT_SERVANT_TRIP_WAIT_MS;
        }

        String name = servant.getName().toLowerCase(Locale.ROOT);
        if (name.contains("demon butler"))
        {
            return 8_500L;
        }
        if (name.equals("butler"))
        {
            return 13_500L;
        }
        if (name.equals("cook"))
        {
            return 18_500L;
        }
        if (name.equals("maid"))
        {
            return 31_500L;
        }
        if (name.equals("rick"))
        {
            return 61_500L;
        }
        return DEFAULT_SERVANT_TRIP_WAIT_MS;
    }

    private int getServantCapacity(Rs2NpcModel servant)
    {
        if (servant == null || servant.getName() == null)
        {
            return DEFAULT_SERVANT_FETCH_AMOUNT;
        }

        String name = servant.getName().toLowerCase(Locale.ROOT);
        if (name.contains("demon butler"))
        {
            return 26;
        }
        if (name.equals("butler"))
        {
            return 20;
        }
        if (name.equals("cook"))
        {
            return 16;
        }
        if (name.equals("maid"))
        {
            return 10;
        }
        if (name.equals("rick"))
        {
            return 6;
        }
        return DEFAULT_SERVANT_FETCH_AMOUNT;
    }

    private void handleBankTrip(KspConstructionConfig.FurnitureMode mode)
    {
        switch (bankStage)
        {
            case EXIT_HOUSE:
                leaveHouse(mode);
                break;
            case WALK_TO_BANK:
                walkToBank();
                break;
            case WITHDRAW_PLANKS:
                withdrawPlanks(mode);
                break;
            case RETURN_TO_PORTAL:
                returnToHousePortal();
                break;
            case ENTER_BUILD_MODE:
                enterBuildMode(mode);
                break;
            case NONE:
            default:
                break;
        }
    }

    private void leaveHouse(KspConstructionConfig.FurnitureMode mode)
    {
        state = ConstructionState.LEAVING_HOUSE;
        status = "Leaving house to bank";

        if (!openHouseOptions())
        {
            return;
        }

        Widget leaveHouse = Rs2Widget.findWidget("Leave House", null);
        if (leaveHouse == null)
        {
            leaveHouse = Rs2Widget.getWidget(LEAVE_HOUSE_WIDGET_ID);
        }
        if (leaveHouse == null)
        {
            fail("Could not find the Leave House option.");
            return;
        }

        Rs2Widget.clickWidget(leaveHouse);
        if (!sleepUntil(() -> findNearestTarget(mode) == null, 10_000))
        {
            status = "Waiting to leave the house";
            return;
        }

        sleep(500);
        Rs2TileObjectModel exteriorPortal = findNearestPortal();
        if (exteriorPortal == null)
        {
            sleepUntil(() -> findNearestPortal() != null, 3_000);
            exteriorPortal = findNearestPortal();
        }
        if (exteriorPortal == null)
        {
            fail("Left the house but could not record the exterior portal location.");
            return;
        }

        exteriorPortalTile = exteriorPortal.getWorldLocation();
        workingTile = null;
        bankStage = BankStage.WALK_TO_BANK;
    }

    private void walkToBank()
    {
        state = ConstructionState.WALKING_TO_BANK;
        status = "Walking to nearest accessible bank";

        if (Rs2Bank.walkToBankAndUseBank())
        {
            bankStage = BankStage.WITHDRAW_PLANKS;
        }
    }

    private void withdrawPlanks(KspConstructionConfig.FurnitureMode mode)
    {
        state = ConstructionState.BANKING;
        status = "Withdrawing " + mode.getPlankName().toLowerCase(Locale.ROOT) + "s";

        if (!Rs2Bank.isOpen() && !Rs2Bank.openBank())
        {
            return;
        }

        // Keep every hammer/saw variant, including the Imcando hammer and crystal saw.
        Rs2Bank.depositAllExcept("Hammer", "Saw");
        sleep(500);

        int before = Rs2Inventory.count(mode.getPlankItemId());
        if (!Rs2Bank.withdrawAll(mode.getPlankItemId()))
        {
            fail("No " + mode.getPlankName().toLowerCase(Locale.ROOT) + "s were found in the bank.");
            return;
        }

        sleepUntil(() -> Rs2Inventory.count(mode.getPlankItemId()) > before, 4_000);
        int withdrawn = Rs2Inventory.count(mode.getPlankItemId());
        if (withdrawn < mode.getPlanksRequired())
        {
            fail("The bank does not contain enough " + mode.getPlankName().toLowerCase(Locale.ROOT) + "s for one build.");
            return;
        }

        Rs2Bank.closeBank();
        bankStage = BankStage.RETURN_TO_PORTAL;
    }

    private void returnToHousePortal()
    {
        state = ConstructionState.RETURNING_TO_HOUSE;
        status = "Returning to the saved house portal";

        if (exteriorPortalTile == null)
        {
            fail("The exterior house portal location was not saved.");
            return;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null || playerLocation.distanceTo2D(exteriorPortalTile) > 4)
        {
            Rs2Walker.walkTo(exteriorPortalTile, 3);
        }

        playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation != null && playerLocation.distanceTo2D(exteriorPortalTile) <= 5)
        {
            bankStage = BankStage.ENTER_BUILD_MODE;
        }
    }

    private void enterBuildMode(KspConstructionConfig.FurnitureMode mode)
    {
        state = ConstructionState.ENTERING_BUILD_MODE;
        status = "Entering house in Build Mode";

        Rs2TileObjectModel portal = findPortalNear(exteriorPortalTile, 6);
        if (portal == null)
        {
            status = "Finding the saved house portal";
            return;
        }

        if (!portal.click("Build mode"))
        {
            return;
        }

        workingTile = null;
        if (!sleepUntil(() -> findNearestTarget(mode) != null, 12_000))
        {
            status = "Waiting for the POH instance";
            return;
        }

        bankStage = BankStage.NONE;
        targetMisses = 0;
        state = ConstructionState.SEARCHING;
        status = "Resuming construction";
    }

    private Rs2TileObjectModel findWorkingObject(KspConstructionConfig.FurnitureMode mode)
    {
        Rs2TileObjectModel onWorkingTile = getObjectOnWorkingTile(mode);
        if (onWorkingTile != null)
        {
            return onWorkingTile;
        }

        Rs2TileObjectModel nearest = findNearestTarget(mode);
        if (nearest != null)
        {
            workingTile = nearest.getWorldLocation();
        }
        return nearest;
    }

    private Rs2TileObjectModel getObjectOnWorkingTile(KspConstructionConfig.FurnitureMode mode)
    {
        if (workingTile == null)
        {
            return null;
        }

        return Microbot.getRs2TileObjectCache()
                .query()
                .withIds(mode.getBuiltObjectId(), mode.getHotspotObjectId())
                .where(object -> workingTile.equals(object.getWorldLocation()))
                .nearest();
    }

    private Rs2TileObjectModel findNearestTarget(KspConstructionConfig.FurnitureMode mode)
    {
        return Microbot.getRs2TileObjectCache()
                .query()
                .withIds(mode.getBuiltObjectId(), mode.getHotspotObjectId())
                .nearest();
    }

    private Rs2TileObjectModel findNearestPortal()
    {
        return Microbot.getRs2TileObjectCache()
                .query()
                .withName("Portal")
                .nearestOnClientThread();
    }

    private Rs2TileObjectModel findPortalNear(WorldPoint point, int maxDistance)
    {
        if (point == null)
        {
            return null;
        }

        Rs2TileObjectModel portal = findNearestPortal();
        if (portal == null || portal.getWorldLocation() == null)
        {
            return null;
        }
        return portal.getWorldLocation().distanceTo2D(point) <= maxDistance ? portal : null;
    }

    private void fail(String message)
    {
        if (state == ConstructionState.STOPPED)
        {
            return;
        }

        lastError = message == null ? "Unknown error" : message;
        status = lastError;
        state = ConstructionState.STOPPED;
        Microbot.log("[KSP Construction] " + lastError);
        Microbot.getNotifier().notify(lastError);
        shutdown();
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        bankStage = BankStage.NONE;
        workingTile = null;
    }

    public ConstructionState getState()
    {
        return state;
    }

    public String getStatus()
    {
        return status;
    }

    public String getLastError()
    {
        return lastError;
    }

    public int getFurnitureBuilt()
    {
        return furnitureBuilt;
    }

    public int getCurrentPlankCount()
    {
        if (config == null)
        {
            return 0;
        }
        return Rs2Inventory.count(config.furnitureMode().getPlankItemId());
    }
}
