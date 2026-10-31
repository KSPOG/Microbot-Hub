package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import java.awt.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** RuneLite native side panel, modeled on AIO Fighter's slot grid and manual start controls. */
final class KspRuniteMinerKillerPanel extends PluginPanel {
    private static final Logger log=LoggerFactory.getLogger(KspRuniteMinerKillerPanel.class);
    private final KspRuniteMinerKillerGearStore store;
    private final ItemManager itemManager;
    private final Runnable onStart,onStop;
    private final Supplier<Boolean> isRunning;
    private final Map<EquipmentInventorySlot,JButton> buttons=new EnumMap<>(EquipmentInventorySlot.class);
    private final JButton capture=new JButton("Save current equipment");
    private final JButton start=new JButton("Start"),stop=new JButton("Stop");
    private final JLabel status=new JLabel("Enable the plugin, then press Start",SwingConstants.CENTER);
    private final JCheckBox hop=new JCheckBox("Hop worlds when no miners are visible");
    private final JSpinner seconds=new JSpinner(new SpinnerNumberModel(
        90,KspRuniteMinerKillerGearStore.MIN_HOP_DELAY_SECONDS,
        KspRuniteMinerKillerGearStore.MAX_HOP_DELAY_SECONDS,5));
    private final JButton recommendedHop=new JButton("Recommended (40s)");
    private final Timer timer;

    KspRuniteMinerKillerPanel(KspRuniteMinerKillerGearStore store,ItemManager manager,
                              Runnable onStart,Runnable onStop,Supplier<Boolean> isRunning) {
        this.store=store;this.itemManager=manager;this.onStart=onStart;this.onStop=onStop;this.isRunning=isRunning;
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(8,5,8,5));
        JLabel heading=label("KSP Runite Miner Killer",true);add(heading);
        JLabel sub=label("Magic PvP | Live scene targets | v0.1.32",false);add(sub);
        add(label("Saved combat equipment",true));
        JPanel gear=dark(new GridBagLayout());
        place(gear,EquipmentInventorySlot.HEAD,1,0);
        place(gear,EquipmentInventorySlot.CAPE,0,1);
        place(gear,EquipmentInventorySlot.AMULET,1,1);
        place(gear,EquipmentInventorySlot.AMMO,2,1);
        place(gear,EquipmentInventorySlot.WEAPON,0,2);
        place(gear,EquipmentInventorySlot.BODY,1,2);
        place(gear,EquipmentInventorySlot.SHIELD,2,2);
        place(gear,EquipmentInventorySlot.LEGS,1,3);
        place(gear,EquipmentInventorySlot.GLOVES,0,4);
        place(gear,EquipmentInventorySlot.BOOTS,1,4);
        place(gear,EquipmentInventorySlot.RING,2,4);
        add(gear);
        capture.setToolTipText("Save equipment from the live worn container; missing trailing slots count as empty");
        capture.addActionListener(e->captureCurrent());add(capture);
        JLabel help=label("Click a slot to search wearable items. Empty = no item requested.",false);add(help);
        add(label("World hopping",true));
        hop.setOpaque(false);hop.setForeground(Color.WHITE);
        hop.setSelected(store.hopEnabled());hop.addActionListener(e->store.hopEnabled(hop.isSelected()));add(hop);
        JPanel wait=dark(new FlowLayout(FlowLayout.LEFT,5,2));
        JLabel waitLabel=label("No target for (seconds):",false);wait.add(waitLabel);
        seconds.setValue(store.hopDelay());
        seconds.setToolTipText("10-600 seconds without a visible miner. 10s permits bursts but may cause hop throttling.");
        seconds.addChangeListener(e->store.hopDelay((Integer)seconds.getValue()));
        wait.add(seconds);add(wait);
        JPanel hopPreset=dark(new FlowLayout(FlowLayout.LEFT,5,2));
        recommendedHop.setToolTipText("Set to 40 seconds: ~90 hops/hour, below the community-estimated 93/hour replenishment. Not a Jagex guarantee.");
        recommendedHop.addActionListener(e->{
            seconds.setValue(KspRuniteMinerKillerGearStore.RECOMMENDED_HOP_DELAY_SECONDS);
            store.hopDelay(KspRuniteMinerKillerGearStore.RECOMMENDED_HOP_DELAY_SECONDS);
        });
        hopPreset.add(recommendedHop);add(hopPreset);
        JLabel hopInfo=label("Minimum 10s (burst) | Recommended 40s",false);
        hopInfo.setToolTipText("40s is an estimated sustainable interval, not an official lockout-safe limit.");
        hopInfo.setFont(hopInfo.getFont().deriveFont(10f));
        add(hopInfo);
        add(label("Automation",true));
        status.setForeground(new Color(205,205,215));add(status);
        JPanel controls=dark(new FlowLayout(FlowLayout.CENTER,6,4));
        start.setPreferredSize(new Dimension(95,31));stop.setPreferredSize(new Dimension(95,31));
        start.addActionListener(e->{onStart.run();refresh();});
        stop.addActionListener(e->{onStop.run();refresh();});
        controls.add(start);controls.add(stop);add(controls);
        for(EquipmentInventorySlot slot:KspRuniteMinerKillerGearStore.SLOTS) refreshSlot(slot);
        refresh();timer=new Timer(1250,e->refresh());timer.start();
    }
    @Override public void removeNotify(){timer.stop();super.removeNotify();}
    @Override public void addNotify(){super.addNotify();if(timer!=null)timer.start();}
    void refresh(){boolean running=isRunning.get();start.setEnabled(!running);stop.setEnabled(running);
        status.setText(running?"Running - press Stop to end":"Idle - press Start to begin");}
    private void place(JPanel panel,EquipmentInventorySlot slot,int col,int row){
        JButton b=new JButton();b.setPreferredSize(new Dimension(67,73));b.setMargin(new Insets(1,1,1,1));
        // Center RuneLite's 36px sprite above a short slot/item label.
        b.setHorizontalAlignment(SwingConstants.CENTER);
        b.setVerticalAlignment(SwingConstants.CENTER);
        b.setHorizontalTextPosition(SwingConstants.CENTER);
        b.setVerticalTextPosition(SwingConstants.BOTTOM);
        b.setIconTextGap(2);
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 9f));
        b.setForeground(new Color(225,225,235));
        b.setBackground(new Color(36,36,40));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(new Color(72,72,77)));
        b.addActionListener(e->edit(slot));
        GridBagConstraints c=new GridBagConstraints();c.gridx=col;c.gridy=row;c.insets=new Insets(2,2,2,2);
        panel.add(b,c);buttons.put(slot,b);
    }
    private void edit(EquipmentInventorySlot slot){
        KspRuniteMinerKillerGearPicker.show(SwingUtilities.getWindowAncestor(this),slot,itemManager,item->{
            store.set(slot,item.name,item.id);
            refreshSlot(slot);
        });
    }
    private void refreshSlot(EquipmentInventorySlot slot){
        JButton b=buttons.get(slot);
        String name=store.get(slot);
        int id=store.itemId(slot);
        b.setIcon(null);
        b.setText(shortName(name.isEmpty()?slot.name():name));
        b.setToolTipText(slot.name()+": "+(name.isEmpty()?"Empty":name + " [item " + id + "]"));
        if (id > 0) {
            try {
                // Exact RuneLite signature: AsyncBufferedImage.addTo(JButton).
                // The async image registers a repaint when the sprite finishes loading.
                itemManager.getImage(id).addTo(b);
            } catch (RuntimeException ex) {
                status.setText("Could not display " + slot.name() + " sprite; selection remains saved.");
            }
        }
    }
    private static String shortName(String s){return s.length()>13?s.substring(0,11)+"..":s;}
    private void captureCurrent(){
        if(!Microbot.isLoggedIn()){
            status.setText("Log into the game before saving equipment.");
            return;
        }
        capture.setEnabled(false);
        status.setText("Reading live worn equipment...");
        new SwingWorker<Map<EquipmentInventorySlot,KspRuniteMinerKillerGearStore.GearItem>,Void>(){
            @Override
            protected Map<EquipmentInventorySlot,KspRuniteMinerKillerGearStore.GearItem> doInBackground() throws Exception {
                // Must run the actual container read on the RuneLite client thread.
                // Retrying here (off the client thread) handles brief login/hop transitions.
                // InventoryID.WORN is container 94 on real Microbot, NOT container 0.
                for(int attempt=0;attempt<6;attempt++){
                    if(isCancelled())return null;
                    if(!Microbot.isLoggedIn()){
                        throw new IllegalStateException("Log in to the game before saving equipment.");
                    }
                    Map<EquipmentInventorySlot,KspRuniteMinerKillerGearStore.GearItem> equipped =
                        Microbot.getClientThread().invoke(() -> {
                            ItemContainer worn = Microbot.getClient().getItemContainer(InventoryID.WORN);
                            if(worn==null || worn.getItems()==null)return null;
                            // Snapshot every displayed slot in one client-thread read.
                            // Empty slots are explicit, not omitted, so a second save clears stale gear.
                            return KspRuniteMinerKillerEquipmentSnapshot.read(
                                worn.getItems(), id -> Microbot.getClient().getItemDefinition(id));
                        });
                    if(equipped!=null)return equipped;
                    if(attempt<5)Thread.sleep(300);
                }
                throw new IllegalStateException("Worn equipment is not available yet (container 94). " +
                    "Make sure the character is fully logged in, then try Save current equipment again.");
            }
            @Override
            protected void done(){
                capture.setEnabled(true);
                try{
                    Map<EquipmentInventorySlot,KspRuniteMinerKillerGearStore.GearItem> equipped=get();
                    if(equipped==null){status.setText("Equipment capture cancelled; saved setup unchanged.");return;}
                    int occupied=KspRuniteMinerKillerEquipmentSnapshot.occupiedCount(equipped);
                    if(occupied==0){
                        // An unexpectedly empty container must never wipe a previously saved loadout.
                        status.setText("All equipment slots appear empty; saved setup unchanged.");
                        return;
                    }
                    store.replace(equipped);
                    for(EquipmentInventorySlot slot:KspRuniteMinerKillerGearStore.SLOTS)refreshSlot(slot);
                    status.setText("Saved all " + equipped.size() + " slots: " + occupied +
                        " equipped, " + (equipped.size()-occupied) + " empty.");
                }catch(InterruptedException ex){
                    Thread.currentThread().interrupt();
                    status.setText("Equipment capture interrupted; saved setup unchanged.");
                }catch(ExecutionException ex){
                    Throwable cause=ex.getCause()==null?ex:ex.getCause();
                    String message=cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage();
                    status.setText("Equipment not saved. " + message);
                    // Routine capture errors belong in the panel and client log, not modal dialogs.
                    // Only a verified missing saved gear item (bank + inventory) may open a dialog.
                    log.warn("[KSP Runite Miner Killer] equipment capture failed: {}",message);
                }
            }
        }.execute();
    }
    private static JPanel dark(LayoutManager layout){JPanel p=new JPanel(layout);p.setBackground(ColorScheme.DARK_GRAY_COLOR);return p;}
    private static JLabel label(String s,boolean heading){JLabel l=new JLabel(s,SwingConstants.CENTER);l.setForeground(heading?Color.WHITE:new Color(188,188,195));
        if(heading)l.setFont(l.getFont().deriveFont(Font.BOLD,13f));l.setBorder(new EmptyBorder(6,0,5,0));return l;}
}
