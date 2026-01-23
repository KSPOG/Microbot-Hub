package net.runelite.client.plugins.microbot.flippilot;

import net.runelite.client.plugins.microbot.flippilot.data.HistoryStore;
import net.runelite.client.plugins.microbot.flippilot.engine.Suggestion;
import net.runelite.client.plugins.microbot.flippilot.microbot.FlipEvent;
import net.runelite.client.plugins.microbot.flippilot.microbot.FlipEventBus;
import net.runelite.client.plugins.microbot.flippilot.storage.WatchlistStore;
import net.runelite.client.plugins.microbot.flippilot.ui.PriceChartPanel;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class FlipPilotPanel extends JPanel
{
    private final AtomicReference<List<Suggestion>> suggestionsRef = new AtomicReference<>(Collections.emptyList());
    private final FlipEventBus eventBus;
    private final HistoryStore historyStore;
    private final WatchlistStore watchlistStore;

    private final SuggestionTableModel tableModel = new SuggestionTableModel();
    private final JTable table = new JTable(tableModel);

    private final JLabel membersLabel = new JLabel("Members: ?");
    private final JLabel statusLabel = new JLabel("Status: idle");
    private final JLabel profitLabel = new JLabel("Session Profit: 0 gp");

    private final PriceChartPanel chart = new PriceChartPanel();
    private final JTextArea detailsArea = new JTextArea();

    private final JTextArea trackingArea = new JTextArea();

    private volatile int selectedItemId = -1;

    public FlipPilotPanel(FlipEventBus eventBus, HistoryStore historyStore, WatchlistStore watchlistStore)
    {
        super(new BorderLayout());
        this.eventBus = eventBus;
        this.historyStore = historyStore;
        this.watchlistStore = watchlistStore;

        JTabbedPane tabs = new JTabbedPane();

        tabs.addTab("Suggestions", buildSuggestionsTab());
        tabs.addTab("Item", buildItemTab());
        tabs.addTab("Tracking", buildTrackingTab());

        add(buildHeader(), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);

        trackingArea.setEditable(false);
        trackingArea.setLineWrap(true);
        trackingArea.setWrapStyleWord(true);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e ->
        {
            int row = table.getSelectedRow();
            if (row >= 0 && row < tableModel.getRowCount())
            {
                Suggestion s = tableModel.getAt(row);
                selectedItemId = s.itemId;
                refreshItemDetails(s);
            }
        });
    }

    private JPanel buildHeader()
    {
        JPanel p = new JPanel(new GridLayout(3, 1));
        p.add(membersLabel);
        p.add(statusLabel);
        p.add(profitLabel);
        return p;
    }

    private JComponent buildSuggestionsTab()
    {
        JPanel p = new JPanel(new BorderLayout());
        table.setFillsViewportHeight(true);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private JComponent buildItemTab()
    {
        JPanel p = new JPanel(new BorderLayout());

        chart.setPreferredSize(new Dimension(10, 180));
        p.add(chart, BorderLayout.NORTH);

        p.add(new JScrollPane(detailsArea), BorderLayout.CENTER);

        JButton addWatch = new JButton("Add to Watchlist");
        JButton removeWatch = new JButton("Remove from Watchlist");

        addWatch.addActionListener(e -> {
            int id = selectedItemId;
            if (id > 0) watchlistStore.add(id);
        });

        removeWatch.addActionListener(e -> {
            int id = selectedItemId;
            if (id > 0) watchlistStore.remove(id);
        });

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btns.add(addWatch);
        btns.add(removeWatch);

        p.add(btns, BorderLayout.SOUTH);

        return p;
    }

    private JComponent buildTrackingTab()
    {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(trackingArea), BorderLayout.CENTER);

        JButton reset = new JButton("Reset session");
        reset.addActionListener(e -> {
            eventBus.resetSession();
            refreshTracking();
        });

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(reset, BorderLayout.WEST);
        p.add(bottom, BorderLayout.SOUTH);

        return p;
    }

    public void setMembers(boolean isMembers)
    {
        membersLabel.setText("Members: " + (isMembers ? "YES" : "NO"));
    }

    public void setStatus(String status)
    {
        statusLabel.setText("Status: " + status);
    }

    public void setSuggestions(List<Suggestion> suggestions)
    {
        suggestionsRef.set(suggestions == null ? Collections.emptyList() : suggestions);
        tableModel.setData(suggestions);
        tableModel.fireTableDataChanged();

        if (tableModel.getRowCount() > 0 && table.getSelectedRow() < 0)
        {
            table.setRowSelectionInterval(0, 0);
        }
    }

    public void tickUi()
    {
        profitLabel.setText("Session Profit: " + eventBus.getSessionProfit() + " gp");
        refreshTracking();
        if (selectedItemId > 0)
        {
            chart.setPoints(historyStore.get(selectedItemId));
        }
    }

    private void refreshItemDetails(Suggestion s)
    {
        chart.setPoints(historyStore.get(s.itemId));

        boolean watched = watchlistStore.isWatched(s.itemId);

        detailsArea.setText(
                "Item: " + s.name + " (" + s.itemId + ")\n" +
                "Watched: " + (watched ? "YES" : "NO") + "\n\n" +
                "High: " + s.high + "\n" +
                "Low: " + s.low + "\n" +
                "Estimated margin: " + s.margin + " gp\n" +
                "ROI: " + new DecimalFormat("0.00").format(s.roiPct) + "%\n" +
                "Limit: " + (s.limit > 0 ? s.limit : "-") + "\n" +
                "Vol(5m): " + s.vol5m + "\n" +
                "Risk: " + new DecimalFormat("0.00").format(s.risk) + "\n" +
                "Score: " + new DecimalFormat("0.00").format(s.score) + "\n"
        );
    }

    private void refreshTracking()
    {
        List<FlipEvent> recent = eventBus.getRecent(25);
        StringBuilder sb = new StringBuilder();
        sb.append("Session profit: ").append(eventBus.getSessionProfit()).append(" gp\n\n");
        sb.append("Recent flips (reported by your logic):\n");
        for (int i = recent.size() - 1; i >= 0; i--)
        {
            FlipEvent e = recent.get(i);
            sb.append("- ").append(e.itemName)
              .append(" x").append(e.qty)
              .append(" profit=").append(e.profit)
              .append("\n");
        }
        trackingArea.setText(sb.toString());
    }

    // ---------- Table model ----------
    private static class SuggestionTableModel extends AbstractTableModel
    {
        private final String[] cols = {"Item", "High", "Low", "Margin", "ROI%", "Limit", "Vol(5m)", "Risk", "Score"};
        private List<Suggestion> data = Collections.emptyList();

        public void setData(List<Suggestion> d) { data = d == null ? Collections.emptyList() : d; }
        public Suggestion getAt(int row) { return data.get(row); }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex)
        {
            Suggestion s = data.get(rowIndex);
            switch (columnIndex)
            {
                case 0: return s.name;
                case 1: return s.high;
                case 2: return s.low;
                case 3: return s.margin;
                case 4: return new DecimalFormat("0.00").format(s.roiPct);
                case 5: return s.limit > 0 ? s.limit : "-";
                case 6: return s.vol5m;
                case 7: return new DecimalFormat("0.00").format(s.risk);
                case 8: return new DecimalFormat("0.00").format(s.score);
                default: return "";
            }
        }
    }
}
