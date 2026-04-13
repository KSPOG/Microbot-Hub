package net.runelite.client.plugins.microbot.flippilot.data;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import javax.inject.Singleton;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Singleton
public class Wiki5mService
{
    private static final String URL_5M = "https://prices.runescape.wiki/api/v1/osrs/5m";

    public Map<Integer, Integer> fetchVolumes()
    {
        Map<Integer, Integer> out = new HashMap<>(20_000);
        try
        {
            HttpURLConnection conn = (HttpURLConnection) new URL(URL_5M).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(20_000);
            conn.setRequestProperty("User-Agent", "FlipPilot/1.0 (RuneLite/Microbot)");

            if (conn.getResponseCode() != 200)
                throw new IOException("5m HTTP " + conn.getResponseCode());

            try (InputStream is = conn.getInputStream();
                 InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                 JsonReader reader = new JsonReader(isr))
            {
                JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
                JsonObject data = root.getAsJsonObject("data");
                for (Map.Entry<String, JsonElement> e : data.entrySet())
                {
                    int id;
                    try { id = Integer.parseInt(e.getKey()); }
                    catch (Exception ignored) { continue; }

                    JsonObject o = e.getValue().getAsJsonObject();
                    int hv = o.has("highPriceVolume") && !o.get("highPriceVolume").isJsonNull()
                            ? o.get("highPriceVolume").getAsInt() : 0;
                    int lv = o.has("lowPriceVolume") && !o.get("lowPriceVolume").isJsonNull()
                            ? o.get("lowPriceVolume").getAsInt() : 0;

                    int vol = Math.max(0, hv) + Math.max(0, lv);
                    if (vol > 0) out.put(id, vol);
                }
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        return out;
    }
}
