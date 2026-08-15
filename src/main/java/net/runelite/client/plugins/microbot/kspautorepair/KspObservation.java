package net.runelite.client.plugins.microbot.kspautorepair;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Optional structured tracing API for KSP plugins.
 *
 * Existing plugins do not have to call this API: KSP Auto Repair also infers actions from
 * RuneLite events. Explicit instrumentation simply raises diagnostic confidence because the
 * observer can distinguish intent from the resulting game-state changes.
 */
public final class KspObservation
{
    public enum Expectation
    {
        ANY_PROGRESS,
        POSITION_CHANGE,
        INVENTORY_CHANGE,
        WIDGET_CHANGE,
        DIALOGUE_CHANGE,
        ANIMATION_CHANGE,
        STATE_CHANGE,
        VARBIT_CHANGE,
        XP_CHANGE
    }

    interface Sink
    {
        void onState(String pluginHint, String state, Map<String, String> metadata);

        void onAction(String actionId,
                      String pluginHint,
                      String action,
                      String target,
                      Expectation expectation,
                      long timeoutMs,
                      Map<String, String> metadata);

        void onProgress(String pluginHint, String marker, Map<String, String> metadata);

        void onWaitReason(String pluginHint, String reason, long expectedUntilMs, Map<String, String> metadata);
    }

    private static volatile Sink sink;

    private KspObservation()
    {
    }

    static void install(Sink value)
    {
        sink = value;
    }

    static void uninstall(Sink value)
    {
        if (sink == value)
        {
            sink = null;
        }
    }

    public static void state(String state)
    {
        state(null, state, Collections.emptyMap());
    }

    public static void state(String pluginHint, String state)
    {
        state(pluginHint, state, Collections.emptyMap());
    }

    public static void state(String pluginHint, String state, Map<String, ?> metadata)
    {
        Sink current = sink;
        if (current != null)
        {
            current.onState(pluginHint, safe(state), stringify(metadata));
        }
    }

    public static String action(String action, String target, Expectation expectation, long timeoutMs)
    {
        return action(null, action, target, expectation, timeoutMs, Collections.emptyMap());
    }

    public static String action(String pluginHint,
                                String action,
                                String target,
                                Expectation expectation,
                                long timeoutMs,
                                Map<String, ?> metadata)
    {
        String id = UUID.randomUUID().toString();
        Sink current = sink;
        if (current != null)
        {
            current.onAction(id,
                    pluginHint,
                    safe(action),
                    safe(target),
                    expectation == null ? Expectation.ANY_PROGRESS : expectation,
                    Math.max(250L, timeoutMs),
                    stringify(metadata));
        }
        return id;
    }

    public static void progress(String marker)
    {
        progress(null, marker, Collections.emptyMap());
    }

    public static void progress(String pluginHint, String marker)
    {
        progress(pluginHint, marker, Collections.emptyMap());
    }

    public static void progress(String pluginHint, String marker, Map<String, ?> metadata)
    {
        Sink current = sink;
        if (current != null)
        {
            current.onProgress(pluginHint, safe(marker), stringify(metadata));
        }
    }

    public static void waitReason(String reason, long expectedDurationMs)
    {
        waitReason(null, reason, expectedDurationMs, Collections.emptyMap());
    }

    public static void waitReason(String pluginHint,
                                  String reason,
                                  long expectedDurationMs,
                                  Map<String, ?> metadata)
    {
        Sink current = sink;
        if (current != null)
        {
            long until = System.currentTimeMillis() + Math.max(0L, expectedDurationMs);
            current.onWaitReason(pluginHint, safe(reason), until, stringify(metadata));
        }
    }

    private static Map<String, String> stringify(Map<String, ?> metadata)
    {
        if (metadata == null || metadata.isEmpty())
        {
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : metadata.entrySet())
        {
            result.put(safe(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return result;
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }
}
