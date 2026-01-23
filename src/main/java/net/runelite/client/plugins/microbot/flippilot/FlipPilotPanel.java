package net.runelite.client.plugins.microbot.flippilot;

import net.runelite.client.plugins.microbot.flippilot.data.HistoryStore;
import net.runelite.client.plugins.microbot.flippilot.engine.Suggestion;
import net.runelite.client.plugins.microbot.flippilot.microbot.FlipEvent;
import net.runelite.client.plugins.microbot.flippilot.microbot.FlipEventBus;
import net.runelite.client.plugins.microbot.flippilot.storage.WatchlistStore;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FlipPilotPanel extends PluginPanel
{
    private static final Color ACTION_ABORT_COLOR = new Color(0xB0, 0x3A, 0x2C);
    private static final Color ACTION_COLLECT_COLOR = new Color(0x3A, 0x7A, 0xFF);

    private final FlipEventBus eventBus;
    private final HistoryStore historyStore;
    private final WatchlistStore watchlistStore;

    private final JLabel itemLabel = new JLabel("No item selected");
    private final JLabel membersLabel = new JLabel("F2P version. Sub for P2P");
    private final JLabel statusLabel = new JLabel("Status: idle");

    private final JLabel profitValue = new JLabel("0 gp");
    private final JLabel roiValue = new JLabel("-");
    private final JLabel flipsValue = new JLabel("0");
    private final JLabel taxValue = new JLabel("-");
    private final JLabel sessionTimeValue = new JLabel("00:00:00");
    private final JLabel hourlyProfitValue = new JLabel("0 gp/hr");
    private final JLabel avgWealthValue = new JLabel("-");

    private final FlipEventTableModel flipEventTableModel = new FlipEventTableModel();
    private final JTable flipEventTable = new JTable(flipEventTableModel);

    private final ButtonGroup adjustGroup = new ButtonGroup();
    private final ButtonGroup riskGroup = new ButtonGroup();

    private List<Suggestion> suggestions = Collections.emptyList();

    public FlipPilotPanel(FlipEventBus eventBus, HistoryStore historyStore, WatchlistStore watchlistStore)
    {
        super();
        this.eventBus = eventBus;
        this.historyStore = historyStore;
        this.watchlistStore = watchlistStore;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);

        content.add(buildHeader());
        content.add(Box.createVerticalStrut(8));
        content.add(buildControls());
        content.add(Box.createVerticalStrut(6));
        content.add(buildActionLegend());
        content.add(Box.createVerticalStrut(10));
        content.add(buildAdjustSection());
        content.add(Box.createVerticalStrut(8));
        content.add(buildRiskSection());
        content.add(Box.createVerticalStrut(10));
        content.add(buildSessionSection());
        content.add(Box.createVerticalStrut(10));
        content.add(buildProfitSection());
        content.add(Box.createVerticalStrut(8));
        content.add(buildStatsSection());
        content.add(Box.createVerticalStrut(10));
        content.add(buildFlipListSection());

        add(new JScrollPane(content), BorderLayout.CENTER);
        configureTable();
    }

    private JPanel buildHeader()
    {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(new EmptyBorder(8, 8, 8, 8));

        JLabel gear = new JLabel("\u2699");
        gear.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        gear.setForeground(ColorScheme.BRAND_ORANGE);
        header.add(gear, BorderLayout.WEST);

        JPanel titlePanel = new JPanel();
        titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Abort offer for");
        title.setFont(FontManager.getRunescapeSmallFont());
        title.setForeground(Color.WHITE);

        itemLabel.setFont(FontManager.getRunescapeBoldFont());
        itemLabel.setForeground(ACTION_COLLECT_COLOR);

        membersLabel.setFont(FontManager.getRunescapeSmallFont());
        membersLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        titlePanel.add(title);
        titlePanel.add(itemLabel);
        titlePanel.add(membersLabel);
        titlePanel.add(statusLabel);

        header.add(titlePanel, BorderLayout.CENTER);

        return header;
    }

    private JPanel buildControls()
    {
        JPanel controls = new JPanel(new GridLayout(1, 4, 6, 0));
        controls.setBackground(ColorScheme.DARK_GRAY_COLOR);

        controls.add(buildIconButton("\u25A0", "Stop", ColorScheme.DARKER_GRAY_COLOR));
        controls.add(buildIconButton("\u23F8", "Pause", ColorScheme.DARKER_GRAY_COLOR));
        controls.add(buildIconButton("\u2298", "Abort", ACTION_ABORT_COLOR));
        controls.add(buildIconButton("\u00BB", "Collect/Buy", ACTION_COLLECT_COLOR));

        return controls;
    }

    private JButton buildIconButton(String text, String tooltip, Color background)
    {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFont(FontManager.getRunescapeBoldFont());
        button.setBackground(background);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        return button;
    }

    private JPanel buildActionLegend()
    {
        JPanel legend = new JPanel(new GridLayout(1, 2, 6, 0));
        legend.setBackground(ColorScheme.DARK_GRAY_COLOR);

        legend.add(buildLegendPill("Abort", ACTION_ABORT_COLOR));
        legend.add(buildLegendPill("Collect/Buy", ACTION_COLLECT_COLOR));

        return legend;
    }

    private JPanel buildLegendPill(String text, Color color)
    {
        JPanel pill = new JPanel(new BorderLayout());
        pill.setBackground(color);
        pill.setBorder(new EmptyBorder(4, 8, 4, 8));

        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(Color.WHITE);
        pill.add(label, BorderLayout.CENTER);
        return pill;
    }

    private JPanel buildAdjustSection()
    {
        JPanel section = buildSectionPanel();
        section.add(buildSectionLabel("How often do you adjust offers?"), BorderLayout.NORTH);

        JPanel buttons = new JPanel(new GridLayout(1, 5, 4, 0));
        buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);

        buttons.add(buildToggle(adjustGroup, "5m", true));
        buttons.add(buildToggle(adjustGroup, "30m", false));
        buttons.add(buildToggle(adjustGroup, "2h", false));
        buttons.add(buildToggle(adjustGroup, "8h", false));
        buttons.add(buildToggle(adjustGroup, "...", false));

        section.add(buttons, BorderLayout.CENTER);
        return section;
    }

    private JPanel buildRiskSection()
    {
        JPanel section = buildSectionPanel();
        section.add(buildSectionLabel("Risk level:"), BorderLayout.NORTH);

        JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 0));
        buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);

        buttons.add(buildToggle(riskGroup, "Low", false));
        buttons.add(buildToggle(riskGroup, "Med", true));
        buttons.add(buildToggle(riskGroup, "High", false));

        section.add(buttons, BorderLayout.CENTER);
        return section;
    }

    private JPanel buildSessionSection()
    {
        JPanel section = buildSectionPanel();
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel sessionLabel = buildSectionLabel("Session");
        row.add(sessionLabel, BorderLayout.WEST);

        JComboBox<String> accountSelect = new JComboBox<>(new String[]{"All accounts"});
        accountSelect.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        accountSelect.setForeground(Color.WHITE);
        row.add(accountSelect, BorderLayout.CENTER);

        JButton reset = new JButton("Reset session");
        reset.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        reset.setForeground(Color.WHITE);
        reset.addActionListener(e -> {
            eventBus.resetSession();
            refreshSessionStats();
        });
        row.add(reset, BorderLayout.EAST);

        section.add(row, BorderLayout.CENTER);
        return section;
    }

    private JPanel buildProfitSection()
    {
        JPanel panel = buildSectionPanel();
        JLabel profitLabel = buildSectionLabel("Profit:");
        profitValue.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
        profitValue.setForeground(new Color(0x43, 0xB0, 0x47));

        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.add(profitLabel, BorderLayout.WEST);
        row.add(profitValue, BorderLayout.EAST);

        panel.add(row, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildStatsSection()
    {
        JPanel panel = buildSectionPanel();
        JPanel grid = new JPanel(new GridLayout(0, 2, 6, 4));
        grid.setBackground(ColorScheme.DARK_GRAY_COLOR);

        grid.add(buildStatLabel("ROI:"));
        grid.add(roiValue);
        grid.add(buildStatLabel("Flips made:"));
        grid.add(flipsValue);
        grid.add(buildStatLabel("Tax paid:"));
        grid.add(taxValue);
        grid.add(buildStatLabel("Session time:"));
        grid.add(sessionTimeValue);
        grid.add(buildStatLabel("Hourly profit:"));
        grid.add(hourlyProfitValue);
        grid.add(buildStatLabel("Avg wealth:"));
        grid.add(avgWealthValue);

        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFlipListSection()
    {
        JPanel panel = buildSectionPanel();
        JLabel label = buildSectionLabel("Recent flips");
        panel.add(label, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(flipEventTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSectionPanel()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(4, 0, 4, 0));
        return panel;
    }

    private JLabel buildSectionLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        return label;
    }

    private JLabel buildStatLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(Color.WHITE);
        return label;
    }

    private JToggleButton buildToggle(ButtonGroup group, String text, boolean selected)
    {
        JToggleButton button = new JToggleButton(text, selected);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setFocusPainted(false);
        button.setBackground(selected ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
        button.setForeground(Color.WHITE);
        button.addItemListener(e -> updateToggleStyles(group));
        group.add(button);
        return button;
    }

    private void updateToggleStyles(ButtonGroup group)
    {
        for (var element = group.getElements(); element.hasMoreElements(); )
        {
            AbstractButton button = element.nextElement();
            boolean selected = button.isSelected();
            button.setBackground(selected ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
            button.setForeground(Color.WHITE);
        }
    }

    private void configureTable()
    {
        flipEventTable.setFillsViewportHeight(true);
        flipEventTable.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        flipEventTable.setForeground(Color.WHITE);
        flipEventTable.setGridColor(ColorScheme.DARK_GRAY_COLOR);
        flipEventTable.setRowHeight(18);
        flipEventTable.getTableHeader().setReorderingAllowed(false);
        flipEventTable.getTableHeader().setBackground(ColorScheme.DARK_GRAY_COLOR);
        flipEventTable.getTableHeader().setForeground(Color.WHITE);
    }

    public void setMembers(boolean isMembers)
    {
        membersLabel.setText(isMembers ? "Members version" : "F2P version. Sub for P2P");
    }

    public void setStatus(String status)
    {
        statusLabel.setText("Status: " + status);
    }

    public void setSuggestions(List<Suggestion> suggestions)
    {
        this.suggestions = suggestions == null ? Collections.emptyList() : new ArrayList<>(suggestions);
        if (!this.suggestions.isEmpty())
        {
            itemLabel.setText(this.suggestions.get(0).name);
            itemLabel.setForeground(ACTION_COLLECT_COLOR);
        }
        else
        {
            itemLabel.setText("No item selected");
            itemLabel.setForeground(Color.WHITE);
        }
    }

    public void tickUi()
    {
        refreshSessionStats();
        refreshFlipTable();
    }

    private void refreshSessionStats()
    {
        long profit = eventBus.getSessionProfit();
        profitValue.setText(formatGp(profit));

        flipsValue.setText(String.valueOf(eventBus.getEventCount()));

        Duration duration = Duration.ofMillis(eventBus.getSessionDurationMs());
        sessionTimeValue.setText(formatDuration(duration));

        double hours = Math.max(0.0001, duration.toMillis() / 3_600_000.0);
        long hourly = Math.round(profit / hours);
        hourlyProfitValue.setText(formatGp(hourly) + "/hr");
    }

    private void refreshFlipTable()
    {
        flipEventTableModel.setEvents(eventBus.getRecent(200));
    }

    private String formatDuration(Duration duration)
    {
        long seconds = duration.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private String formatGp(long value)
    {
        return String.format("%,d gp", value);
    }

    private static class FlipEventTableModel extends AbstractTableModel
    {
        private final String[] columns = {"Item", "Profit"};
        private List<FlipEvent> events = Collections.emptyList();

        public void setEvents(List<FlipEvent> events)
        {
            this.events = events == null ? Collections.emptyList() : new ArrayList<>(events);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount()
        {
            return events.size();
        }

        @Override
        public int getColumnCount()
        {
            return columns.length;
        }

        @Override
        public String getColumnName(int column)
        {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex)
        {
            FlipEvent event = events.get(events.size() - 1 - rowIndex);
            if (columnIndex == 0)
            {
                return event.qty + " x " + event.itemName;
            }
            return String.format("%,d", event.profit);
        }
    }
}
