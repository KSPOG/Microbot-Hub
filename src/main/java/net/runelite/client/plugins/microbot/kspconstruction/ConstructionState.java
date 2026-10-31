package net.runelite.client.plugins.microbot.kspconstruction;

public enum ConstructionState
{
    STARTING("Starting"),
    SEARCHING("Finding furniture hotspot"),
    BUILDING("Building furniture"),
    REMOVING("Removing furniture"),
    CALLING_SERVANT("Calling servant"),
    SERVANT_DIALOGUE("Handling servant dialogue"),
    WAITING_FOR_SERVANT("Waiting for servant"),
    LEAVING_HOUSE("Leaving house"),
    WALKING_TO_BANK("Walking to bank"),
    BANKING("Withdrawing planks"),
    RETURNING_TO_HOUSE("Returning to house"),
    ENTERING_BUILD_MODE("Entering build mode"),
    STOPPED("Stopped");

    private final String displayName;

    ConstructionState(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
