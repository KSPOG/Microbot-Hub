package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sell;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SellList
{
    LOGS("Logs"),
    OAK_LOGS("Oak logs"),
    YEW_LOGS("Yew logs"),
    BRONZE_DAGGER("Bronze dagger"),
    BRONZE_SCIMITAR("Bronze scimitar"),
    BRONZE_WARHAMMER("Bronze warhammer"),
    BRONZE_PLATEBODY("Bronze platebody"),
    IRON_SCIMITAR("Iron scimitar"),
    IRON_WARHAMMER("Iron warhammer"),
    IRON_PLATEBODY("Iron platebody"),
    STEEL_SCIMITAR("Steel scimitar"),
    STEEL_WARHAMMER("Steel warhammer"),
    STEEL_PLATEBODY("Steel platebody");

    private final String displayName;
}
