package net.runelite.client.plugins.microbot.flippilot.data;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import javax.inject.Singleton;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Singleton
public class HistoryStore
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<Integer, List<PricePoint>>>(){}.getType();

    private final File file;
    private Map<Integer, List<PricePoint>> data = new HashMap<>();

    public HistoryStore()
    {
        File dir = new File(System.getProperty("user.home"), ".flippilot");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "price_history.json");
        load();
    }

    public synchronized void append(int itemId, PricePoint p, int maxPoints)
    {
        List<PricePoint> list = data.computeIfAbsent(itemId, k -> new ArrayList<>());
        list.add(p);
        if (list.size() > maxPoints)
        {
            list.subList(0, list.size() - maxPoints).clear();
        }
    }

    public synchronized List<PricePoint> get(int itemId)
    {
        List<PricePoint> list = data.get(itemId);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    public synchronized void save()
    {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))
        {
            GSON.toJson(data, TYPE, w);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private synchronized void load()
    {
        if (!file.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))
        {
            Map<Integer, List<PricePoint>> loaded = GSON.fromJson(r, TYPE);
            if (loaded != null) data = loaded;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
