package net.runelite.client.plugins.microbot.KSPAutoWoodcutter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;

import net.runelite.client.events.ConfigChanged;

import net.runelite.api.events.ConfigChanged;

import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.woodcutting.ForestryEventPlugin;
import net.runelite.client.plugins.microbot.woodcutting.Forestry.EggEvent;
import net.runelite.client.plugins.microbot.woodcutting.Forestry.EntlingsEvent;
import net.runelite.client.plugins.microbot.woodcutting.Forestry.FlowersEvent;
import net.runelite.client.plugins.microbot.woodcutting.Forestry.FoxEvent;
import net.runelite.client.plugins.microbot.woodcutting.Forestry.HivesEvent;
import net.runelite.client.plugins.microbot.woodcutting.Forestry.LeprechaunEvent;
import net.runelite.client.plugins.microbot.woodcutting.Forestry.RitualEvent;
import net.runelite.client.plugins.microbot.woodcutting.Forestry.RootEvent;
import net.runelite.client.plugins.microbot.woodcutting.Forestry.StrugglingSaplingEvent;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.AWTException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@PluginDescriptor(
        name = PluginConstants.KSP + "Auto Woodcutter",
        description = "Progressive woodcutting with banking or dropping",
        tags = {"woodcutting", "microbot", "ksp"},
        version = KSPAutoWoodcutterPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KSPAutoWoodcutterPlugin extends Plugin implements ForestryEventPlugin {

    public static final String version = "0.1.6";

    public static final String version = "0.1.5";


    @Inject
    private KSPAutoWoodcutterConfig config;

    @Provides
    KSPAutoWoodcutterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KSPAutoWoodcutterConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KSPAutoWoodcutterOverlay overlay;

    @Inject
    private KSPAutoWoodcutterScript script;

    @Inject
    public Rs2TileObjectCache rs2TileObjectCache;

    private EggEvent eggEvent;
    private EntlingsEvent entlingsEvent;
    private FlowersEvent flowersEvent;
    private FoxEvent foxEvent;
    private HivesEvent hivesEvent;
    private LeprechaunEvent leprechaunEvent;
    private RitualEvent ritualEvent;
    private RootEvent rootEvent;
    private StrugglingSaplingEvent saplingEvent;

    public final List<Rs2NpcModel> ritualCircles = new ArrayList<>();
    public ForestryEvents currentForestryEvent = ForestryEvents.NONE;
    public final GameObject[] saplingOrder = new GameObject[3];
    public final List<GameObject> saplingIngredients = new ArrayList<>(5);
    private final AtomicInteger completedForestryEvents = new AtomicInteger(0);


    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(overlay);
        if (config.enableForestry()) {
            addEvents();
        }
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        removeEvents();
        ritualCircles.clear();
        currentForestryEvent = ForestryEvents.NONE;
        completedForestryEvents.set(0);
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != net.runelite.api.ChatMessageType.SPAM
                && event.getType() != net.runelite.api.ChatMessageType.GAMEMESSAGE
                && event.getType() != net.runelite.api.ChatMessageType.MESBOX) {
            return;
        }

        final var msg = event.getMessage();
        if (msg.startsWith("The sapling seems to love")) {
            int ingredientNum = msg.contains("first") ? 1 : (msg.contains("second") ? 2 : (msg.contains("third") ? 3 : -1));
            if (ingredientNum == -1) {
                log.debug("unable to find ingredient index from message: {}", msg);
                return;
            }

            GameObject ingredientObj = this.saplingIngredients.stream()
                    .filter(obj -> {
                        String compositionName = Rs2GameObject.getCompositionName(obj).orElse(null);
                        return compositionName != null && msg.contains(compositionName.toLowerCase());
                    })
                    .findAny()
                    .orElse(null);
            if (ingredientObj == null) {
                log.debug("unable to find ingredient from message: {}", msg);
                return;
            }

            this.saplingOrder[ingredientNum - 1] = ingredientObj;
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();
        int id = npc.getId();
        if (id >= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1 && id <= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4) {
            this.ritualCircles.add(new Rs2NpcModel(npc));
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        int id = npc.getId();
        if (id >= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1 && id <= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4) {
            this.ritualCircles.removeIf(n -> n.getIndex() == npc.getIndex());
        }
    }

    @Subscribe
    public void onGameObjectSpawned(final GameObjectSpawned event) {
        GameObject gameObject = event.getGameObject();
        switch (gameObject.getId()) {
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_1:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_2:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_3:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4A:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4B:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4C:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_5:
                this.saplingIngredients.add(gameObject);
                break;
        }
    }

    @Subscribe
    public void onGameObjectDespawned(final GameObjectDespawned event) {
        final GameObject object = event.getGameObject();

        switch (object.getId()) {
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_1:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_2:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_3:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4A:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4B:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4C:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_5:
                this.saplingIngredients.remove(object);
                break;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged ev) {
        if (ev.getGroup().equals(KSPAutoWoodcutterConfig.configGroup)) {
            if (ev.getKey().equals("enableForestry")) {
                if (config.enableForestry()) {
                    addEvents();
                } else {
                    removeEvents();
                }
            } else {
                var key = ev.getKey();
                var value = ev.getNewValue();
                if (value != null && value.equals("true")) {
                    addEvent(key);
                } else if (value != null && value.equals("false")) {
                    removeEvent(key);
                }
            }
        }
    }

    private void addEvents() {
        var eventManager = Microbot.getBlockingEventManager();

        if (config.eggEvent()) {
            eggEvent = new EggEvent(this);
            eventManager.add(eggEvent);
        }

        if (config.entlingsEvent()) {
            entlingsEvent = new EntlingsEvent(this);
            eventManager.add(entlingsEvent);
        }

        if (config.flowersEvent()) {
            flowersEvent = new FlowersEvent(this);
            eventManager.add(flowersEvent);
        }

        if (config.foxEvent()) {
            foxEvent = new FoxEvent(this);
            eventManager.add(foxEvent);
        }

        if (config.hivesEvent()) {
            hivesEvent = new HivesEvent(this);
            eventManager.add(hivesEvent);
        }

        if (config.leprechaunEvent()) {
            leprechaunEvent = new LeprechaunEvent(this);
            eventManager.add(leprechaunEvent);
        }

        if (config.ritualEvent()) {
            ritualEvent = new RitualEvent(this);
            eventManager.add(ritualEvent);
        }

        if (config.rootEvent()) {
            rootEvent = new RootEvent(this);
            eventManager.add(rootEvent);
        }

        if (config.saplingEvent()) {
            saplingEvent = new StrugglingSaplingEvent(this);
            eventManager.add(saplingEvent);
        }
    }

    private void removeEvents() {
        var eventManager = Microbot.getBlockingEventManager();

        if (eggEvent != null) {
            eventManager.remove(eggEvent);
            eggEvent = null;
        }

        if (entlingsEvent != null) {
            eventManager.remove(entlingsEvent);
            entlingsEvent = null;
        }

        if (flowersEvent != null) {
            eventManager.remove(flowersEvent);
            flowersEvent = null;
        }

        if (foxEvent != null) {
            eventManager.remove(foxEvent);
            foxEvent = null;
        }

        if (hivesEvent != null) {
            eventManager.remove(hivesEvent);
            hivesEvent = null;
        }

        if (leprechaunEvent != null) {
            eventManager.remove(leprechaunEvent);
            leprechaunEvent = null;
        }

        if (ritualEvent != null) {
            eventManager.remove(ritualEvent);
            ritualEvent = null;
        }

        if (rootEvent != null) {
            eventManager.remove(rootEvent);
            rootEvent = null;
        }

        if (saplingEvent != null) {
            eventManager.remove(saplingEvent);
            saplingEvent = null;
        }
    }

    private void addEvent(String key) {
        var eventManager = Microbot.getBlockingEventManager();
        switch (key) {
            case "eggEvent":
                eggEvent = new EggEvent(this);
                eventManager.add(eggEvent);
                break;
            case "entlingsEvent":
                entlingsEvent = new EntlingsEvent(this);
                eventManager.add(entlingsEvent);
                break;
            case "flowersEvent":
                flowersEvent = new FlowersEvent(this);
                eventManager.add(flowersEvent);
                break;
            case "foxEvent":
                foxEvent = new FoxEvent(this);
                eventManager.add(foxEvent);
                break;
            case "hivesEvent":
                hivesEvent = new HivesEvent(this);
                eventManager.add(hivesEvent);
                break;
            case "leprechaunEvent":
                leprechaunEvent = new LeprechaunEvent(this);
                eventManager.add(leprechaunEvent);
                break;
            case "ritualEvent":
                ritualEvent = new RitualEvent(this);
                eventManager.add(ritualEvent);
                break;
            case "rootEvent":
                rootEvent = new RootEvent(this);
                eventManager.add(rootEvent);
                break;
            case "saplingEvent":
                saplingEvent = new StrugglingSaplingEvent(this);
                eventManager.add(saplingEvent);
                break;
        }
    }

    private void removeEvent(String key) {
        var eventManager = Microbot.getBlockingEventManager();
        switch (key) {
            case "eggEvent":
                if (eggEvent != null) {
                    eventManager.remove(eggEvent);
                    eggEvent = null;
                }
                break;
            case "entlingsEvent":
                if (entlingsEvent != null) {
                    eventManager.remove(entlingsEvent);
                    entlingsEvent = null;
                }
                break;
            case "flowersEvent":
                if (flowersEvent != null) {
                    eventManager.remove(flowersEvent);
                    flowersEvent = null;
                }
                break;
            case "foxEvent":
                if (foxEvent != null) {
                    eventManager.remove(foxEvent);
                    foxEvent = null;
                }
                break;
            case "hivesEvent":
                if (hivesEvent != null) {
                    eventManager.remove(hivesEvent);
                    hivesEvent = null;
                }
                break;
            case "leprechaunEvent":
                if (leprechaunEvent != null) {
                    eventManager.remove(leprechaunEvent);
                    leprechaunEvent = null;
                }
                break;
            case "ritualEvent":
                if (ritualEvent != null) {
                    eventManager.remove(ritualEvent);
                    ritualEvent = null;
                }
                break;
            case "rootEvent":
                if (rootEvent != null) {
                    eventManager.remove(rootEvent);
                    rootEvent = null;
                }
                break;
            case "saplingEvent":
                if (saplingEvent != null) {
                    eventManager.remove(saplingEvent);
                    saplingEvent = null;
                }
                break;
        }
    }

    @Override
    public boolean isEnabled() {
        return Microbot.isPluginEnabled(this);
    }

    @Override
    public void setCurrentForestryEvent(ForestryEvents event) {
        currentForestryEvent = event;
    }

    @Override
    public ForestryEvents getCurrentForestryEvent() {
        return currentForestryEvent;
    }

    @Override
    public WoodcuttingTree getSelectedTree() {
        KSPAutoWoodcutterTree selected = resolveTargetTree();
        return mapToWoodcuttingTree(selected);
    }

    private KSPAutoWoodcutterTree resolveTargetTree() {
        if (config.mode().isProgressiveMode()) {
            int level = Microbot.getClient().getRealSkillLevel(net.runelite.api.Skill.WOODCUTTING);
            return KSPAutoWoodcutterTree.resolveForLevel(level);
        }
        return config.tree();
    }

    private WoodcuttingTree mapToWoodcuttingTree(KSPAutoWoodcutterTree tree) {
        if (tree == null) {
            return WoodcuttingTree.TREE;
        }
        for (WoodcuttingTree candidate : WoodcuttingTree.values()) {
            if (candidate.getLogID() == tree.getLogId()) {
                return candidate;
            }
        }
        return WoodcuttingTree.TREE;
    }

    @Override
    public boolean ensureInventorySpace(int requiredSlots) {
        int currentFreeSlots = 28 - Rs2Inventory.count();
        if (currentFreeSlots >= requiredSlots) {
            return true;
        }

        WoodcuttingTree tree = getSelectedTree();
        String logName = tree.getLog();
        int slotsNeeded = requiredSlots - currentFreeSlots;
        int logsToDelete = Math.min(slotsNeeded, Rs2Inventory.count(logName));

        if (logsToDelete <= 0) {
            log.warn("Cannot make inventory space - no logs to drop");
            return false;
        }

        log.info("Making space for forestry rewards: dropping {} logs of {}", logsToDelete, tree.getName());

        int actualDropped = Rs2Inventory.dropAmount(logName, logsToDelete, InteractOrder.EFFICIENT_ROW);

        boolean success = (28 - Rs2Inventory.count()) >= requiredSlots;
        if (!success) {
            log.warn("Failed to create enough inventory space: dropped {} logs but still need {} slots",
                    actualDropped, requiredSlots);
        }

        return success;
    }

    @Override
    public void incrementForestryEventCompleted() {
        completedForestryEvents.incrementAndGet();
    }

    @Override
    public List<Rs2NpcModel> getRitualCircles() {
        return ritualCircles;
    }

    @Override
    public GameObject[] getSaplingOrder() {
        return saplingOrder;
    }

    @Override
    public List<GameObject> getSaplingIngredients() {
        return saplingIngredients;
    }

    @Override
    public Rs2TileObjectCache getTileObjectCache() {
        return rs2TileObjectCache;
    }
}
