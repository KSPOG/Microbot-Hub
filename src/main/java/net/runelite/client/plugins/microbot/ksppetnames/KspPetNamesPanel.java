package net.runelite.client.plugins.microbot.ksppetnames;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public final class KspPetNamesPanel extends PluginPanel
{
    private static final Color ACCENT = new Color(80, 220, 120);
    private static final Color ERROR = new Color(245, 105, 105);

    private final KspPetNamesService service;

    private final JLabel currentPetValue = valueLabel();
    private final JLabel npcIdValue = valueLabel();
    private final JLabel currentSavedValue = valueLabel();
    private final JTextField nicknameField = new JTextField();

    private final JButton saveButton = new JButton("Save Name");
    private final JButton clearButton = new JButton("Clear Current");
    private final JButton deleteButton = new JButton("Delete Selected Saved Name");

    private final DefaultListModel<KspPetNamesService.SavedPet> savedModel = new DefaultListModel<>();
    private final JList<KspPetNamesService.SavedPet> savedList = new JList<>(savedModel);
    private final JTextArea statusArea = new JTextArea();

    private final Timer refreshTimer;

    private String lastPetKey = null;
    private boolean forceFieldReload = true;

    KspPetNamesPanel(KspPetNamesService service)
    {
        super(false);
        this.service = service;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(5, 5, 5, 5));

        JLabel title = new JLabel("KSP Pet Names");
        title.setForeground(ACCENT);
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);

        JLabel version = new JLabel("v" + KspPetNamesPlugin.VERSION + " • account-bound");
        version.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        version.setFont(FontManager.getRunescapeSmallFont());
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(version);
        content.add(Box.createVerticalStrut(5));

        JPanel current = section("Current pet");
        current.add(row("Pet", currentPetValue));
        current.add(row("NPC ID", npcIdValue));
        current.add(row("Saved name", currentSavedValue));
        current.add(Box.createVerticalStrut(4));

        JLabel nameLabel = new JLabel("Custom name");
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        current.add(nameLabel);

        nicknameField.setFont(FontManager.getRunescapeSmallFont());
        nicknameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        nicknameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        nicknameField.setToolTipText("Name for the active pet on the currently active RuneScape account profile.");
        nicknameField.addActionListener(e -> saveCurrent());
        current.add(nicknameField);
        current.add(Box.createVerticalStrut(3));

        configureButton(saveButton);
        configureButton(clearButton);
        saveButton.addActionListener(e -> saveCurrent());
        clearButton.addActionListener(e -> clearCurrent());

        current.add(saveButton);
        current.add(Box.createVerticalStrut(2));
        current.add(clearButton);
        content.add(current);
        content.add(Box.createVerticalStrut(5));

        JPanel saved = section("Saved names - active account");
        savedList.setFont(FontManager.getRunescapeSmallFont());
        savedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        savedList.setVisibleRowCount(6);

        JScrollPane savedScroll = new JScrollPane(savedList);
        savedScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        savedScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 126));
        saved.add(savedScroll);
        saved.add(Box.createVerticalStrut(3));

        configureButton(deleteButton);
        deleteButton.addActionListener(e -> deleteSelected());
        saved.add(deleteButton);

        JLabel accountNote = new JLabel("<html>Names are stored in the active RuneScape profile,<br>not in a shared/global plugin setting.</html>");
        accountNote.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        accountNote.setFont(FontManager.getRunescapeSmallFont());
        accountNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        saved.add(Box.createVerticalStrut(4));
        saved.add(accountNote);

        content.add(saved);
        content.add(Box.createVerticalStrut(5));

        JPanel status = section("Status");
        statusArea.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusArea.setFont(FontManager.getRunescapeSmallFont());
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusArea.setEditable(false);
        statusArea.setFocusable(false);
        statusArea.setOpaque(false);
        statusArea.setRows(3);
        statusArea.setText("Summon a pet, enter a name, then press Save Name.");
        status.add(statusArea);
        content.add(status);

        add(content, BorderLayout.NORTH);

        refreshTimer = new Timer(350, e -> refresh());
        refreshTimer.start();
        refresh();
    }

    void dispose()
    {
        refreshTimer.stop();
    }

    void onProfileChanged()
    {
        lastPetKey = null;
        forceFieldReload = true;
        setStatus("Active RuneScape profile changed. Loaded that account's pet names.", false);
        refresh();
    }

    private void saveCurrent()
    {
        KspPetNamesService.SaveResult result = service.saveCurrentPetName(nicknameField.getText());
        setStatus(result.getMessage(), !result.isSuccess());
        forceFieldReload = true;
        refresh();
    }

    private void clearCurrent()
    {
        KspPetNamesService.SaveResult result = service.clearCurrentPetName();
        setStatus(result.getMessage(), !result.isSuccess());
        forceFieldReload = true;
        refresh();
    }

    private void deleteSelected()
    {
        KspPetNamesService.SavedPet selected = savedList.getSelectedValue();
        if (selected == null)
        {
            setStatus("Select a saved pet name first.", true);
            return;
        }

        KspPetNamesService.SaveResult result = service.deleteSavedPet(selected.getPetKey());
        setStatus(result.getMessage(), !result.isSuccess());
        forceFieldReload = true;
        refresh();
    }

    private void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        KspPetNamesService.PetSnapshot pet = service.getCurrentPet();

        if (!pet.isPresent())
        {
            currentPetValue.setText("None");
            npcIdValue.setText("-");
            currentSavedValue.setText("-");
            saveButton.setEnabled(false);
            clearButton.setEnabled(false);

            if (forceFieldReload || lastPetKey != null)
            {
                nicknameField.setText("");
            }

            nicknameField.setEnabled(false);
            lastPetKey = null;
            forceFieldReload = false;
        }
        else
        {
            currentPetValue.setText(ellipsize(pet.getGameName(), 22));
            currentPetValue.setToolTipText(pet.getGameName());
            npcIdValue.setText(String.valueOf(pet.getNpcId()));
            currentSavedValue.setText(pet.getNickname().isEmpty() ? "Not named" : ellipsize(pet.getNickname(), 22));
            currentSavedValue.setToolTipText(pet.getNickname());

            nicknameField.setEnabled(true);
            saveButton.setEnabled(true);
            clearButton.setEnabled(!pet.getNickname().isEmpty());

            boolean petChanged = lastPetKey == null || !lastPetKey.equals(pet.getPetKey());
            if (petChanged || forceFieldReload)
            {
                nicknameField.setText(pet.getNickname());
            }

            lastPetKey = pet.getPetKey();
            forceFieldReload = false;
        }

        refreshSavedNames();
    }

    private void refreshSavedNames()
    {
        String selectedKey = null;
        KspPetNamesService.SavedPet selected = savedList.getSelectedValue();
        if (selected != null)
        {
            selectedKey = selected.getPetKey();
        }

        List<KspPetNamesService.SavedPet> pets = service.getSavedPets();

        boolean different = savedModel.size() != pets.size();
        if (!different)
        {
            for (int i = 0; i < pets.size(); i++)
            {
                KspPetNamesService.SavedPet old = savedModel.getElementAt(i);
                KspPetNamesService.SavedPet now = pets.get(i);
                if (!old.getPetKey().equals(now.getPetKey())
                    || !old.getNickname().equals(now.getNickname())
                    || !old.getGameName().equals(now.getGameName()))
                {
                    different = true;
                    break;
                }
            }
        }

        if (different)
        {
            savedModel.clear();
            for (KspPetNamesService.SavedPet pet : pets)
            {
                savedModel.addElement(pet);
            }

            if (selectedKey != null)
            {
                for (int i = 0; i < savedModel.size(); i++)
                {
                    if (selectedKey.equals(savedModel.getElementAt(i).getPetKey()))
                    {
                        savedList.setSelectedIndex(i);
                        break;
                    }
                }
            }
        }

        deleteButton.setEnabled(savedList.getSelectedValue() != null);
    }

    private void setStatus(String text, boolean error)
    {
        statusArea.setText(text == null ? "" : text);
        statusArea.setForeground(error ? ERROR : ColorScheme.LIGHT_GRAY_COLOR);
    }

    private static JPanel section(String title)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(4, 4, 4, 4)
        ));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(title);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(label);
        panel.add(Box.createVerticalStrut(2));
        return panel;
    }

    private static JPanel row(String leftText, JLabel right)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel left = new JLabel(leftText);
        left.setForeground(Color.WHITE);
        left.setFont(FontManager.getRunescapeSmallFont());

        right.setHorizontalAlignment(JLabel.RIGHT);

        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.CENTER);
        return row;
    }

    private static JLabel valueLabel()
    {
        JLabel label = new JLabel("-");
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont());
        return label;
    }

    private static void configureButton(JButton button)
    {
        button.setFocusPainted(false);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        button.setPreferredSize(new Dimension(0, 25));
    }

    private static String ellipsize(String value, int max)
    {
        if (value == null)
        {
            return "";
        }

        if (value.length() <= max)
        {
            return value;
        }

        return value.substring(0, Math.max(1, max - 1)) + "…";
    }
}
