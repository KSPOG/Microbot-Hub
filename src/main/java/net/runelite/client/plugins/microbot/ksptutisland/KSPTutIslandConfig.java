package net.runelite.client.plugins.microbot.ksptutisland;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(KSPTutIslandConfig.GROUP)
public interface KSPTutIslandConfig extends Config
{
    String GROUP = "ksptutisland";
    String ACCOUNT_SECTION = "account";

    @ConfigSection(
            name = "Account handling",
            description = "Controls what the script does after Tutorial Island is completed.",
            position = 0
    )
    String accountSection = ACCOUNT_SECTION;

    @ConfigItem(
            keyName = "runMultipleAccounts",
            name = "Run multiple accounts",
            description = "Logout after Tutorial Island completion and keep the script ready for the next account.",
            section = ACCOUNT_SECTION,
            position = 0
    )
    default boolean runMultipleAccounts()
    {
        return false;
    }

    @ConfigItem(
            keyName = "accountQueue",
            name = "Login details queue",
            description = "Separate accounts with semicolons. Format: email:password;email2:password2 or email:password:world",
            section = ACCOUNT_SECTION,
            position = 1,
            secret = true
    )
    default String accountQueue()
    {
        return "";
    }
}
