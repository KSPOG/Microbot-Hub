package net.runelite.client.plugins.microbot.kfires;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

@PluginDescriptor(
        name=PluginConstants.KSP+"Firemaking",
        description="Burns configured logs using nearby fires/Forester's Campfires or a selected tile",
        tags={"ksp","firemaking","campfire"},
        version=KspFiremakingPlugin.VERSION,
        minClientVersion="2.6.19",
        enabledByDefault=PluginConstants.DEFAULT_ENABLED,
        isExternal=PluginConstants.IS_EXTERNAL
)
public class KspFiremakingPlugin extends Plugin {
    public static final String VERSION="0.0.10";
    @Inject private Client client;
    @Inject private KspFiremakingConfig config;
    @Inject private OverlayManager overlays;
    private KspFiremakingScript script;
    private KspFiremakingOverlay overlay;
    volatile WorldPoint tile;
    volatile String status="Starting";
    volatile String fire="None";
    volatile int burned, startFmXp, lastFmXp;
    volatile long started;

    @Provides KspFiremakingConfig provideConfig(ConfigManager m) { return m.getConfig(KspFiremakingConfig.class); }
    void status(String s) { status=s; Microbot.status=s; }
    @Override protected void startUp() {
        burned=0; startFmXp=lastFmXp=client.getSkillExperience(Skill.FIREMAKING);
        started=System.currentTimeMillis(); overlays.add(overlay=new KspFiremakingOverlay(this,config,client));
        (script=new KspFiremakingScript(this,config)).start();
    }
    @Override protected void shutDown() {
        if (script!=null) script.shutdown();
        if (overlay!=null) overlays.remove(overlay);
    }

    @Subscribe public void onStatChanged(StatChanged e) {
        if (e.getSkill()==Skill.FIREMAKING && e.getXp()>lastFmXp) { burned++; lastFmXp=e.getXp(); }
    }

    @Subscribe public void onMenuEntryAdded(MenuEntryAdded e) {
        MenuAction a=e.getMenuEntry().getType();
        if (a!=MenuAction.WALK && a!=MenuAction.SET_HEADING) return;
        WorldView w=client.getWorldView(e.getMenuEntry().getWorldViewId());
        Tile t=w==null?null:w.getSelectedSceneTile();
        if (t==null) return;
        WorldPoint p=WorldPoint.fromLocalInstance(client,t.getLocalLocation());
        client.createMenuEntry(-1).setOption("Set Firemaking Tile").setTarget("Tile")
                .setType(MenuAction.RUNELITE).onClick(x->{ tile=p; status("Tile set"); });
    }
}

class KspFiremakingScript extends Script {
    private static final int BURN_WIDGET=17694735, CAMPFIRE_MIN=49927, CAMPFIRE_MAX=49932;
    private final KspFiremakingPlugin plugin;
    private final KspFiremakingConfig config;
    private int lastCount=-1, phase, bankPhase;
    private boolean burning, wasAnimating, banking;

    KspFiremakingScript(KspFiremakingPlugin plugin,KspFiremakingConfig config) { this.plugin=plugin; this.config=config; }

    boolean start() {
        mainScheduledFuture=scheduledExecutorService.scheduleWithFixedDelay(()->{
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) { plugin.status("Paused / blocked"); return; }
                WorldPoint tile=plugin.tile;
                if (tile==null) { plugin.fire="None"; plugin.status("Right-click tile -> Set Firemaking Tile"); return; }

                KspFiremakingConfig.Log log=config.log();
                int count=Rs2Inventory.count(log.id);
                boolean anim=Rs2Player.isAnimating();
                Rs2TileObjectModel fire=fire(tile);
                boolean needTinderbox=fire==null;
                plugin.fire=fire==null?"None":fire.getName();

                if (lastCount<0) lastCount=count;
                if (count!=lastCount) {
                    lastCount=count; phase=0;
                    if (burning && count>0) { plugin.status("Burning "+log.name); return; }
                    if (count==0) burning=false;
                }

                if (anim) { wasAnimating=true; plugin.status(burning?"Burning "+log.name:"Firemaking"); return; }
                if (wasAnimating) { wasAnimating=false; if (!burning) phase=0; }

                // Never leave banking while the bank widget is still present.
                if (count==0 || banking || bankWidget()
                        || (needTinderbox && !Rs2Inventory.hasItem(ItemID.TINDERBOX))
                        || (!needTinderbox && Rs2Inventory.hasItem(ItemID.TINDERBOX))) {
                    burning=false; phase=0; banking=true;
                    if (!bank(log,needTinderbox)) return;
                    lastCount=Rs2Inventory.count(log.id);
                    return;
                }

                // A visible burn widget is authoritative: process it before any new world click.
                if (burnWidget()) {
                    if (count>0 && phase!=5) {
                        plugin.status("Starting campfire burn");
                        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                        phase=5; burning=true; lastCount=count;
                    }
                    return;
                }

                if (burning) {
                    if (fire!=null && !Rs2Player.isMoving()) return;
                    burning=false; phase=0;
                }

                if (fire!=null) { burn(log,fire,count); return; }

                if (phase>=3) {
                    phase=0;
                    if (Rs2Inventory.isItemSelected()) Rs2Inventory.deselect();
                }

                if (!Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
                    plugin.status("Getting tinderbox");
                    banking=true; bankPhase=0;
                    return;
                }

                if (Rs2Player.getWorldLocation().distanceTo(tile)>0) {
                    plugin.status("Walking to tile");
                    Rs2Walker.walkTo(tile,0);
                    return;
                }

                light(log,count);
            } catch (Exception e) {
                if (!(e.getCause() instanceof InterruptedException) && !Thread.currentThread().isInterrupted())
                    Microbot.logStackTrace("KspFiremaking",e);
            }
        },0,100,TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean bankWidget() { return Rs2Widget.isWidgetVisible(Rs2Bank.BANK_ITEM_CONTAINER); }
    private boolean burnWidget() { return Rs2Widget.isWidgetVisible(BURN_WIDGET); }

    private void light(KspFiremakingConfig.Log log,int count) {
        if (bankWidget() || burnWidget()) return;
        if (phase==2) { plugin.status("Lighting "+log.name); return; }
        if (!Rs2Inventory.isItemSelected()) {
            plugin.status("Selecting tinderbox");
            if (Rs2Inventory.use(ItemID.TINDERBOX)) phase=1;
            return;
        }
        if (Rs2Inventory.getSelectedItemId()!=ItemID.TINDERBOX) {
            Rs2Inventory.deselect(); phase=0; return;
        }
        plugin.status("Lighting "+log.name);
        if (Rs2Inventory.use(log.id)) { phase=2; lastCount=count; }
    }

    private Rs2TileObjectModel fire(WorldPoint tile) {
        return Microbot.getRs2TileObjectCache().query()
                .where(o->o.getWorldLocation()!=null && o.getWorldLocation().getPlane()==tile.getPlane()
                        && (o.getId()==ObjectID.FIRE || o.getId()>=CAMPFIRE_MIN && o.getId()<=CAMPFIRE_MAX))
                .nearest(tile,15);
    }

    private void burn(KspFiremakingConfig.Log log,Rs2TileObjectModel fire,int count) {
        if (bankWidget() || Rs2Player.isMoving()) return;

        if (burnWidget()) {
            if (phase!=5) {
                plugin.status("Starting campfire burn");
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                phase=5; burning=true; lastCount=count;
            }
            return;
        }

        // If the click consumed the item selection but no burn widget appeared and
        // the player is no longer busy, the interaction did not complete. Re-arm it.
        if (phase==4) {
            if (Rs2Inventory.isItemSelected() || Rs2Player.isAnimating()) return;
            phase=0;
        }

        if (!Rs2Inventory.isItemSelected()) {
            plugin.status("Selecting "+log.name);
            if (Rs2Inventory.use(log.id)) phase=3;
            return;
        }

        if (Rs2Inventory.getSelectedItemId()!=log.id) {
            Rs2Inventory.deselect(); phase=0; return;
        }

        plugin.status("Adding "+log.name+" to fire");
        if (fire.click()) phase=4;
    }

    private boolean bank(KspFiremakingConfig.Log log,boolean needTinderbox) {
        if (bankPhase==0) {
            if (!Rs2Bank.isOpen()) {
                plugin.status("Opening bank");
                if (!Rs2Bank.openBank()) Rs2Bank.walkToBank();
                return false;
            }
            bankPhase=1;
        }

        // Rs2Bank can report open before its actual item-container widget is ready.
        if (!bankWidget()) {
            plugin.status(bankPhase>=3?"Waiting for bank to close":"Waiting for bank widget");
            if (!Rs2Bank.isOpen() && bankPhase>=3) bankPhase=4;
            else return false;
        }

        if (bankPhase==1) {
            if (!Rs2Bank.hasItem(log.id) && !Rs2Inventory.hasItem(log.id)) {
                plugin.status("No "+log.name+" in bank");
                return false;
            }

            plugin.status(needTinderbox?"Banking / getting tinderbox":"Banking tinderbox");
            Rs2Bank.depositAllExcept(ItemID.TINDERBOX);

            if (needTinderbox) {
                if (!Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
                    if (!Rs2Bank.hasItem(ItemID.TINDERBOX)) {
                        plugin.status("No tinderbox in bank");
                        return false;
                    }
                    Rs2Bank.withdrawOne(ItemID.TINDERBOX);
                }
            } else if (Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
                Rs2Bank.depositAll(ItemID.TINDERBOX);
            }

            if (Rs2Bank.hasItem(log.id)) Rs2Bank.withdrawAll(log.id);
            bankPhase=2;
            return false;
        }

        if (bankPhase==2) {
            if (!Rs2Inventory.hasItem(log.id)) {
                plugin.status("Waiting for logs widget update");
                return false;
            }
            if (needTinderbox && !Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
                plugin.status("Waiting for tinderbox widget update");
                return false;
            }
            if (!needTinderbox && Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
                plugin.status("Waiting for tinderbox deposit");
                return false;
            }

            plugin.status("Closing bank");
            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
                bankPhase=3;
                return false;
            }
            bankPhase=3;
        }

        if (bankPhase==3) {
            if (Rs2Bank.isOpen() || bankWidget()) {
                plugin.status("Waiting for bank widget to close");
                return false;
            }
            bankPhase=4;
        }

        if (bankPhase==4) {
            if (burnWidget()) { plugin.status("Waiting for burn widget"); return false; }
            if (Rs2Player.isMoving()) { plugin.status("Waiting for movement"); return false; }
            if (Rs2Player.isAnimating()) { plugin.status("Waiting for animation"); return false; }
            if (Rs2Inventory.isItemSelected()) { Rs2Inventory.deselect(); return false; }

            plugin.status("Banking complete");
            bankPhase=0; banking=false; lastCount=Rs2Inventory.count(log.id);
            return true;
        }
        return false;
    }
}

class KspFiremakingOverlay extends OverlayPanel {
    private final KspFiremakingPlugin p;
    private final KspFiremakingConfig c;
    private final Client client;

    KspFiremakingOverlay(KspFiremakingPlugin p,KspFiremakingConfig c,Client client) {
        super(p); this.p=p; this.c=c; this.client=client; setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override public Dimension render(Graphics2D g) {
        if (!Microbot.isLoggedIn()) return null;
        KspFiremakingConfig.Log log=c.log();
        long sec=(System.currentTimeMillis()-p.started)/1000, xp=client.getSkillExperience(Skill.FIREMAKING)-p.startFmXp;
        long rate=sec<1?0:p.burned*3600L/sec;
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(230,0));
        panelComponent.getChildren().add(TitleComponent.builder().text("KSP Firemaking v"+KspFiremakingPlugin.VERSION).color(new Color(255,180,70)).build());
        line("Status",p.status); line("Log",log.name); line("Remaining",String.valueOf(Rs2Inventory.count(log.id)));
        line("Burned",p.burned+" ("+rate+"/h)"); line("Fire/Campfire",p.fire);
        line("FM level",String.valueOf(client.getRealSkillLevel(Skill.FIREMAKING))); line("XP gained",String.valueOf(xp));
        line("Runtime",String.format("%02d:%02d:%02d",sec/3600,sec/60%60,sec%60));
        line("Tile",p.tile==null?"Not set":p.tile.getX()+","+p.tile.getY()+","+p.tile.getPlane());
        return super.render(g);
    }

    private void line(String a,String b) {
        panelComponent.getChildren().add(LineComponent.builder().left(a+":").right(b).build());
    }
}

