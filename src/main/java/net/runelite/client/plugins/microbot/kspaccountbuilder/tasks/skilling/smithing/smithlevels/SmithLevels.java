package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smithlevels;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SmithLevels
{
    BRONZE_DAGGER("Bronze dagger", 1),
    BRONZE_SCIMITAR("Bronze scimitar", 5),
    BRONZE_WARHAMMER("Bronze warhammer", 9),
    BRONZE_PLATEBODY("Bronze platebody", 18),
    IRON_SCIMITAR("Iron scimitar", 20),
    IRON_WARHAMMER("Iron warhammer", 24),
    IRON_PLATEBODY("Iron platebody", 33),
    STEEL_SCIMITAR("Steel scimitar", 35),
    STEEL_WARHAMMER("Steel warhammer", 39),
    STEEL_PLATEBODY("Steel platebody", 48);

    private final String displayName;
    private final int requiredLevel;
}
