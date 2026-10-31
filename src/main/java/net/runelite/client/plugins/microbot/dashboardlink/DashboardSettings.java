package net.runelite.client.plugins.microbot.dashboardlink;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Range;

/** Only typed, non-sensitive values are exported. Text and custom controls stay local. */
final class DashboardSettings {
    private DashboardSettings() { }

    static boolean supported(Method method) {
        ConfigItem item = method.getAnnotation(ConfigItem.class);
        if (item == null || method.getParameterCount() != 0 || item.hidden() || item.secret()
            || !item.warning().isEmpty()) return false;
        String key = item.keyName().toLowerCase(Locale.ROOT);
        if (key.matches(".*(password|token|secret|credential|webhook|apikey|pin).*")) return false;
        Class<?> type = method.getReturnType();
        return type == boolean.class || type == int.class || type.isEnum();
    }

    static List<Map<String, Object>> describe(Config config) throws ReflectiveOperationException {
        List<Map<String, Object>> result = new ArrayList<>();
        if (config == null) return result;
        for (Method method : config.getClass().getInterfaces()[0].getMethods()) {
            if (!supported(method)) continue;
            Object value = method.invoke(config);
            if (value == null) continue;
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            Class<?> type = method.getReturnType();
            Map<String, Object> setting = new LinkedHashMap<>();
            setting.put("key", item.keyName());
            setting.put("name", item.name());
            setting.put("type", type.isEnum() ? "enum" : type.getSimpleName());
            setting.put("value", value instanceof Enum ? ((Enum<?>) value).name() : value.toString());
            if (type.isEnum()) {
                List<String> options = new ArrayList<>();
                for (Object option : type.getEnumConstants()) options.add(((Enum<?>) option).name());
                setting.put("options", options);
            }
            Range range = method.getAnnotation(Range.class);
            if (range != null) { setting.put("min", range.min()); setting.put("max", range.max()); }
            result.add(setting);
        }
        return result;
    }

    static void update(Config config, ConfigManager manager, String key, String value) {
        if (config == null || value.length() > 128) throw new IllegalArgumentException("Setting is not editable");
        Class<?> type = config.getClass().getInterfaces()[0];
        ConfigGroup group = type.getAnnotation(ConfigGroup.class);
        if (group == null) throw new IllegalArgumentException("Missing configuration group");
        Method method = Arrays.stream(type.getMethods()).filter(DashboardSettings::supported)
            .filter(m -> m.getAnnotation(ConfigItem.class).keyName().equals(key)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Setting is not editable"));
        validate(method, value);
        manager.setConfiguration(group.value(), key, value);
    }

    static void validate(Method method, String value) {
        if (!supported(method)) throw new IllegalArgumentException("Setting is not editable");
        Class<?> type = method.getReturnType();
        if (type == boolean.class && !"true".equals(value) && !"false".equals(value))
            throw new IllegalArgumentException("Invalid boolean");
        if (type == int.class) {
            int number = Integer.parseInt(value);
            Range range = method.getAnnotation(Range.class);
            if (range != null && (number < range.min() || number > range.max()))
                throw new IllegalArgumentException("Value is out of range");
        }
        if (type.isEnum() && Arrays.stream(type.getEnumConstants()).noneMatch(v -> ((Enum<?>) v).name().equals(value)))
            throw new IllegalArgumentException("Invalid enum option");
    }
}
