package net.runelite.client.plugins.microbot.flippilot.data;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import javax.inject.Singleton;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads OSRS Wiki item mapping (id, name, members, limit).
 * Cache in memory; refreshes at most once per 24 hours.
 */
@Singleton
public class ItemRepository
{
    private static final String MAPPING_URL = "https://prices.runescape.wiki/api/v1/osrs/mapping";

    private final AtomicReference<List<ItemDef>> cache = new AtomicReference<>(Collections.emptyList());
    private volatile long lastLoadMs = 0;

    public List<ItemDef> getAllItems()
    {
        return cache.get();
    }

    public synchronized void ensureLoaded()
    {
        if (!cache.get().isEmpty() && (System.currentTimeMillis() - lastLoadMs) < 24L * 60L * 60L * 1000L)
        {
            return;
        }

        List<ItemDef> items = new ArrayList<>(50_000);
        try
        {
            HttpURLConnection conn = (HttpURLConnection) new URL(MAPPING_URL).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(20_000);
            conn.setRequestProperty("User-Agent", "FlipPilot/1.0 (RuneLite/Microbot)");

            if (conn.getResponseCode() != 200)
            {
                throw new IOException("Mapping HTTP " + conn.getResponseCode());
            }

            try (InputStream is = conn.getInputStream();
                 InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                 JsonReader reader = new JsonReader(isr))
            {
                JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
                for (JsonElement el : arr)
                {
                    JsonObject o = el.getAsJsonObject();
                    int id = o.get("id").getAsInt();
                    String name = o.get("name").getAsString();
                    boolean members = o.has("members") && !o.get("members").isJsonNull() && o.get("members").getAsBoolean();

                    int limit = 0;
                    if (o.has("limit") && !o.get("limit").isJsonNull())
                    {
                        try { limit = o.get("limit").getAsInt(); } catch (Exception ignored) {}
                    }

                    if (id > 0 && name != null && !name.trim().isEmpty())
                    {
                        items.add(new ItemDef(id, name, members, limit));
                    }
                }
            }

            cache.set(Collections.unmodifiableList(items));
            lastLoadMs = System.currentTimeMillis();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public List<ItemDef> getUniverse(boolean isMembers)
    {
        List<ItemDef> all = getAllItems();
        if (isMembers) return all;

        List<ItemDef> f2p = new ArrayList<>(Math.max(1000, all.size() / 2));
        for (ItemDef d : all)
        {
            if (!d.members) f2p.add(d);
        }
        return f2p;
    }
}
