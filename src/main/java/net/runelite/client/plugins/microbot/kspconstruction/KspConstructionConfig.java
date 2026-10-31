package net.runelite.client.plugins.microbot.kspconstruction;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(KspConstructionConfig.GROUP)
@ConfigInformation(
        "Start inside your player-owned house in Build Mode beside the selected furniture hotspot.<br />" +
        "Keep a hammer and saw available. Servant mode requires a hired servant and planks in the bank.<br />" +
        "Bank mode exits through the house portal, uses the nearest accessible bank, and returns to the same portal."
)
public interface KspConstructionConfig extends Config
{
    String GROUP = "kspConstruction";

    enum FurnitureMode
    {
        OAK_LARDER(
                "Oak larder",
                ItemID.PLANK_OAK,
                "Oak plank",
                "Oak planks",
                8,
                13566,
                15403,
                '2'
        ),
        MAHOGANY_TABLE(
                "Mahogany table",
                ItemID.PLANK_MAHOGANY,
                "Mahogany plank",
                "Mahogany planks",
                6,
                13298,
                15298,
                '6'
        );

        private final String displayName;
        private final int plankItemId;
        private final String plankName;
        private final String servantOptionName;
        private final int planksRequired;
        private final int builtObjectId;
        private final int hotspotObjectId;
        private final char buildKey;

        FurnitureMode(
                String displayName,
                int plankItemId,
                String plankName,
                String servantOptionName,
                int planksRequired,
                int builtObjectId,
                int hotspotObjectId,
                char buildKey)
        {
            this.displayName = displayName;
            this.plankItemId = plankItemId;
            this.plankName = plankName;
            this.servantOptionName = servantOptionName;
            this.planksRequired = planksRequired;
            this.builtObjectId = builtObjectId;
            this.hotspotObjectId = hotspotObjectId;
            this.buildKey = buildKey;
        }

        public int getPlankItemId()
        {
            return plankItemId;
        }

        public String getPlankName()
        {
            return plankName;
        }

        public String getServantOptionName()
        {
            return servantOptionName;
        }

        public int getPlanksRequired()
        {
            return planksRequired;
        }

        public int getBuiltObjectId()
        {
            return builtObjectId;
        }

        public int getHotspotObjectId()
        {
            return hotspotObjectId;
        }

        public char getBuildKey()
        {
            return buildKey;
        }

        @Override
        public String toString()
        {
            return displayName;
        }
    }

    enum PlankSource
    {
        CALL_SERVANT("Call servant"),
        BANK_YOURSELF("Bank yourself");

        private final String displayName;

        PlankSource(String displayName)
        {
            this.displayName = displayName;
        }

        @Override
        public String toString()
        {
            return displayName;
        }
    }

    @ConfigSection(
            name = "Construction",
            description = "Construction training settings",
            position = 0
    )
    String constructionSection = "construction";

    @ConfigItem(
            keyName = "furnitureMode",
            name = "Furniture",
            description = "Furniture to repeatedly build and remove",
            position = 0,
            section = constructionSection
    )
    default FurnitureMode furnitureMode()
    {
        return FurnitureMode.OAK_LARDER;
    }

    @ConfigItem(
            keyName = "plankSource",
            name = "Plank source",
            description = "Use the hired servant or bank the planks yourself",
            position = 1,
            section = constructionSection
    )
    default PlankSource plankSource()
    {
        return PlankSource.CALL_SERVANT;
    }
}
