package net.runelite.client.plugins.microbot.kspruniteminerkiller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

/** Chooses only ordinary free-to-play worlds; hop attempts are throttled and outcome-checked. */
final class KspRuniteMinerKillerWorldHop {
    private static KspRuniteMinerKillerGearStore store;
    private static long noTargetSince, lastAttempt;
    private static int pendingWorld=-1;
    private static final Set<Integer> tried=new HashSet<>();
    private KspRuniteMinerKillerWorldHop(){}
    static synchronized void reset(KspRuniteMinerKillerGearStore settings){
        store=settings;noTargetSince=0L;lastAttempt=0L;pendingWorld=-1;tried.clear();
    }
    static synchronized void targetSeen(){noTargetSince=0L;}
    static synchronized String tick(long now){
        if(store==null||!store.hopEnabled())return null;
        if(noTargetSince==0L)noTargetSince=now;
        if(Microbot.isHopping())return "Waiting for world hop";
        final int current=Microbot.getClient().getWorld();
        if(pendingWorld>0){
            if(current==pendingWorld){pendingWorld=-1;noTargetSince=now;return "World hop completed";}
            if(now-lastAttempt<20_000L)return "Waiting for world transfer";
            pendingWorld=-1;
        }
        if(now-noTargetSince<store.hopDelay()*1000L)return null;
        // Use the selected interval for both successful hops and retries. A separate
        // fixed 25s cooldown previously made the 10s setting ineffective.
        final long cooldownMs = Math.max(KspRuniteMinerKillerGearStore.MIN_HOP_DELAY_SECONDS,
            store.hopDelay()) * 1000L;
        if(now-lastAttempt<cooldownMs)return "World hop cooling down";
        if(Rs2Player.isInCombat())return "Combat active - postponing hop";
        WorldResult result=Microbot.getWorldService()==null?null:Microbot.getWorldService().getWorlds();
        if(result==null||result.getWorlds()==null)return "World list unavailable - waiting";
        List<Integer> choices=new ArrayList<>();
        // Microbot 2.6.43 (09822b4): http World.getTypes() returns EnumSet<WorldType>.
        // Compile against the actual http API; a mock exposing Set<WorldType> silently
        // produces an incompatible JVM descriptor and crashes when world hopping.
        for(World world:result.getWorlds()){
            if(world==null||world.getId()==current||world.getPlayers()<0||world.getPlayers()>=1950)continue;
            if(world.getTypes()==null||world.getTypes().contains(WorldType.MEMBERS))continue;
            boolean blocked=false;
            for(WorldType type:world.getTypes()){
                // Only ordinary F2P worlds: avoid special worlds and access restrictions.
                String t=type.name();
                if(!"F2P".equals(t)&&!"FREE".equals(t)) {blocked=true;break;}
            }
            if(!blocked)choices.add(world.getId());
        }
        if(choices.isEmpty())return "No safe F2P worlds available";
        List<Integer> untried=new ArrayList<>();
        for(int world:choices)if(!tried.contains(world))untried.add(world);
        if(untried.isEmpty()){tried.clear();untried.addAll(choices);}
        Collections.shuffle(untried);
        int next=untried.get(0);
        tried.add(current);tried.add(next);lastAttempt=now;
        if(Microbot.hopToWorld(next)){
            pendingWorld=next;
            if(Microbot.getClient().getWorld()==next){pendingWorld=-1;noTargetSince=System.currentTimeMillis();}
            return "Hopping to F2P world "+next;
        }
        return "World "+next+" hop failed; retry later";
    }
}
