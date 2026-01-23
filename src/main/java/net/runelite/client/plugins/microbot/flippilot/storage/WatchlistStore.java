package net.runelite.client.plugins.microbot.flippilot.storage;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import javax.inject.Singleton;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Singleton
public class WatchlistStore
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Set<Integer>>() {}.getType();

    private final File file;
    private Set<Integer> watch = new HashSet<>();

    public WatchlistStore()
    {
        File dir = new File(System.getProperty("user.home"), ".flippilot");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "watchlist.json");
        load();
    }

    public synchronized boolean isWatched(int itemId)
    {
        return watch.contains(itemId);
    }

    public synchronized Set<Integer> all()
    {
        return Collections.unmodifiableSet(watch);
    }

    public synchronized void add(int itemId)
    {
        watch.add(itemId);
        save();
    }

    public synchronized void remove(int itemId)
    {
        watch.remove(itemId);
        save();
    }

    private synchronized void load()
    {
        if (!file.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))
        {
            Set<Integer> loaded = GSON.fromJson(r, TYPE);
            if (loaded != null) watch = loaded;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private synchronized void save()
    {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))
        {
            GSON.toJson(watch, TYPE, w);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
