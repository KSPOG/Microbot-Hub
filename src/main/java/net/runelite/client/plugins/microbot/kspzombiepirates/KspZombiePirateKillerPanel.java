package net.runelite.client.plugins.microbot.kspzombiepirates;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.HashSet;
import java.util.Set;

class KspZombiePirateKillerPanel extends PluginPanel {
    private final KspZombiePirateKillerConfig config;
    private final ConfigManager configManager;
    private final KspZombiePirateKillerPlugin plugin;

    private final JPasswordField licenseKeyField = new JPasswordField();
    private final JLabel licenseStatus = new JLabel("License not validated.");
    private final JPanel settingsPanel = new JPanel();

    private JTextArea equipmentNames;
    private JTextArea inventoryItems;
    private JCheckBox keepProtectMagic;
    private JSpinner eatAtHp;
    private JSpinner emergencyTeleportHp;
    private JSpinner drinkPrayerAt;
    private JSpinner bankWhenPrayerBelow;
    private JCheckBox prayAtNearestAltar;
    private JTextArea activePrayerNames;
    private JTextArea lootNames;
    private JSpinner minLootValue;
    private JSpinner lootRange;
    private JSpinner attackRange;
    private JSpinner emptyContainerWidgetId;
    private JCheckBox useEmptyContainersButton;

    KspZombiePirateKillerPanel(KspZombiePirateKillerConfig config, ConfigManager configManager, KspZombiePirateKillerPlugin plugin) {
        this.config = config;
        this.configManager = configManager;
        this.plugin = plugin;
        build();
    }

    private void build() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);

        content.add(title("KSP Zombie Pirates"));
        content.add(Box.createRigidArea(new Dimension(0, 6)));
        content.add(buildLicensePanel());
        content.add(Box.createRigidArea(new Dimension(0, 6)));
        content.add(buildSettingsPanel());

        add(content, BorderLayout.NORTH);
    }

    private JPanel buildLicensePanel() {
        JPanel panel = sectionPanel(6);
        panel.add(label("License key"));
        licenseKeyField.setText(config.licenseKey());
        panel.add(fullWidth(licenseKeyField));
        panel.add(Box.createRigidArea(new Dimension(0, 4)));

        JButton validateButton = new JButton("Validate license");
        validateButton.addActionListener(event -> plugin.validateLicense(
                new String(licenseKeyField.getPassword()).trim()
        ));
        panel.add(fullWidth(validateButton));
        panel.add(Box.createRigidArea(new Dimension(0, 4)));

        licenseStatus.setForeground(Color.LIGHT_GRAY);
        panel.add(fullWidth(licenseStatus));
        return panel;
    }

    private JPanel buildSettingsPanel() {
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        settingsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR),
                new EmptyBorder(6, 6, 6, 6)
        ));
        settingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        settingsPanel.add(title("Settings"));
        settingsPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        equipmentNames = area(config.equipmentNames(), 12);
        inventoryItems = area(config.inventoryItems(), 8);
        keepProtectMagic = new JCheckBox("Protect mage", config.keepProtectMagic());
        eatAtHp = spinner(config.eatAtHp(), 1, 99);
        emergencyTeleportHp = spinner(config.emergencyTeleportHp(), 1, 99);
        drinkPrayerAt = spinner(config.drinkPrayerAt(), 1, 99);
        bankWhenPrayerBelow = spinner(config.bankWhenPrayerBelow(), 0, 99);
        prayAtNearestAltar = new JCheckBox("Pray at nearest altar", config.prayAtNearestAltar());
        activePrayerNames = area(config.activePrayerNames(), 8);
        lootNames = area(defaultIfBlank(config.lootNames(), KspZombiePirateKillerConfig.DEFAULT_LOOT_NAMES), 18);
        minLootValue = spinner(config.minLootValue(), 0, 100_000_000);
        lootRange = spinner(config.lootRange(), 1, 15);
        attackRange = spinner(config.attackRange(), 1, 20);
        emptyContainerWidgetId = spinner(config.emptyContainerWidgetId(), 1, Integer.MAX_VALUE);
        useEmptyContainersButton = new JCheckBox("Use Empty containers", config.useEmptyContainersButton());

        addEditAreaSetting(settingsPanel, "Custom gear", equipmentNames, "Edit custom gear");
        addEditAreaSetting(settingsPanel, "Inventory restock", inventoryItems, "Edit inventory restock");
        addEditAreaSetting(settingsPanel, "Loot whitelist", lootNames, "Edit loot whitelist");
        addSpinnerSetting(settingsPanel, "Min loot GP", minLootValue);
        addPrayerSelectorSetting(settingsPanel, "Active prayers", activePrayerNames);
        settingsPanel.add(Box.createRigidArea(new Dimension(0, 2)));
        settingsPanel.add(fullWidth(keepProtectMagic));
        addSpinnerSetting(settingsPanel, "Eat HP", eatAtHp);
        addSpinnerSetting(settingsPanel, "Tele HP", emergencyTeleportHp);
        addSpinnerSetting(settingsPanel, "Drink pray", drinkPrayerAt);
        addSpinnerSetting(settingsPanel, "Bank pray", bankWhenPrayerBelow);
        settingsPanel.add(fullWidth(prayAtNearestAltar));
        addSpinnerSetting(settingsPanel, "Loot range", lootRange);
        addSpinnerSetting(settingsPanel, "Attack range", attackRange);
        addSpinnerSetting(settingsPanel, "Empty widget", emptyContainerWidgetId);
        settingsPanel.add(fullWidth(useEmptyContainersButton));
        settingsPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        JButton saveButton = new JButton("Save settings");
        saveButton.addActionListener(event -> saveSettings());
        settingsPanel.add(fullWidth(saveButton));
        settingsPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        JPanel controlRow = new JPanel(new GridLayout(1, 2, 6, 0));
        controlRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JButton startButton = new JButton("Start");
        startButton.addActionListener(event -> {
            saveSettings();
            plugin.startScriptFromPanel();
        });

        JButton stopButton = new JButton("Stop");
        stopButton.addActionListener(event -> plugin.stopScriptFromPanel());

        controlRow.add(startButton);
        controlRow.add(stopButton);
        settingsPanel.add(fullWidth(controlRow));

        settingsPanel.setVisible(false);
        return settingsPanel;
    }

    void setLicenseState(boolean valid, String message) {
        SwingUtilities.invokeLater(() -> {
            licenseStatus.setText(message == null ? "" : message);
            licenseStatus.setForeground(valid ? new Color(110, 210, 120) : new Color(230, 130, 130));
            settingsPanel.setVisible(valid);
            revalidate();
            repaint();
        });
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback == null ? "" : fallback;
        }
        return value;
    }

    private void saveSettings() {
        setString("equipmentNames", equipmentNames.getText());
        setString("inventoryItems", inventoryItems.getText());
        setBoolean("keepProtectMagic", keepProtectMagic.isSelected());
        setInt("eatAtHp", eatAtHp);
        setInt("emergencyTeleportHp", emergencyTeleportHp);
        setInt("drinkPrayerAt", drinkPrayerAt);
        setInt("bankWhenPrayerBelow", bankWhenPrayerBelow);
        setBoolean("prayAtNearestAltar", prayAtNearestAltar.isSelected());
        setString("activePrayerNames", activePrayerNames.getText());
        setString("lootWhitelistNames", lootNames.getText());
        setInt("minLootValue", minLootValue);
        setInt("lootRange", lootRange);
        setInt("attackRange", attackRange);
        setInt("emptyContainerWidgetId", emptyContainerWidgetId);
        setBoolean("useEmptyContainersButton", useEmptyContainersButton.isSelected());

        licenseStatus.setText("Settings saved.");
        licenseStatus.setForeground(new Color(110, 210, 120));
    }

    private void setString(String key, String value) {
        configManager.setConfiguration(KspZombiePirateKillerConfig.GROUP, key, value == null ? "" : value);
    }

    private void setBoolean(String key, boolean value) {
        configManager.setConfiguration(KspZombiePirateKillerConfig.GROUP, key, value);
    }

    private void setInt(String key, JSpinner spinner) {
        Object value = spinner.getValue();
        int intValue = value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
        configManager.setConfiguration(KspZombiePirateKillerConfig.GROUP, key, intValue);
    }

    private JPanel sectionPanel(int padding) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR),
                new EmptyBorder(padding, padding, padding, padding)
        ));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.LIGHT_GRAY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JTextArea area(String text, int rows) {
        JTextArea textArea = new JTextArea(text == null ? "" : text, rows, 24);
        textArea.setLineWrap(false);
        textArea.setWrapStyleWord(false);
        return textArea;
    }

    private JSpinner spinner(int value, int min, int max) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        spinner.setPreferredSize(new Dimension(66, spinner.getPreferredSize().height));
        return spinner;
    }

    private void addEditAreaSetting(JPanel panel, String name, JTextArea textArea, String dialogTitle) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.add(label(name), BorderLayout.WEST);

        JButton editButton = new JButton("Edit");
        editButton.addActionListener(event -> openTextEditor(dialogTitle, textArea));
        row.add(editButton, BorderLayout.EAST);

        panel.add(fullWidth(row));
        panel.add(Box.createRigidArea(new Dimension(0, 3)));
    }

    private void addPrayerSelectorSetting(JPanel panel, String name, JTextArea textArea) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.add(label(name), BorderLayout.WEST);

        JButton editButton = new JButton("Select");
        editButton.addActionListener(event -> openPrayerSelector(textArea));
        row.add(editButton, BorderLayout.EAST);

        panel.add(fullWidth(row));
        panel.add(Box.createRigidArea(new Dimension(0, 3)));
    }

    private void openPrayerSelector(JTextArea target) {
        Set<String> selected = new HashSet<>();
        for (String line : target.getText().split("\\R")) {
            String normalized = normalizePrayerName(line);
            if (!normalized.isEmpty()) {
                selected.add(normalized);
            }
        }

        JPanel selector = new JPanel();
        selector.setLayout(new BoxLayout(selector, BoxLayout.Y_AXIS));

        JCheckBox[] boxes = new JCheckBox[Rs2PrayerEnum.values().length];
        int index = 0;
        for (Rs2PrayerEnum prayer : Rs2PrayerEnum.values()) {
            JCheckBox box = new JCheckBox(prayer.getName());
            box.setSelected(selected.contains(normalizePrayerName(prayer.getName()))
                    || selected.contains(normalizePrayerName(prayer.name())));
            boxes[index++] = box;
            selector.add(box);
        }

        JScrollPane pane = new JScrollPane(selector);
        pane.setPreferredSize(new Dimension(300, 360));

        int result = JOptionPane.showConfirmDialog(
                this,
                pane,
                "Select active prayers",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            StringBuilder out = new StringBuilder();
            for (JCheckBox box : boxes) {
                if (box != null && box.isSelected()) {
                    if (out.length() > 0) {
                        out.append('\n');
                    }
                    out.append(box.getText());
                }
            }
            target.setText(out.toString());
        }
    }

    private String normalizePrayerName(String value) {
        return value == null ? "" : value.toLowerCase()
                .replace("from", "")
                .replace("of", "")
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .trim();
    }

    private void openTextEditor(String title, JTextArea target) {
        JTextArea editor = area(target.getText(), Math.max(10, target.getRows()));
        JScrollPane pane = new JScrollPane(editor);
        pane.setPreferredSize(new Dimension(380, 300));

        int result = JOptionPane.showConfirmDialog(
                this,
                pane,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            target.setText(editor.getText());
        }
    }

    private void addSpinnerSetting(JPanel panel, String name, JSpinner spinner) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.add(label(name), BorderLayout.WEST);
        row.add(spinner, BorderLayout.EAST);
        panel.add(fullWidth(row));
        panel.add(Box.createRigidArea(new Dimension(0, 2)));
    }

    private Component fullWidth(Component component) {
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        if (component instanceof JCheckBox) {
            component.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            component.setForeground(Color.LIGHT_GRAY);
        }
        if (component instanceof JComponent) {
            ((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        return component;
    }
}
