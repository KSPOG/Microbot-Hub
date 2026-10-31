package net.runelite.client.plugins.microbot.dashboardlink;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("dashboardLink")
public interface DashboardLinkConfig extends Config {
    @ConfigItem(keyName = "url", name = "Dashboard address", description = "HTTPS origin of your Account Console", position = 0)
    default String url() { return ""; }

    @ConfigItem(keyName = "token", name = "Connection key", description = "Create a separate key for this character in the dashboard", secret = true, position = 1)
    default String token() { return ""; }

    @ConfigItem(keyName = "siteAccessToken", name = "Private Site access token", description = "Optional Sites gateway token for an owner-only deployment; leave blank for a public dashboard", secret = true, position = 2)
    default String siteAccessToken() { return ""; }

    @ConfigItem(keyName = "allowControl", name = "Allow dashboard control", description = "Allow plugin toggles, supported setting changes, and pause/resume commands", position = 3)
    default boolean allowControl() { return false; }
}
