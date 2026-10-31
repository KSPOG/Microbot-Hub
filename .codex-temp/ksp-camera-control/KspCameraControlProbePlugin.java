package probe;

import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientInt;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptResultStore;

@PluginDescriptor(
        name = "KSP Camera Control Probe",
        description = "Sets and reports KSP camera values",
        tags = {"debug", "ksp"},
        enabledByDefault = false
)
public class KspCameraControlProbePlugin extends Plugin {
    public static final String CLASS_NAME = "probe.KspCameraControlProbePlugin";
    private static final int TARGET_PITCH = 358;
    private static final int TARGET_YAW = 968;
    private static final int TARGET_ZOOM = 377;

    private volatile boolean running;

    @Override
    protected void startUp() {
        running = true;
        Thread worker = new Thread(this::runProbe, "ksp-camera-control-probe");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    protected void shutDown() {
        running = false;
    }

    private void runProbe() {
        submit("before-set");
        setCamera();
        submit("after-set-targets");
        sleep(250);
        submit("after-250ms");
        sleep(750);
        submit("after-1000ms");
        sleep(1000);
        submit("after-2000ms");
    }

    private void setCamera() {
        Microbot.getClientThread().invoke(() -> {
            Client client = Microbot.getClient();
            client.setCameraPitchRelaxerEnabled(true);
            client.setCameraPitchTarget(TARGET_PITCH);
            client.setCameraYawTarget(TARGET_YAW);
            client.setVarcIntValue(VarClientInt.CAMERA_ZOOM_FIXED_VIEWPORT, TARGET_ZOOM);
            client.setVarcIntValue(VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT, TARGET_ZOOM);
            client.runScript(ScriptID.CAMERA_DO_ZOOM, TARGET_ZOOM, TARGET_ZOOM);
            return null;
        });
    }

    private void submit(String phase) {
        if (!running) {
            return;
        }
        Microbot.getClientThread().invoke(() -> {
            Client client = Microbot.getClient();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("phase", phase);
            result.put("loggedIn", Microbot.isLoggedIn());
            result.put("pitch", client.getCameraPitch());
            result.put("pitchTarget", client.getCameraPitchTarget());
            result.put("yaw", client.getCameraYaw());
            result.put("yawTarget", client.getCameraYawTarget());
            result.put("scale", client.getScale());
            result.put("resized", client.isResized());
            result.put("zoomFixed", client.getVarcIntValue(VarClientInt.CAMERA_ZOOM_FIXED_VIEWPORT));
            result.put("zoomResizable", client.getVarcIntValue(VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT));
            result.put("targetPitch", TARGET_PITCH);
            result.put("targetYaw", TARGET_YAW);
            result.put("targetZoom", TARGET_ZOOM);
            ScriptResultStore.submit(CLASS_NAME, result);
            return null;
        });
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
