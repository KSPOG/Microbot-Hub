package net.runelite.client.plugins.microbot.creatorbridge;

import java.util.List;
import java.util.Map;

public final class CreatorBridgePayload
{
    private long pid;
    private String profileName;
    private String pluginVersion;

    private boolean loggedIn;
    private boolean shuttingDown;

    private String playerName;
    private String gameState;
    private int world;

    private String scriptName;
    private String currentTask;
    private String scriptStatus;

    private int x;
    private int y;
    private int plane;

    private int totalLevel;
    private Map<String, Integer> levels;
    private Map<String, Integer> xp;

    private int questCount;
    private int completedQuestCount;
    private Map<String, String> questStates;
    private List<TelemetryEvent> recentQuestEvents;

    private List<TelemetryEvent> recentItemEvents;
    private List<GroundItemInfo> nearbyGroundItems;

    private boolean stationaryTooLong;
    private int stationarySeconds;
    private int stationaryX;
    private int stationaryY;
    private int stationaryPlane;

    private long updatedAt;

    public long getPid() { return pid; }
    public void setPid(long pid) { this.pid = pid; }

    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = clean(profileName); }

    public String getPluginVersion() { return pluginVersion; }
    public void setPluginVersion(String pluginVersion) { this.pluginVersion = clean(pluginVersion); }

    public boolean isLoggedIn() { return loggedIn; }
    public void setLoggedIn(boolean loggedIn) { this.loggedIn = loggedIn; }

    public boolean isShuttingDown() { return shuttingDown; }
    public void setShuttingDown(boolean shuttingDown) { this.shuttingDown = shuttingDown; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = clean(playerName); }

    public String getGameState() { return gameState; }
    public void setGameState(String gameState) { this.gameState = clean(gameState); }

    public int getWorld() { return world; }
    public void setWorld(int world) { this.world = world; }

    public String getScriptName() { return scriptName; }
    public void setScriptName(String scriptName) { this.scriptName = clean(scriptName); }

    public String getCurrentTask() { return currentTask; }
    public void setCurrentTask(String currentTask) { this.currentTask = clean(currentTask); }

    public String getScriptStatus() { return scriptStatus; }
    public void setScriptStatus(String scriptStatus) { this.scriptStatus = clean(scriptStatus); }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getPlane() { return plane; }
    public void setPlane(int plane) { this.plane = plane; }

    public int getTotalLevel() { return totalLevel; }
    public void setTotalLevel(int totalLevel) { this.totalLevel = Math.max(0, totalLevel); }

    public Map<String, Integer> getLevels() { return levels; }
    public void setLevels(Map<String, Integer> levels) { this.levels = levels; }

    public Map<String, Integer> getXp() { return xp; }
    public void setXp(Map<String, Integer> xp) { this.xp = xp; }

    public int getQuestCount() { return questCount; }
    public void setQuestCount(int questCount) { this.questCount = Math.max(0, questCount); }

    public int getCompletedQuestCount() { return completedQuestCount; }
    public void setCompletedQuestCount(int completedQuestCount) { this.completedQuestCount = Math.max(0, completedQuestCount); }

    public Map<String, String> getQuestStates() { return questStates; }
    public void setQuestStates(Map<String, String> questStates) { this.questStates = questStates; }

    public List<TelemetryEvent> getRecentQuestEvents() { return recentQuestEvents; }
    public void setRecentQuestEvents(List<TelemetryEvent> recentQuestEvents) { this.recentQuestEvents = recentQuestEvents; }

    public List<TelemetryEvent> getRecentItemEvents() { return recentItemEvents; }
    public void setRecentItemEvents(List<TelemetryEvent> recentItemEvents) { this.recentItemEvents = recentItemEvents; }

    public List<GroundItemInfo> getNearbyGroundItems() { return nearbyGroundItems; }
    public void setNearbyGroundItems(List<GroundItemInfo> nearbyGroundItems) { this.nearbyGroundItems = nearbyGroundItems; }

    public boolean isStationaryTooLong() { return stationaryTooLong; }
    public void setStationaryTooLong(boolean stationaryTooLong) { this.stationaryTooLong = stationaryTooLong; }

    public int getStationarySeconds() { return stationarySeconds; }
    public void setStationarySeconds(int stationarySeconds) { this.stationarySeconds = Math.max(0, stationarySeconds); }

    public int getStationaryX() { return stationaryX; }
    public void setStationaryX(int stationaryX) { this.stationaryX = stationaryX; }

    public int getStationaryY() { return stationaryY; }
    public void setStationaryY(int stationaryY) { this.stationaryY = stationaryY; }

    public int getStationaryPlane() { return stationaryPlane; }
    public void setStationaryPlane(int stationaryPlane) { this.stationaryPlane = stationaryPlane; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    private static String clean(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replace('\u00A0', ' ').trim();
    }

    public static final class TelemetryEvent
    {
        private String type;
        private String name;
        private int id;
        private int quantity;
        private String details;
        private long timestamp;

        public TelemetryEvent() {}

        public TelemetryEvent(String type, String name, int id, int quantity, String details, long timestamp)
        {
            this.type = clean(type);
            this.name = clean(name);
            this.id = id;
            this.quantity = quantity;
            this.details = clean(details);
            this.timestamp = timestamp;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = clean(type); }
        public String getName() { return name; }
        public void setName(String name) { this.name = clean(name); }
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = clean(details); }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    public static final class GroundItemInfo
    {
        private String name;
        private int id;
        private int quantity;
        private int totalValue;
        private int totalGeValue;
        private boolean owned;
        private boolean lootable;
        private int x;
        private int y;
        private int plane;

        public String getName() { return name; }
        public void setName(String name) { this.name = clean(name); }
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public int getTotalValue() { return totalValue; }
        public void setTotalValue(int totalValue) { this.totalValue = totalValue; }
        public int getTotalGeValue() { return totalGeValue; }
        public void setTotalGeValue(int totalGeValue) { this.totalGeValue = totalGeValue; }
        public boolean isOwned() { return owned; }
        public void setOwned(boolean owned) { this.owned = owned; }
        public boolean isLootable() { return lootable; }
        public void setLootable(boolean lootable) { this.lootable = lootable; }
        public int getX() { return x; }
        public void setX(int x) { this.x = x; }
        public int getY() { return y; }
        public void setY(int y) { this.y = y; }
        public int getPlane() { return plane; }
        public void setPlane(int plane) { this.plane = plane; }
    }
}
