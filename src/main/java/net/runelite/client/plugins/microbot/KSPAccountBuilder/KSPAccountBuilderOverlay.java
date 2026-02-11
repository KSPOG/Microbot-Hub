package net.runelite.client.plugins.microbot.KSPAccountBuilder;


import net.runelite.client.plugins.microbot.KSPAutoMiner.KSPAutoMinerScript;
import net.runelite.client.plugins.microbot.KSPAutoWoodcutter.KSPAutoWoodcutterScript;


import net.runelite.client.plugins.microbot.KSPAutoMiner.KSPAutoMinerScript;
import net.runelite.client.plugins.microbot.KSPAutoWoodcutter.KSPAutoWoodcutterScript;


import net.runelite.client.plugins.microbot.KSPAutoMiner.KSPAutoMinerScript;
import net.runelite.client.plugins.microbot.KSPAutoWoodcutter.KSPAutoWoodcutterScript;


import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.time.Duration;

public class KSPAccountBuilderOverlay extends OverlayPanel {
    @Inject
    KSPAccountBuilderOverlay(KSPAccountBuilderPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_CENTER);
    }

    @Override
    public Dimension render(java.awt.Graphics2D graphics) {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(320, 190));


        panelComponent.setPreferredSize(new Dimension(320, 190));


        panelComponent.setPreferredSize(new Dimension(320, 190));

        panelComponent.setPreferredSize(new Dimension(240, 150));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Account Builder")
                .color(Color.ORANGE)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Stage:")

                .right(formatStageLabel(KSPAccountBuilderScript.stageLabel))


                .right(formatStageLabel(KSPAccountBuilderScript.stageLabel))


                .right(formatStageLabel(KSPAccountBuilderScript.stageLabel))

                .right(KSPAccountBuilderScript.stageLabel)

                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")

                .right(buildDetailedStatus())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Currently Doing:")

                .right(KSPAccountBuilderScript.getCurrentStageTask())


                .right(KSPAccountBuilderScript.getCurrentStageTask())

                .right(KSPAccountBuilderScript.getCurrentTaskSummary())

                .right(KSPAccountBuilderScript.status)



                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time running:")
                .right(formatDuration(KSPAccountBuilderScript.getRuntime()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Change Task in:")
                .right(formatDuration(KSPAccountBuilderScript.getTimeUntilSwitch()))
                .build());


        panelComponent.getChildren().add(LineComponent.builder()
                .left("TTNB:")
                .right(formatDuration(KSPAccountBuilderScript.getTimeUntilNextBreak()))
                .build());

        return super.render(graphics);
    }

    private String buildDetailedStatus() {
        String stageStatus = getActiveStageStatus();
        String builderStatus = normalizeStatus(KSPAccountBuilderScript.status);

        if (stageStatus.isEmpty()) {
            return builderStatus.isEmpty() ? "Idle" : builderStatus;
        }

        if (builderStatus.isEmpty() || stageStatus.equalsIgnoreCase(builderStatus)) {
            return stageStatus;
        }

        return stageStatus + ", " + builderStatus;
    }

    private String getActiveStageStatus() {
        String stage = KSPAccountBuilderScript.stageLabel;
        if (stage == null) {
            return "";
        }

        switch (stage) {
            case "MINING":
                return normalizeStatus(KSPAutoMinerScript.status);
            case "WOODCUTTING":
                return normalizeStatus(KSPAutoWoodcutterScript.status);
            case "F2P_FISHING":
            case "F2P_COOKER":
                return "Running " + formatStageLabel(stage);
            default:
                return "";
        }
    }

    private String normalizeStatus(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "idle".equalsIgnoreCase(trimmed) || "stopped".equalsIgnoreCase(trimmed)) {
            return "";
        }
        return trimmed;
    }

    private String formatStageLabel(String stageLabel) {
        if (stageLabel == null || stageLabel.isEmpty()) {
            return "None";
        }
        String[] words = stageLabel.toLowerCase().split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.length() == 0 ? stageLabel : label.toString();
    }




        return super.render(graphics);
    }


    private String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
