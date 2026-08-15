package net.runelite.client.plugins.microbot.kspautorepair;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@PluginDescriptor(
        name = PluginConstants.KSP + "Auto Repair Agent",
        description = "Autonomously observes KSP intent, actions and game-state results and validates self-healing source repairs",
        tags = {"ksp", "agent", "repair", "debug", "autonomous", "development", "observer", "codex", "chatgpt"},
        version = KspAutoRepairPlugin.VERSION,
        cardUrl = "",
        iconUrl = "",
        minClientVersion = "2.6.19",
        enabledByDefault = false,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspAutoRepairPlugin extends Plugin
{
    public static final String VERSION = "0.2.3";
    private static final long CLIENT_CALL_TIMEOUT_SECONDS = 5L;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private PluginManager pluginManager;

    @Inject
    private KspAutoRepairConfig config;

    private KspAutoRepairCoordinator coordinator;

    @Provides
    KspAutoRepairConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspAutoRepairConfig.class);
    }

    @Override
    protected void startUp()
    {
        coordinator = new KspAutoRepairCoordinator(clientForCoordinator(), pluginManager, coordinatorConfig());
        coordinator.start();
    }

    @Override
    protected void shutDown()
    {
        if (coordinator != null)
        {
            coordinator.stop();
            coordinator = null;
        }
    }

    /**
     * The coordinator deliberately performs Git, Codex and validation work off the game thread.
     * Some evidence capture calls still touch RuneLite's Client API, which must run on the Client
     * thread. This proxy keeps ordinary event-driven calls direct, while synchronously marshalling
     * background Client API calls through ClientThread before returning their value to the worker.
     */
    private Client clientForCoordinator()
    {
        return (Client) Proxy.newProxyInstance(
                Client.class.getClassLoader(),
                new Class<?>[]{Client.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class)
                    {
                        return invokeClientMethod(method, args);
                    }

                    if ("isClientThread".equals(method.getName()) && method.getParameterCount() == 0)
                    {
                        return client.isClientThread();
                    }

                    if (client.isClientThread())
                    {
                        return invokeClientMethod(method, args);
                    }

                    AtomicReference<Object> result = new AtomicReference<>();
                    AtomicReference<Throwable> failure = new AtomicReference<>();
                    CountDownLatch completed = new CountDownLatch(1);

                    clientThread.invoke(() -> {
                        try
                        {
                            result.set(invokeClientMethod(method, args));
                        }
                        catch (Throwable t)
                        {
                            failure.set(t);
                        }
                        finally
                        {
                            completed.countDown();
                        }
                    });

                    boolean finished;
                    try
                    {
                        finished = completed.await(CLIENT_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    }
                    catch (InterruptedException interrupted)
                    {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting for RuneLite client-thread call: "
                                + method.getName(), interrupted);
                    }

                    if (!finished)
                    {
                        throw new IllegalStateException("Timed out waiting for RuneLite client-thread call: "
                                + method.getName());
                    }

                    Throwable thrown = failure.get();
                    if (thrown != null)
                    {
                        throw thrown;
                    }
                    return result.get();
                });
    }

    private Object invokeClientMethod(Method method, Object[] args) throws Throwable
    {
        try
        {
            return method.invoke(client, args);
        }
        catch (InvocationTargetException invocationFailure)
        {
            throw invocationFailure.getCause();
        }
    }

    /**
     * KspAutoRepairCoordinator already owns a hardened generic command backend. Keep that
     * coordinator stable and adapt the first-class Codex / ChatGPT selection onto the hardened
     * path internally. To the user Codex remains a native backend: no custom command is needed.
     */
    private KspAutoRepairConfig coordinatorConfig()
    {
        return (KspAutoRepairConfig) Proxy.newProxyInstance(
                KspAutoRepairConfig.class.getClassLoader(),
                new Class<?>[]{KspAutoRepairConfig.class},
                (proxy, method, args) -> {
                    KspAgentBackend selectedBackend = config.agentBackend();
                    if (selectedBackend == KspAgentBackend.CODEX_CHATGPT)
                    {
                        if ("agentBackend".equals(method.getName()))
                        {
                            return KspAgentBackend.CUSTOM;
                        }
                        if ("customAgentCommand".equals(method.getName()))
                        {
                            return buildCodexCommand();
                        }
                    }

                    try
                    {
                        return method.invoke(config, args);
                    }
                    catch (InvocationTargetException invocationFailure)
                    {
                        throw invocationFailure.getCause();
                    }
                });
    }

    private String buildCodexCommand()
    {
        String executable = clean(config.codexExecutable());
        if (executable.isEmpty())
        {
            executable = "codex";
        }

        String model = clean(config.codexModel());
        StringBuilder command = new StringBuilder();
        if (isWindows())
        {
            command.append(windowsToken(executable));
        }
        else
        {
            command.append(posixToken(executable));
        }

        // -a/--ask-for-approval is a top-level Codex option in current CLI builds, so it must
        // appear before the exec subcommand. Exec-specific options follow exec.
        command.append(" -a never exec")
                .append(" -C \"{repo}\"")
                .append(" --sandbox workspace-write")
                .append(" --skip-git-repo-check")
                .append(" --ephemeral")
                .append(" --color never");

        if (!model.isEmpty())
        {
            command.append(" -m ")
                    .append(isWindows() ? windowsToken(model) : posixToken(model));
        }

        // The coordinator replaces {prompt} with the generated incident prompt file. Redirecting
        // that file to stdin lets Codex consume the exact prompt via its documented '-' input mode.
        command.append(" - < \"{prompt}\"");
        return command.toString();
    }

    private static String windowsToken(String value)
    {
        String clean = clean(value).replace("\"", "");
        if (clean.matches("[A-Za-z0-9_./:\\\\-]+"))
        {
            return clean;
        }
        return "\"" + clean + "\"";
    }

    private static String posixToken(String value)
    {
        return "'" + clean(value).replace("'", "'\"'\"'") + "'";
    }

    private static boolean isWindows()
    {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String clean(String value)
    {
        return value == null ? "" : value.trim();
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onGameTick();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onGameStateChanged(event);
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onItemContainerChanged(event);
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onVarbitChanged(event);
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onWidgetLoaded(event);
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onWidgetClosed(event);
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onMenuOptionClicked(event);
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onNpcSpawned(event);
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onNpcDespawned(event);
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onGameObjectSpawned(event);
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onGameObjectDespawned(event);
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onAnimationChanged(event);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onChatMessage(event);
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        KspAutoRepairCoordinator current = coordinator;
        if (current != null) current.onStatChanged(event);
    }
}
