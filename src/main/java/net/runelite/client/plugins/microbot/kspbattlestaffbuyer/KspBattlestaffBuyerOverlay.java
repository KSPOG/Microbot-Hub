package net.runelite.client.plugins.microbot.kspbattlestaffbuyer;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class KspBattlestaffBuyerOverlay extends OverlayPanel
{
    private final KspBattlestaffBuyerPlugin plugin;

    @Inject
    KspBattlestaffBuyerOverlay(KspBattlestaffBuyerPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        KspBattlestaffBuyerScript script = plugin.getScript();
        if (script == null)
        {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(255, 220));
        panelComponent.setBackgroundColor(new Color(0, 0, 0, 175));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Battlestaff Buyer")
                .color(Color.WHITE)
                .build());

        addLine("Status:", shorten(script.getStatus(), 29));
        addLine("Runtime:", formatDuration(script.getRuntimeMs()));
        addLine("Bought:", format(script.getBattlestavesBought())
                + " / " + format(script.getBattlestavesPerHour()) + " ph");
        addLine("Inventory:", format(script.getInventoryBattlestaves()));
        addLine("Bank:", format(script.getBankBattlestaves()));
        addLine("Cycle staffs:", format(script.getCycleAcquiredStaffs()));
        addLine("Cycle cost:", format(script.getCycleAcquisitionCost()) + " gp");
        addLine("Shop stock:", format(script.getShopStock()));
        addLine("Profit floor:", format(script.getMinimumProfitableSellPrice()) + " gp");
        if (script.isUsingCustomSellPrice())
        {
            addLine("Price mode:", "Custom");
            addLine("Custom price:", format(script.getConfiguredCustomSellPrice()) + " gp");
        }
        else
        {
            addLine("Price mode:", "Automatic");
        }
        if (script.getActiveSellPrice() > 0)
        {
            addLine("GE offer:", format(script.getActiveSellQuantity())
                    + " @ " + format(script.getActiveSellPrice()));
        }
        addLine("GE offers:", format(script.getGeSellOffers()));
        addLine("World hops:", format(script.getWorldHops()));
        addLine("Bank trips:", format(script.getBankTrips()));
        addLine("Coins:", format(script.getCoinsRemaining()));

        return super.render(graphics);
    }

    private void addLine(String left, String right)
    {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left)
                .right(right)
                .build());
    }

    private String format(long value)
    {
        return String.format("%,d", value);
    }

    private String formatDuration(long millis)
    {
        long seconds = Math.max(0L, millis) / 1_000L;
        return String.format("%02d:%02d:%02d",
                seconds / 3600,
                (seconds % 3600) / 60,
                seconds % 60);
    }

    private String shorten(String value, int max)
    {
        if (value == null)
        {
            return "";
        }
        return value.length() <= max
                ? value
                : value.substring(0, Math.max(0, max - 3)) + "...";
    }
}
