package probe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptResultStore;

@PluginDescriptor(name = "KSP PluginManager Probe", description = "Reports loaded KSP plugin instance", tags = {"debug", "ksp"}, enabledByDefault = false)
public class KspPluginManagerProbePlugin extends Plugin {
    private static final String CLASS_NAME = "probe.KspPluginManagerProbePlugin";

    @Override
    protected void startUp() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Plugin plugin : Microbot.getPluginManager().getPlugins()) {
            Class<?> clazz = plugin.getClass();
            if (!clazz.getName().toLowerCase().contains("kspaccountbuilder")) {
                continue;
            }
            Map<String, Object> match = new LinkedHashMap<>();
            match.put("className", clazz.getName());
            match.put("classLoader", String.valueOf(clazz.getClassLoader()));
            match.put("active", Microbot.getPluginManager().isActive(plugin));
            PluginDescriptor desc = clazz.getAnnotation(PluginDescriptor.class);
            match.put("descriptorVersion", desc != null ? desc.version() : null);
            try {
                Field version = clazz.getDeclaredField("VERSION");
                version.setAccessible(true);
                match.put("versionField", version.get(null));
            } catch (Exception e) {
                match.put("versionError", e.toString());
            }
            inspectScript(clazz.getClassLoader(), match);
            matches.add(match);
        }
        result.put("matches", matches);
        ScriptResultStore.submit(CLASS_NAME, result);
    }

    private void inspectScript(ClassLoader loader, Map<String, Object> match) {
        try {
            Class<?> script = Class.forName("net.runelite.client.plugins.microbot.kspaccountbuilder.KspAccountBuilderScript", false, loader);
            List<String> fields = new ArrayList<>();
            for (Field field : script.getDeclaredFields()) {
                if (field.getName().contains("CAMERA")) {
                    field.setAccessible(true);
                    Object value;
                    try {
                        value = field.get(null);
                    } catch (Exception ignored) {
                        value = "<unreadable>";
                    }
                    fields.add(field.getName() + "=" + value);
                }
            }
            match.put("scriptCameraFields", fields);
        } catch (Exception e) {
            match.put("scriptError", e.toString());
        }
    }
}
