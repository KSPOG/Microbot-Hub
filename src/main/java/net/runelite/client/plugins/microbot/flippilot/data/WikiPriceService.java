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
public class WikiPriceService
{
    private static final String LATEST_URL = "https://prices.runescape.wiki/api/v1/osrs/latest";

    public Map<Integer, LivePrice> fetchLatest()
    {
        Map<Integer, LivePrice> out = new HashMap<>(20_000);
        try
        {
            HttpURLConnection conn = (HttpURLConnection) new URL(LATEST_URL).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(20_000);
            conn.setRequestProperty("User-Agent", "FlipPilot/1.0 (RuneLite/Microbot)");

            if (conn.getResponseCode() != 200)
            {
                throw new IOException("Latest HTTP " + conn.getResponseCode());
            }

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
                    catch (NumberFormatException nfe) { continue; }

                    JsonObject p = e.getValue().getAsJsonObject();
                    int high = p.has("high") && !p.get("high").isJsonNull() ? p.get("high").getAsInt() : 0;
                    int low  = p.has("low")  && !p.get("low").isJsonNull()  ? p.get("low").getAsInt()  : 0;

                    if (high > 0 && low > 0)
                    {
                        out.put(id, new LivePrice(high, low));
                    }
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
