package probe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptResultStore;

@PluginDescriptor(name = "KSP Reflect Probe", description = "Reports loaded KSP class fields", tags = {"debug", "ksp"}, enabledByDefault = false)
public class KspReflectProbePlugin extends Plugin {
    private static final String CLASS_NAME = "probe.KspReflectProbePlugin";

    @Override
    protected void startUp() {
        Map<String, Object> result = new LinkedHashMap<>();
        inspectPlugin(result);
        inspectScript(result);
        ScriptResultStore.submit(CLASS_NAME, result);
    }

    private void inspectPlugin(Map<String, Object> result) {
        try {
            Class<?> plugin = Class.forName("net.runelite.client.plugins.microbot.kspaccountbuilder.KspAccountBuilderPlugin");
            Field version = plugin.getDeclaredField("VERSION");
            version.setAccessible(true);
            result.put("loadedVersion", version.get(null));
            result.put("pluginClassLoader", String.valueOf(plugin.getClassLoader()));
        } catch (Exception e) {
            result.put("pluginError", e.toString());
        }
    }

    private void inspectScript(Map<String, Object> result) {
        try {
            Class<?> script = Class.forName("net.runelite.client.plugins.microbot.kspaccountbuilder.KspAccountBuilderScript");
            List<String> cameraFields = new ArrayList<>();
            for (Field field : script.getDeclaredFields()) {
                if (field.getName().contains("CAMERA")) {
                    field.setAccessible(true);
                    Object value;
                    try {
                        value = field.get(null);
                    } catch (Exception ignored) {
                        value = "<unreadable>";
                    }
                    cameraFields.add(field.getName() + "=" + value);
                }
            }
            result.put("scriptCameraFields", cameraFields);
            result.put("scriptClassLoader", String.valueOf(script.getClassLoader()));
        } catch (Exception e) {
            result.put("scriptError", e.toString());
        }
    }
}
