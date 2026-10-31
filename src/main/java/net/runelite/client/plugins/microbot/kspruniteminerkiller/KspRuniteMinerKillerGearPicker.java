package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.microbot.Microbot;

/** Searchable slot-correct equipment browser following the AIO Fighter equipment index. */
final class KspRuniteMinerKillerGearPicker {
    private static volatile Map<EquipmentInventorySlot,List<KspRuniteMinerKillerGearStore.GearItem>> cache;
    private static final Map<Integer,EquipmentInventorySlot> BY_SLOT=slotIndex();
    private KspRuniteMinerKillerGearPicker() {}

    static void show(Window parent,EquipmentInventorySlot slot,ItemManager items,
                     Consumer<KspRuniteMinerKillerGearStore.GearItem> onSelected){
        JDialog dialog=new JDialog(parent,"Select "+slot.name()+" equipment",JDialog.ModalityType.MODELESS);
        JPanel root=new JPanel(new BorderLayout(5,7));
        root.setBorder(javax.swing.BorderFactory.createEmptyBorder(9,9,9,9));
        JTextField search=new JTextField();
        search.setToolTipText("Filter wearable items by name");
        root.add(search,BorderLayout.NORTH);
        DefaultListModel<KspRuniteMinerKillerGearStore.GearItem> model=new DefaultListModel<>();
        JList<KspRuniteMinerKillerGearStore.GearItem> list=new JList<>(model);
        list.setCellRenderer((l,item,index,selected,focus)->{
            JLabel label=new JLabel(item==null?"":item.name);
            label.setOpaque(true);
            label.setBackground(selected?l.getSelectionBackground():l.getBackground());
            label.setForeground(selected?l.getSelectionForeground():l.getForeground());
            return label;
        });
        root.add(new JScrollPane(list),BorderLayout.CENTER);
        JLabel status=new JLabel("Loading equipment catalog...");
        JPanel bottom=new JPanel(new BorderLayout(3,3));bottom.add(status,BorderLayout.NORTH);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,3,2));
        JButton clear=new JButton("Leave empty");
        clear.addActionListener(e->{onSelected.accept(new KspRuniteMinerKillerGearStore.GearItem("",-1));dialog.dispose();});
        JButton manual=new JButton("Enter name");
        manual.addActionListener(e->{String name=javax.swing.JOptionPane.showInputDialog(dialog,"Item name:");
            if(name!=null){onSelected.accept(new KspRuniteMinerKillerGearStore.GearItem(name.trim(),-1));dialog.dispose();}});
        JButton select=new JButton("Select");select.setEnabled(false);
        select.addActionListener(e->{KspRuniteMinerKillerGearStore.GearItem gear=list.getSelectedValue();
            if(gear!=null){onSelected.accept(gear);dialog.dispose();}});
        JButton cancel=new JButton("Cancel");cancel.addActionListener(e->dialog.dispose());
        actions.add(clear);actions.add(manual);actions.add(cancel);actions.add(select);
        bottom.add(actions,BorderLayout.CENTER);root.add(bottom,BorderLayout.SOUTH);
        dialog.setContentPane(root);dialog.setSize(new Dimension(405,510));dialog.setLocationRelativeTo(parent);
        final List<KspRuniteMinerKillerGearStore.GearItem>[] active=new List[]{Collections.emptyList()};
        Runnable filter=()->{
            String q=search.getText().trim().toLowerCase(Locale.ROOT);
            model.clear();int shown=0;
            for(KspRuniteMinerKillerGearStore.GearItem item:active[0]){
                if(item.name.toLowerCase(Locale.ROOT).contains(q)){
                    model.addElement(item);shown++;if(shown>=500)break;
                }
            }
            status.setText(active[0].isEmpty()?"Loading / no wearable items found":shown+" matches (max 500)");
            select.setEnabled(!model.isEmpty());
        };
        search.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e){filter.run();}
            public void removeUpdate(DocumentEvent e){filter.run();}
            public void changedUpdate(DocumentEvent e){filter.run();}
        });
        list.addListSelectionListener(e->select.setEnabled(list.getSelectedValue()!=null));
        list.addMouseListener(new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent e){
            if(e.getClickCount()==2 && list.getSelectedValue()!=null)select.doClick();
        }});
        Map<EquipmentInventorySlot,List<KspRuniteMinerKillerGearStore.GearItem>> existing=cache;
        if(existing!=null){active[0]=existing.getOrDefault(slot,Collections.emptyList());filter.run();}
        else new SwingWorker<Map<EquipmentInventorySlot,List<KspRuniteMinerKillerGearStore.GearItem>>,Void>(){
            @Override protected Map<EquipmentInventorySlot,List<KspRuniteMinerKillerGearStore.GearItem>> doInBackground(){
                return Microbot.getClientThread().invoke(()->indexFromLiveDefinitions(items));
            }
            @Override protected void done(){try{
                Map<EquipmentInventorySlot,List<KspRuniteMinerKillerGearStore.GearItem>> indexed=get();
                if(indexed==null)throw new IllegalStateException("No equipment data");
                cache=indexed;active[0]=indexed.getOrDefault(slot,Collections.emptyList());filter.run();
            }catch(Exception ex){status.setText("Catalog unavailable; use Enter name");}}
        }.execute();
        dialog.setVisible(true);
    }

    private static Map<EquipmentInventorySlot,List<KspRuniteMinerKillerGearStore.GearItem>> indexFromLiveDefinitions(ItemManager manager){
        EnumMap<EquipmentInventorySlot,LinkedHashMap<String,KspRuniteMinerKillerGearStore.GearItem>> result=
            new EnumMap<>(EquipmentInventorySlot.class);
        for(EquipmentInventorySlot slot:KspRuniteMinerKillerGearStore.SLOTS)result.put(slot,new LinkedHashMap<>());
        for(int id=0;id<Microbot.getClient().getItemCount();id++){
            try{
                ItemComposition composition=Microbot.getClient().getItemDefinition(id);
                if(composition==null||composition.getName()==null||composition.getName().isBlank()
                        ||"null".equalsIgnoreCase(composition.getName())||composition.getNote()!=-1
                        ||composition.getPlaceholderTemplateId()>0)continue;
                ItemStats stats=manager.getItemStats(id);
                ItemEquipmentStats equipment=stats==null?null:stats.getEquipment();
                EquipmentInventorySlot slot=equipment==null?null:BY_SLOT.get(equipment.getSlot());
                if(slot==null||!stats.isEquipable()||!result.containsKey(slot))continue;
                String name=composition.getName().trim();
                result.get(slot).putIfAbsent(name.toLowerCase(Locale.ROOT),
                        new KspRuniteMinerKillerGearStore.GearItem(name,id));
            }catch(Exception ignored){}
        }
        EnumMap<EquipmentInventorySlot,List<KspRuniteMinerKillerGearStore.GearItem>> indexed=
                new EnumMap<>(EquipmentInventorySlot.class);
        result.forEach((slot,byName)->{
            List<KspRuniteMinerKillerGearStore.GearItem> list=new ArrayList<>(byName.values());
            list.sort(Comparator.comparing(a->a.name,String.CASE_INSENSITIVE_ORDER));
            indexed.put(slot,Collections.unmodifiableList(list));
        });
        return Collections.unmodifiableMap(indexed);
    }
    private static Map<Integer,EquipmentInventorySlot> slotIndex(){
        Map<Integer,EquipmentInventorySlot> index=new HashMap<>();
        for(EquipmentInventorySlot slot:EquipmentInventorySlot.values())index.put(slot.getSlotIdx(),slot);
        return Collections.unmodifiableMap(index);
    }
}
