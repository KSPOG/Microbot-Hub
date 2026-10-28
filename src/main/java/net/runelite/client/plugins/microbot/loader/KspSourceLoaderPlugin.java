package net.runelite.client.plugins.microbot.loader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.externalplugins.PluginJarClassLoader;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@PluginDescriptor(
        name = PluginConstants.KSP + "Source Loader",
        description = "Incrementally hot-loads changed KSP plugins from GitHub source",
        tags = {"ksp", "source", "loader", "hot reload"},
        authors = {"KSP"},
        version = "2.2.6",
        minClientVersion = "2.6.28",
        enabledByDefault = true,
        isExternal = true
)
public class KspSourceLoaderPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(KspSourceLoaderPlugin.class);

    private static final String OWNER = "KSPOG";
    private static final String REPO = "ksppluginsrelease";
    private static final String BRANCH = "main";
    private static final String API = "https://api.github.com/repos/" + OWNER + "/" + REPO;
    private static final String RAW = "https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/";
    private static final long REFRESH_MINUTES = 5L;
    private static final long MAX_FILE = 2L << 20;
    private static final long MAX_UNIT = 25L << 20;

    private static final Path ROOT =
            Path.of(System.getProperty("user.home"), ".runelite", "ksp-source-loader");
    private static final Path SOURCES = ROOT.resolve("sources");
    private static final Path CLASSES = ROOT.resolve("classes");

    private static final Pattern TYPE_AFTER_SLF4J =
            Pattern.compile("\\b(class|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");

    private static final String PLUGIN_CONSTANTS =
            "package net.runelite.client.plugins.microbot;\n"
                    + "public final class PluginConstants {\n"
                    + " private PluginConstants(){}\n"
                    + " public static final String DEFAULT_PREFIX=\"<html>[<font color=#b8f704>MB</font>] \";\n"
                    + " public static final String KSP=\"<html>[<font color=#b8f704>KSP</font>] \";\n"
                    + " public static final boolean DEFAULT_ENABLED=false, IS_EXTERNAL=true;\n"
                    + "}\n";

    /*
     * KspSupportConfig is intentionally compiled into each independently loaded
     * plugin unit. Config interfaces are resolved reflectively by Guice before
     * a plugin can be instantiated; relying on another unit's isolated
     * PluginJarClassLoader for this tiny mixin caused NoClassDefFoundError during
     * initial incremental loads. A local copy avoids that bootstrap dependency.
     *
     * KSP Support itself still compiles its canonical source from the repository.
     * The mixin only contains config metadata/constants, so it does not hold
     * mutable runtime state and is safe to define per isolated plugin unit.
     */
    private static final String KSP_SUPPORT_CONFIG =
            "package net.runelite.client.plugins.microbot.kspsupport;\n"
                    + "import net.runelite.client.config.ConfigButton;\n"
                    + "import net.runelite.client.config.ConfigItem;\n"
                    + "public interface KspSupportConfig {\n"
                    + " String SUPPORT_KEY=\"kspSupportDiscord\";\n"
                    + " String SUPPORT_URL=\"https://discord.gg/mTBVf5FKB2\";\n"
                    + " @ConfigItem(keyName=SUPPORT_KEY,name=\"Support\",description=\"Open the KSP Plugins support Discord.\",position=10000)\n"
                    + " default ConfigButton kspSupportDiscord(){ return new ConfigButton(); }\n"
                    + "}\n";

    @Inject private OkHttpClient http;
    @Inject private Gson gson;
    @Inject private ConfigManager configManager;
    @Inject private EventBus eventBus;

    /*
     * loaded: currently registered runtime units.
     * seen: last remote fingerprint attempted for each unit.
     *
     * "seen" is intentionally separate from the runtime fingerprint. If a bad
     * update fails, the old runtime is restored but the same broken remote
     * revision is not retried every five minutes. A later code change creates a
     * new fingerprint and is attempted normally.
     */
    private final Map<String, LoadedUnit> loaded = new LinkedHashMap<>();
    private final Map<String, String> seen = new HashMap<>();
    private final AtomicBoolean refreshing = new AtomicBoolean();

    private ScheduledExecutorService timer;
    private volatile String lastCommit;

    @Provides
    KspSourceLoaderConfig provideConfig()
    {
        return configManager.getConfig(KspSourceLoaderConfig.class);
    }

    @Override
    protected void startUp()
    {
        try
        {
            Files.createDirectories(SOURCES);
            Files.createDirectories(CLASSES);
        }
        catch (IOException e)
        {
            log.error("KSP Source Loader cache setup failed", e);
            return;
        }

        timer = Executors.newSingleThreadScheduledExecutor(r ->
        {
            Thread t = new Thread(r, "ksp-source-loader");
            t.setDaemon(true);
            return t;
        });
        timer.scheduleWithFixedDelay(this::refreshSafe, 0L, REFRESH_MINUTES, TimeUnit.MINUTES);
        log.info("KSP Source Loader v2.2.6 started | ABI-safe stop + guarded unit refresh");
    }

    @Override
    protected void shutDown()
    {
        if (timer != null)
        {
            timer.shutdownNow();
            timer = null;
        }

        for (LoadedUnit unit : new ArrayList<>(loaded.values()))
        {
            try
            {
                unload(unit);
                loaded.remove(unit.key);
            }
            catch (Throwable t)
            {
                // Keep the classloaders reachable if stop failed. Closing
                // them while the plugin remains active would orphan its code.
                log.error("Cannot unload KSP unit {} during loader shutdown; plugin retained",
                        unit.key, t);
            }
        }
        seen.clear();
        pluginsChanged();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (event != null
                && KspSourceLoaderConfig.GROUP.equals(event.getGroup())
                && KspSourceLoaderConfig.REFRESH_KEY.equals(event.getKey())
                && timer != null)
        {
            timer.execute(this::refreshSafe);
        }
    }

    private void refreshSafe()
    {
        if (!refreshing.compareAndSet(false, true))
        {
            log.debug("KSP refresh already running");
            return;
        }

        try
        {
            refresh();
        }
        catch (Throwable t)
        {
            log.error("KSP source refresh failed", t);
        }
        finally
        {
            refreshing.set(false);
        }
    }

    private void refresh() throws Exception
    {
        RemoteRepo remote = readRepo();

        Set<String> removed = new LinkedHashSet<>(seen.keySet());
        removed.removeAll(remote.units.keySet());

        List<RemoteUnit> changed = remote.units.values().stream()
                .filter(unit -> !unit.fingerprint.equals(seen.get(unit.key)))
                .sorted(Comparator
                        .comparingInt((RemoteUnit unit) -> unitPriority(unit.key))
                        .thenComparing(unit -> unit.key))
                .collect(Collectors.toList());

        if (changed.isEmpty() && removed.isEmpty())
        {
            lastCommit = remote.commit;
            log.debug("KSP Java sources unchanged | commit={}", shortHash(remote.commit));
            return;
        }

        log.info("KSP incremental refresh | changed={} removed={} commit={}",
                changed.stream().map(u -> u.key).collect(Collectors.toList()),
                removed,
                shortHash(remote.commit));

        boolean uiChanged = false;

        for (String key : removed)
        {
            LoadedUnit old = loaded.get(key);
            if (old != null)
            {
                log.info("Removing deleted KSP unit {}", key);
                try
                {
                    unload(old);
                    loaded.remove(key);
                    uiChanged = true;
                }
                catch (Throwable t)
                {
                    log.error("Deleted KSP unit {} could not stop; retaining its active runtime",
                            key, t);
                    continue;
                }
            }
            seen.remove(key);
        }

        for (RemoteUnit unit : changed)
        {
            /*
             * Mark the remote fingerprint as attempted before doing expensive
             * work. Failed source will not repeatedly stop a working plugin on
             * every timer tick.
             */
            seen.put(unit.key, unit.fingerprint);
            if (refreshUnit(unit)) uiChanged = true;
        }

        lastCommit = remote.commit;
        if (uiChanged) pluginsChanged();
        cleanup();
    }

    private boolean refreshUnit(RemoteUnit remote)
    {
        LoadedUnit old = loaded.get(remote.key);
        Set<String> active = old == null ? Collections.emptySet() : activeKeys(old);
        Path rollbackClasses = old == null ? null : old.classes;
        String rollbackFingerprint = old == null ? null : old.fingerprint;

        if (old != null)
        {
            log.info("Refreshing only KSP unit {} | active={}", remote.key, active);
            try
            {
                unload(old);
            }
            catch (Throwable stopFailure)
            {
                // Do not install another instance when the old one could
                // still be registered/active; retry on a later poll.
                log.error("KSP refresh aborted | unit={} old runtime retained; stop failed",
                        remote.key, stopFailure);
                seen.remove(remote.key, remote.fingerprint);
                return false;
            }
            loaded.remove(remote.key);
        }
        else
        {
            log.info("Loading new KSP unit {}", remote.key);
        }

        try
        {
            Path classes = build(remote);
            LoadedUnit next = load(remote.key, remote.fingerprint, classes, active, old == null);
            loaded.put(remote.key, next);

            log.info("KSP unit refreshed | unit={} plugins={} restarted={}",
                    remote.key, next.plugins.size(), activeKeys(next).size());
            return true;
        }
        catch (Throwable updateFailure)
        {
            log.error("KSP unit update rejected | unit={} fingerprint={}",
                    remote.key, shortHash(remote.fingerprint), updateFailure);

            if (rollbackClasses == null)
            {
                // Initial load may fail only because another source unit has not
                // been compiled yet. Let a later poll retry it after the other
                // units have been registered.
                seen.remove(remote.key, remote.fingerprint);
                return false;
            }

            try
            {
                LoadedUnit rollback =
                        load(remote.key, rollbackFingerprint, rollbackClasses, active, false);
                loaded.put(remote.key, rollback);
                log.info("KSP unit rollback complete | unit={} restored={}",
                        remote.key, activeKeys(rollback));
                return true;
            }
            catch (Throwable rollbackFailure)
            {
                log.error("KSP unit rollback FAILED | unit={}", remote.key, rollbackFailure);
                return true;
            }
        }
    }

    private Path build(RemoteUnit remote) throws Exception
    {
        String unitDir = cacheKey(remote.key);
        Path finalSource = SOURCES.resolve(unitDir).resolve(remote.fingerprint);
        Path classDir = CLASSES.resolve(unitDir).resolve(remote.fingerprint);

        if (Files.isDirectory(classDir)) return classDir;

        Path staging = SOURCES.resolve(unitDir).resolve(remote.fingerprint + ".tmp");
        delete(staging);
        Files.createDirectories(staging);

        long total = 0L;
        boolean hasPluginConstants = false;

        log.info("Downloading changed unit {} | javaFiles={}", remote.key, remote.files.size());

        try
        {
            for (RemoteFile file : remote.files)
            {
                if (file.size > MAX_FILE)
                    throw new IOException("Source exceeds 2 MiB: " + file.path);

                byte[] bytes = getBytes(RAW + remote.commit + "/" + file.path);
                if (bytes.length > MAX_FILE)
                    throw new IOException("Source exceeds 2 MiB: " + file.path);
                if (!file.sha.equals(gitBlobSha(bytes)))
                    throw new IOException("Git blob SHA mismatch: " + file.path);

                total += bytes.length;
                if (total > MAX_UNIT)
                    throw new IOException("Unit Java source exceeds 25 MiB: " + remote.key);

                Path out = safeResolve(staging, file.path);
                Files.createDirectories(out.getParent());

                if (productionJava(file.path))
                {
                    String source = normalize(file.path, new String(bytes, StandardCharsets.UTF_8));
                    hasPluginConstants |= file.path.endsWith("/PluginConstants.java")
                            || source.contains("class PluginConstants")
                            && source.contains("package net.runelite.client.plugins.microbot");
                    Files.writeString(out, source, StandardCharsets.UTF_8);
                }
                else
                {
                    // Binary resources must retain their original verified bytes.
                    Files.write(out, bytes);
                }
            }

            if (!hasPluginConstants)
            {
                Path pc = staging.resolve("net/runelite/client/plugins/microbot/PluginConstants.java");
                Files.createDirectories(pc.getParent());
                Files.writeString(pc, PLUGIN_CONSTANTS, StandardCharsets.UTF_8);
            }

            /*
             * Do not make ordinary plugin units depend on kspsupport being
             * instantiated first just to resolve their Config interface.
             * The canonical kspsupport unit keeps its repository source.
             */
            if (!"kspsupport".equalsIgnoreCase(remote.key))
            {
                Path supportConfig = staging.resolve(
                        "net/runelite/client/plugins/microbot/kspsupport/KspSupportConfig.java");
                Files.createDirectories(supportConfig.getParent());
                Files.writeString(supportConfig, KSP_SUPPORT_CONFIG, StandardCharsets.UTF_8);
            }

            compile(staging, classDir, remote.key);

            // JavaCompiler emits class files only. Copy verified binary resources
            // to the package paths expected by ClassLoader.getResourceAsStream.
            for (RemoteFile file : remote.files)
            {
                if (productionJava(file.path)) continue;

                String resourcePath = file.path.replace('\\', '/');
                int resourcesIndex = resourcePath.indexOf("/src/main/resources/");
                if (resourcesIndex >= 0)
                {
                    resourcePath = resourcePath.substring(resourcesIndex + "/src/main/resources/".length());
                }
                else if (resourcePath.startsWith("src/main/resources/"))
                {
                    resourcePath = resourcePath.substring("src/main/resources/".length());
                }
                else
                {
                    resourcePath = "net/runelite/client/plugins/microbot/" + resourcePath;
                }

                Path destination = safeResolve(classDir, resourcePath);
                Files.createDirectories(destination.getParent());
                Files.copy(safeResolve(staging, file.path), destination);
            }

            Files.createDirectories(finalSource.getParent());
            delete(finalSource);
            Files.move(staging, finalSource);
            return classDir;
        }
        finally
        {
            delete(staging);
        }
    }

    private void compile(Path sourceDir, Path classDir, String unitKey) throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
        {
            try
            {
                compiler = (JavaCompiler) Class.forName(
                                "org.eclipse.jdt.internal.compiler.tool.EclipseCompiler")
                        .getDeclaredConstructor().newInstance();
            }
            catch (Throwable t)
            {
                throw new IllegalStateException("No Java compiler available (JDK/ECJ required)", t);
            }
        }

        delete(classDir);
        Files.createDirectories(classDir);

        List<File> javaFiles;
        try (Stream<Path> stream = Files.walk(sourceDir))
        {
            javaFiles = stream.filter(p -> p.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }
        if (javaFiles.isEmpty())
            throw new IllegalStateException("No Java files in unit " + unitKey);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        try (StandardJavaFileManager fm =
                     compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8))
        {
            fm.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(classDir.toFile()));
            fm.setLocation(StandardLocation.CLASS_PATH, classPath(unitKey));

            long started = System.nanoTime();
            Boolean ok = compiler.getTask(
                            null,
                            fm,
                            diagnostics,
                            Arrays.asList("-source", "11", "-target", "11", "-proc:none", "-encoding", "UTF-8"),
                            null,
                            fm.getJavaFileObjectsFromFiles(javaFiles))
                    .call();

            if (!Boolean.TRUE.equals(ok))
            {
                String errors = diagnostics.getDiagnostics().stream()
                        .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                        .limit(50)
                        .map(d -> d.getSource() + ":" + d.getLineNumber() + ": "
                                + d.getMessage(Locale.ROOT))
                        .collect(Collectors.joining(" | "));
                delete(classDir);
                throw new IllegalStateException(
                        errors.isEmpty() ? "Compilation failed for " + unitKey : errors);
            }

            log.info("Compiled KSP unit {} | sources={} ms={}",
                    unitKey,
                    javaFiles.size(),
                    (System.nanoTime() - started) / 1_000_000L);
        }
    }

    private Collection<File> classPath(String compilingUnit)
    {
        LinkedHashSet<File> files = new LinkedHashSet<>();
        String cp = System.getProperty("java.class.path", "");

        if (!cp.isBlank())
        {
            for (String part : cp.split(Pattern.quote(File.pathSeparator)))
                if (!part.isBlank()) files.add(new File(part));
        }

        addCodeSource(files, Plugin.class);
        addCodeSource(files, getClass());

        /*
         * Allows one source unit to compile against already-loaded KSP utility
         * classes without recompiling/stopping those plugins.
         */
        loaded.forEach((key, unit) ->
        {
            if (!key.equals(compilingUnit) && unit.classes != null)
                files.add(unit.classes.toFile());
        });

        return files;
    }

    private static void addCodeSource(Set<File> files, Class<?> type)
    {
        try
        {
            if (type.getProtectionDomain().getCodeSource() != null)
                files.add(new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()));
        }
        catch (Exception ignored) { }
    }

    private LoadedUnit load(
            String unitKey,
            String fingerprint,
            Path classDir,
            Set<String> restart,
            boolean initial) throws Exception
    {
        URLClassLoader dependencies = dependencyLoader(unitKey);
        PluginJarClassLoader classLoader =
                new PluginJarClassLoader(classDir.toFile(), dependencies);

        List<Class<? extends Plugin>> types = pluginClasses(classLoader, classDir);
        LinkedHashMap<String, Plugin> plugins = new LinkedHashMap<>();

        try
        {
            for (Class<? extends Plugin> type : types)
            {
                Plugin plugin = instantiate(type);
                String key = type.getName();

                if (plugins.putIfAbsent(key, plugin) != null)
                    throw new IllegalStateException("Duplicate plugin class " + key);

                Microbot.getPluginManager().add(plugin);
            }

            for (Map.Entry<String, Plugin> entry : plugins.entrySet())
            {
                boolean shouldStart = initial
                        ? Microbot.getPluginManager().isPluginEnabled(entry.getValue())
                        : restart.contains(entry.getKey());

                if (shouldStart) start(entry.getValue());
            }

            restart.stream()
                    .filter(key -> !plugins.containsKey(key))
                    .forEach(key -> log.warn(
                            "Previously active plugin disappeared from unit {}: {}", unitKey, key));

            return new LoadedUnit(unitKey, fingerprint, classDir, dependencies, classLoader, plugins);
        }
        catch (Throwable t)
        {
            plugins.values().forEach(this::removeQuietly);
            close(classLoader);
            close(dependencies);
            throw t;
        }
    }

    private URLClassLoader dependencyLoader(String unitKey)
    {
        List<URL> urls = new ArrayList<>();
        List<ClassLoader> delegates = new ArrayList<>();

        loaded.forEach((key, unit) ->
        {
            if (key.equals(unitKey)) return;

            // Prefer the already-loaded unit's real classloader. This preserves
            // class identity for shared KSP types such as KspSupportConfig,
            // KspVerifiedBank and KspMuleWorkerService instead of defining a
            // second copy of those classes in every dependent plugin unit.
            if (unit.loader != null) delegates.add(unit.loader);

            // Keep the class directory as a final fallback for compiler/runtime
            // compatibility if a class has not yet been resolved by the unit loader.
            if (unit.classes != null)
            {
                try
                {
                    urls.add(unit.classes.toUri().toURL());
                }
                catch (Exception ignored) { }
            }
        });

        return new DependencyClassLoader(
                urls.toArray(new URL[0]),
                Plugin.class.getClassLoader(),
                delegates);
    }

    @SuppressWarnings("unchecked")
    private List<Class<? extends Plugin>> pluginClasses(ClassLoader loader, Path root) throws IOException
    {
        List<Class<? extends Plugin>> result = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(root))
        {
            for (Path path : stream
                    .filter(p -> p.toString().endsWith(".class"))
                    .filter(p -> !p.getFileName().toString().contains("$"))
                    .collect(Collectors.toList()))
            {
                String name = root.relativize(path)
                        .toString()
                        .replace(File.separatorChar, '.');
                name = name.substring(0, name.length() - 6);

                try
                {
                    Class<?> type = Class.forName(name, false, loader);
                    if (Plugin.class.isAssignableFrom(type)
                            && type.isAnnotationPresent(PluginDescriptor.class))
                    {
                        result.add((Class<? extends Plugin>) type);
                    }
                }
                catch (ClassNotFoundException | NoClassDefFoundError e)
                {
                    log.debug("Skipping class {}: {}", name, e.getMessage());
                }
            }
        }

        result.sort(Comparator.comparing(Class::getName));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Plugin instantiate(Class<? extends Plugin> type) throws Exception
    {
        Plugin plugin = type.getDeclaredConstructor().newInstance();
        Injector parent = Microbot.getInjector();

        com.google.inject.Module module = (Binder binder) ->
        {
            Class<Plugin> pluginType = (Class<Plugin>) type;
            binder.bind(pluginType).toInstance(plugin);
            binder.install(plugin);
        };

        plugin.setInjector(parent.createChildInjector(module));
        return plugin;
    }

    private void unload(LoadedUnit unit) throws Exception
    {
        List<Plugin> plugins = new ArrayList<>(unit.plugins.values());
        Collections.reverse(plugins);

        plugins.forEach(this::prepareUnload);

        // Unload is a prerequisite for hot replacement, not a best-effort
        // operation. An active old plugin must never be removed from the
        // manager or have its classloader closed after a failed stop.
        for (Plugin plugin : plugins)
        {
            if (Microbot.getPluginManager().isActive(plugin))
            {
                stop(plugin);
            }
        }

        for (Plugin plugin : plugins)
        {
            if (Microbot.getPluginManager().isActive(plugin))
            {
                throw new IllegalStateException(
                        "Refusing to unload active plugin " + name(plugin));
            }
        }

        for (Plugin plugin : plugins)
        {
            Microbot.getPluginManager().remove(plugin);
        }
        unit.plugins.clear();
        close(unit.loader);
        close(unit.dependencies);
    }

    private Set<String> activeKeys(LoadedUnit unit)
    {
        LinkedHashSet<String> active = new LinkedHashSet<>();
        if (unit == null) return active;

        unit.plugins.forEach((key, plugin) ->
        {
            try
            {
                if (Microbot.getPluginManager().isActive(plugin)) active.add(key);
            }
            catch (Throwable ignored) { }
        });
        return active;
    }

    private void start(Plugin plugin) throws Exception
    {
        edt(() ->
        {
            try
            {
                PluginManager manager = Microbot.getPluginManager();
                manager.setPluginEnabled(plugin, true);

                if (!manager.startPlugin(plugin))
                    throw new IllegalStateException("startPlugin returned false");

                log.info("Started {}", name(plugin));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });
    }

    private void stop(Plugin plugin) throws Exception
    {
        edt(() ->
        {
            PluginManager manager = Microbot.getPluginManager();
            if (!manager.isActive(plugin))
            {
                manager.setPluginEnabled(plugin, false);
                return;
            }

            try
            {
                // Compile-time PluginManager.stopPlugin descriptors differ
                // between client builds: (Plugin)void and (Plugin)boolean.
                // Reflection binds by name and parameter types at runtime,
                // avoiding a NoSuchMethodError caused only by return type.
                Method method = manager.getClass().getMethod("stopPlugin", Plugin.class);
                Object result;
                try
                {
                    result = method.invoke(manager, plugin);
                }
                catch (java.lang.reflect.InvocationTargetException e)
                {
                    throw new IllegalStateException(
                            "PluginManager.stopPlugin failed for " + name(plugin), e.getCause());
                }

                if (manager.isActive(plugin))
                {
                    throw new IllegalStateException(
                            "PluginManager.stopPlugin did not deactivate " + name(plugin)
                                    + " (result=" + result + ")");
                }

                // Disable only after stop is confirmed, so failed unloads
                // retain their prior enabled configuration and can be retried.
                manager.setPluginEnabled(plugin, false);
                log.info("Stopped {} | PluginManager.stopPlugin returned {}",
                        name(plugin), result);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(
                        "Unable to resolve runtime PluginManager.stopPlugin(Plugin) for "
                                + name(plugin), e);
            }
        });
    }

    private void prepareUnload(Plugin plugin)
    {
        try
        {
            Method method = plugin.getClass().getMethod("prepareHotUnload");
            method.invoke(plugin);
        }
        catch (NoSuchMethodException ignored) { }
        catch (Throwable t)
        {
            log.warn("prepareHotUnload failed for {}", name(plugin), t);
        }
    }

    private void removeQuietly(Plugin plugin)
    {
        try
        {
            if (Microbot.getPluginManager().isActive(plugin)) stop(plugin);
            if (Microbot.getPluginManager().isActive(plugin))
            {
                log.error("Skipping removal of still-active plugin {}", name(plugin));
                return;
            }
            Microbot.getPluginManager().remove(plugin);
        }
        catch (Throwable t)
        {
            log.error("Failed to clean up plugin {}; keeping runtime referenced", name(plugin), t);
        }
    }

    private void edt(Runnable action) throws Exception
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            action.run();
            return;
        }

        AtomicReference<Throwable> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                action.run();
            }
            catch (Throwable t)
            {
                error.set(t);
            }
        });

        if (error.get() != null)
        {
            if (error.get() instanceof Exception) throw (Exception) error.get();
            throw new RuntimeException(error.get());
        }
    }

    private void pluginsChanged()
    {
        try
        {
            if (eventBus != null) eventBus.post(new ExternalPluginsChanged());
        }
        catch (Throwable t)
        {
            log.warn("Failed to refresh plugin list UI", t);
        }
    }

    private RemoteRepo readRepo() throws Exception
    {
        String commit = json(API + "/commits/" + BRANCH).get("sha").getAsString();
        JsonObject tree = json(API + "/git/trees/" + commit + "?recursive=1");

        if (tree.has("truncated") && tree.get("truncated").getAsBoolean())
            throw new IOException("GitHub returned a truncated tree");

        List<RemoteFile> all = new ArrayList<>();
        Set<String> mainProjects = new HashSet<>();

        for (JsonElement element : tree.getAsJsonArray("tree"))
        {
            JsonObject obj = element.getAsJsonObject();
            String path = text(obj, "path");
            String sha = text(obj, "sha");
            String type = text(obj, "type");

            if (!"blob".equals(type) || sha == null || !productionSource(path)) continue;

            RemoteFile file = new RemoteFile(
                    path,
                    sha,
                    obj.has("size") ? obj.get("size").getAsLong() : -1L);

            all.add(file);
            if (underMainJava(path)) mainProjects.add(projectRoot(path));
        }

        List<RemoteFile> selected = all.stream()
                .filter(file -> !mainProjects.contains(projectRoot(file.path))
                        || underMainJava(file.path)
                        || file.path.startsWith("src/main/resources/")
                        || file.path.contains("/src/main/resources/"))
                .collect(Collectors.toList());

        Map<String, List<RemoteFile>> grouped =
                selected.stream().collect(Collectors.groupingBy(
                        file -> projectRoot(file.path),
                        LinkedHashMap::new,
                        Collectors.toList()));

        LinkedHashMap<String, RemoteUnit> units = new LinkedHashMap<>();

        for (Map.Entry<String, List<RemoteFile>> entry : grouped.entrySet())
        {
            List<RemoteFile> files = entry.getValue();
            files.sort(Comparator.comparing(file -> file.path));
            units.put(entry.getKey(), new RemoteUnit(
                    entry.getKey(),
                    commit,
                    fingerprint(files),
                    files));
        }

        return new RemoteRepo(commit, units);
    }

    private static String fingerprint(List<RemoteFile> files) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        for (RemoteFile file : files)
        {
            digest.update(file.path.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(file.sha.getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) '\n');
        }

        return hex(digest.digest());
    }

    private JsonObject json(String url) throws IOException
    {
        return gson.fromJson(new String(getBytes(url), StandardCharsets.UTF_8), JsonObject.class);
    }

    private byte[] getBytes(String url) throws IOException
    {
        Request.Builder request = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ksp-microbot-source-loader")
                .header("Cache-Control", "no-cache");

        String token = System.getenv("GITHUB_TOKEN");
        if (token != null && !token.isBlank())
            request.header("Authorization", "Bearer " + token.trim());

        try (Response response = http.newCall(request.build()).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
                throw new IOException("HTTP " + response.code() + " for " + url);

            return response.body().bytes();
        }
    }

    private static String normalize(String path, String source)
    {
        if (!source.contains("@Slf4j") && !source.contains("@lombok.extern.slf4j.Slf4j"))
        {
            if (source.contains("import lombok.") || source.contains("@lombok."))
                throw new IllegalArgumentException(path + ": unsupported Lombok annotation");
            return source;
        }

        String result = source.replaceAll(
                "(?m)^\\s*import\\s+lombok\\.extern\\.slf4j\\.Slf4j\\s*;\\s*(?:\\R|$)",
                "");

        int from = 0;
        while (true)
        {
            int shortAt = result.indexOf("@Slf4j", from);
            int fullAt = result.indexOf("@lombok.extern.slf4j.Slf4j", from);

            if (shortAt < 0 && fullAt < 0) break;

            boolean shortForm = shortAt >= 0 && (fullAt < 0 || shortAt < fullAt);
            int at = shortForm ? shortAt : fullAt;
            int length = shortForm ? 7 : 26;

            result = result.substring(0, at) + result.substring(at + length);

            Matcher matcher = TYPE_AFTER_SLF4J.matcher(result);
            matcher.region(at, result.length());

            if (!matcher.find())
                throw new IllegalArgumentException(path + ": cannot expand @Slf4j");

            int brace = result.indexOf('{', matcher.end());
            String logger =
                    "\n private static final org.slf4j.Logger log="
                            + "org.slf4j.LoggerFactory.getLogger("
                            + matcher.group(2)
                            + ".class);\n";

            result = result.substring(0, brace + 1)
                    + logger
                    + result.substring(brace + 1);
            from = brace + logger.length();
        }

        if (result.contains("import lombok.") || result.contains("@lombok."))
            throw new IllegalArgumentException(path + ": unsupported Lombok annotation");

        return result;
    }

    private static boolean productionJava(String path)
    {
        if (path == null || !path.toLowerCase(Locale.ROOT).endsWith(".java")) return false;
        String normalized = "/" + path.replace('\\', '/').toLowerCase(Locale.ROOT) + "/";
        return !normalized.contains("/src/test/")
                && !normalized.contains("/build/")
                && !normalized.contains("/target/");
    }

    private static boolean productionSource(String path)
    {
        if (productionJava(path)) return true;
        if (path == null) return false;
        String normalized = "/" + path.replace('\\', '/').toLowerCase(Locale.ROOT) + "/";
        return normalized.endsWith(".png/")
                && !normalized.contains("/src/test/")
                && !normalized.contains("/build/")
                && !normalized.contains("/target/");
    }

    private static boolean underMainJava(String path)
    {
        return path.startsWith("src/main/java/") || path.contains("/src/main/java/");
    }

    private static int unitPriority(String key)
    {
        /*
         * Shared source dependency order.
         *
         * kspmule imports kspbank.KspVerifiedBank, while many normal plugins
         * import both kspmule.* and kspsupport.*. These shared units therefore
         * have to be compiled/loaded before the plugin units that consume them.
         */
        if ("kspsupport".equalsIgnoreCase(key)) return 0;
        if ("kspbank".equalsIgnoreCase(key)) return 1;
        if ("kspmule".equalsIgnoreCase(key)) return 2;
        if ("util".equalsIgnoreCase(key) || "ksputil".equalsIgnoreCase(key)) return 3;
        return 10;
    }

    /**
     * One top-level repository project/folder is one independently reloadable
     * unit. Nested Gradle-style projects use the path before /src/main/java/.
     */
    private static String projectRoot(String path)
    {
        String normalized = path.replace('\\', '/');

        if (normalized.startsWith("src/main/java/")) return "__root__";

        int main = normalized.indexOf("/src/main/java/");
        if (main > 0) return normalized.substring(0, main);

        int slash = normalized.indexOf('/');
        return slash < 0 ? "__root__" : normalized.substring(0, slash);
    }

    private static Path safeResolve(Path root, String path) throws IOException
    {
        Path relative = Path.of(path).normalize();
        Path out = root.resolve(relative).normalize();

        if (relative.isAbsolute() || relative.startsWith("..") || !out.startsWith(root))
            throw new IOException("Unsafe source path: " + path);

        return out;
    }

    private static String gitBlobSha(byte[] bytes) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.US_ASCII));
        digest.update(bytes);
        return hex(digest.digest());
    }

    private static String text(JsonObject obj, String key)
    {
        return obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString()
                : null;
    }

    private static String cacheKey(String key)
    {
        String clean = key.replaceAll("[^A-Za-z0-9._-]", "_");
        return clean.isBlank() ? "__root__" : clean;
    }

    private static String hex(byte[] bytes)
    {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format("%02x", value & 0xff));
        return out.toString();
    }

    private static String shortHash(String value)
    {
        return value == null ? "null" : value.substring(0, Math.min(10, value.length()));
    }

    private static String name(Plugin plugin)
    {
        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        return descriptor == null ? plugin.getClass().getSimpleName() : descriptor.name();
    }

    private static void close(URLClassLoader loader)
    {
        try
        {
            if (loader != null) loader.close();
        }
        catch (IOException ignored) { }
    }

    private void cleanup()
    {
        for (String key : seen.keySet())
        {
            keepNewest(SOURCES.resolve(cacheKey(key)), 2);
            keepNewest(CLASSES.resolve(cacheKey(key)), 2);
        }
    }

    private void keepNewest(Path root, int keep)
    {
        if (!Files.isDirectory(root)) return;

        try (Stream<Path> stream = Files.list(root))
        {
            List<Path> dirs = stream
                    .filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().endsWith(".tmp"))
                    .sorted(Comparator.comparingLong(this::modified).reversed())
                    .collect(Collectors.toList());

            for (int i = keep; i < dirs.size(); i++) delete(dirs.get(i));
        }
        catch (IOException ignored) { }
    }

    private long modified(Path path)
    {
        try
        {
            return Files.getLastModifiedTime(path).toMillis();
        }
        catch (IOException ignored)
        {
            return 0L;
        }
    }

    private static void delete(Path root)
    {
        if (root == null || !Files.exists(root)) return;

        try
        {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>()
            {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException
                {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException error)
                        throws IOException
                {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException ignored) { }
    }

    /**
     * Runtime dependency bridge for independently reloadable source units.
     *
     * RuneLite/Microbot API classes are resolved from the application loader.
     * KSP shared classes are resolved from the already-loaded unit classloaders
     * so all dependents see the exact same Class object. The class-directory URLs
     * are retained only as a last fallback.
     */
    private static final class DependencyClassLoader extends URLClassLoader
    {
        private final List<ClassLoader> delegates;

        DependencyClassLoader(URL[] urls, ClassLoader parent, List<ClassLoader> delegates)
        {
            super(urls, parent);
            this.delegates = new ArrayList<>(delegates);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
        {
            /*
             * Shared KSP runtime types must come from the already-loaded owner
             * unit when possible so services/helpers retain one Class identity.
             * Config-only KspSupportConfig is normally resolved from the
             * dependent unit's local shim before this loader is reached.
             */
            if (name.startsWith("net.runelite.client.plugins.microbot.kspbank.")
                    || name.startsWith("net.runelite.client.plugins.microbot.kspmule.")
                    || name.startsWith("net.runelite.client.plugins.microbot.kspsupport."))
            {
                for (ClassLoader delegate : delegates)
                {
                    try
                    {
                        return delegate.loadClass(name);
                    }
                    catch (ClassNotFoundException ignored) { }
                }
            }

            // Core Java and RuneLite/Microbot classes come from the normal
            // application hierarchy / dependency class directories.
            try
            {
                return super.loadClass(name, resolve);
            }
            catch (ClassNotFoundException ignored) { }

            for (ClassLoader delegate : delegates)
            {
                try
                {
                    return delegate.loadClass(name);
                }
                catch (ClassNotFoundException ignored) { }
            }

            throw new ClassNotFoundException(name);
        }
    }

    private static final class RemoteFile
    {
        final String path;
        final String sha;
        final long size;

        RemoteFile(String path, String sha, long size)
        {
            this.path = path;
            this.sha = sha;
            this.size = size;
        }
    }

    private static final class RemoteUnit
    {
        final String key;
        final String commit;
        final String fingerprint;
        final List<RemoteFile> files;

        RemoteUnit(String key, String commit, String fingerprint, List<RemoteFile> files)
        {
            this.key = key;
            this.commit = commit;
            this.fingerprint = fingerprint;
            this.files = files;
        }
    }

    private static final class RemoteRepo
    {
        final String commit;
        final Map<String, RemoteUnit> units;

        RemoteRepo(String commit, Map<String, RemoteUnit> units)
        {
            this.commit = commit;
            this.units = units;
        }
    }

    private static final class LoadedUnit
    {
        final String key;
        final String fingerprint;
        final Path classes;
        final URLClassLoader dependencies;
        final PluginJarClassLoader loader;
        final Map<String, Plugin> plugins;

        LoadedUnit(
                String key,
                String fingerprint,
                Path classes,
                URLClassLoader dependencies,
                PluginJarClassLoader loader,
                Map<String, Plugin> plugins)
        {
            this.key = key;
            this.fingerprint = fingerprint;
            this.classes = classes;
            this.dependencies = dependencies;
            this.loader = loader;
            this.plugins = plugins;
        }
    }
}
