package net.runelite.client.plugins.microbot.flippilot.data;

import net.runelite.api.Client;
import net.runelite.api.WorldType;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MembersDetector
{
    private final Client client;

    @Inject
    public MembersDetector(Client client)
    {
        this.client = client;
    }

    public boolean isMembers()
    {
        try
        {
            return client.getWorldType() != null && client.getWorldType().contains(WorldType.MEMBERS);
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
