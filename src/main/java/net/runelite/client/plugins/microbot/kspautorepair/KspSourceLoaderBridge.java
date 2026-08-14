package net.runelite.client.plugins.microbot.kspautorepair;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Runtime compatibility bridge to the separately installed KSP Source Loader. */
final class KspSourceLoaderBridge
{
    private static final Logger log = LoggerFactory.getLogger(KspSourceLoaderBridge.class);
    private static final String LOADER_CLASS_SUFFIX = ".loader.KspSourceLoaderPlugin";
    private static final String COMPILER_CLASS = "net.runelite.client.plugins.microbot.loader.InMemoryJavaCompiler";
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;");

    interface Listener
    {
        void onLoaderEvent(String event, Map<String, String> details);
    }

    static final class PreflightResult
    {
        final boolean available;
        final boolean success;
        final String mechanism;
        final String details;

        private PreflightResult(boolean available, boolean success, String mechanism, String details)
        {
            this.available = available;
            this.success = success;
            this.mechanism = mechanism;
            this.details = details;
        }

        static PreflightResult unavailable(String details)
        {
            return new PreflightResult(false, false, "unavailable", details);
        }

        static PreflightResult result(boolean success, String mechanism, String details)
        {
            return new PreflightResult(true, success, mechanism, details);
        }
    }

    private final PluginManager pluginManager;
    private final Listener listener;
    private volatile Plugin loader;
    private volatile Object installedListenerProxy;
    private volatile String hookDescription = "log fallback";

    KspSourceLoaderBridge(PluginManager pluginManager, Listener listener)
    {
        this.pluginManager = pluginManager;
        this.listener = listener;
    }

    void attach()
    {
        loader = findLoader();
        if (loader == null)
        {
            hookDescription = "source loader not currently loaded; log fallback";
            return;
        }
        if (tryAttachLifecycleListener(loader))
        {
            return;
        }
        hookDescription = "loader discovered without listener API; log/revision fallback";
        emit("BRIDGE_FALLBACK", detail("loaderClass", loader.getClass().getName()));
    }

    String hookDescription()
    {
        return hookDescription;
    }

    Optional<String> currentRevision()
    {
        Plugin current = ensureLoader();
        if (current == null)
        {
            return Optional.empty();
        }
        String[] names = {"getCurrentRevision", "getLoadedRevision", "getLastRevision",
                "currentRevision", "loadedRevision", "lastRevision"};
        for (String name : names)
        {
            Method method = findNoArgMethod(current.getClass(), name);
            if (method == null)
            {
                continue;
            }
            try
            {
                method.setAccessible(true);
                String revision = normalizeRevision(method.invoke(current));
                if (!revision.isEmpty())
                {
                    return Optional.of(revision);
                }
            }
            catch (ReflectiveOperationException ignored)
            {
            }
        }
        for (Field field : allFields(current.getClass()))
        {
            String name = field.getName().toLowerCase(Locale.ROOT);
            if (!(name.contains("revision") || name.equals("sha") || name.contains("commit")))
            {
                continue;
            }
            try
            {
                field.setAccessible(true);
                String revision = normalizeRevision(field.get(current));
                if (!revision.isEmpty())
                {
                    return Optional.of(revision);
                }
            }
            catch (ReflectiveOperationException ignored)
            {
            }
        }
        return Optional.empty();
    }

    boolean requestRefresh()
    {
        Plugin current = ensureLoader();
        if (current == null)
        {
            return false;
        }
        String[] names = {"requestRefresh", "refreshNow", "manualRefresh", "refreshSources", "refresh"};
        for (String name : names)
        {
            Method method = findNoArgMethod(current.getClass(), name);
            if (method == null)
            {
                continue;
            }
            try
            {
                method.setAccessible(true);
                method.invoke(current);
                emit("REFRESH_REQUESTED", detail("method", method.getName()));
                return true;
            }
            catch (ReflectiveOperationException ignored)
            {
            }
        }
        return false;
    }

    PreflightResult preflight(Path repository)
    {
        try
        {
            Map<String, String> sources = readSources(repository);
            if (sources.isEmpty())
            {
                return PreflightResult.result(false, "source-loader-bridge", "No Java source files were found");
            }
            Plugin current = ensureLoader();
            if (current != null)
            {
                PreflightResult loaderResult = tryValidationMethods(current, sources, repository);
                if (loaderResult.available)
                {
                    return loaderResult;
                }
            }
            PreflightResult compilerResult = tryCompilerClass(sources, repository, current);
            if (compilerResult.available)
            {
                return compilerResult;
            }
        }
        catch (Throwable t)
        {
            return PreflightResult.result(false, "source-loader-bridge", t.toString());
        }
        return PreflightResult.unavailable("No compatible source-loader validation method was discoverable");
    }

    private PreflightResult tryValidationMethods(Object target, Map<String, String> sources, Path repository)
    {
        for (Method method : allMethods(target.getClass()))
        {
            String name = method.getName().toLowerCase(Locale.ROOT);
            boolean candidate = name.equals("validatesources") || name.equals("validatecandidate")
                    || name.equals("preflight") || name.equals("compileforvalidation")
                    || name.equals("validateandcompile");
            if (!candidate || method.getParameterCount() != 1)
            {
                continue;
            }
            Object argument = supportedArgument(method.getParameterTypes()[0], sources, repository);
            if (argument == Unsupported.INSTANCE)
            {
                continue;
            }
            try
            {
                method.setAccessible(true);
                return interpretResult(method.invoke(target, argument),
                        target.getClass().getSimpleName() + "." + method.getName());
            }
            catch (Throwable t)
            {
                Throwable cause = t.getCause() == null ? t : t.getCause();
                return PreflightResult.result(false,
                        target.getClass().getSimpleName() + "." + method.getName(), cause.toString());
            }
        }
        return PreflightResult.unavailable("loader has no compatible validation method");
    }

    private PreflightResult tryCompilerClass(Map<String, String> sources, Path repository, Plugin currentLoader)
    {
        List<ClassLoader> classLoaders = new ArrayList<>();
        if (currentLoader != null)
        {
            classLoaders.add(currentLoader.getClass().getClassLoader());
        }
        classLoaders.add(Thread.currentThread().getContextClassLoader());
        classLoaders.add(getClass().getClassLoader());
        Class<?> compilerClass = null;
        for (ClassLoader classLoader : classLoaders)
        {
            if (classLoader == null)
            {
                continue;
            }
            try
            {
                compilerClass = Class.forName(COMPILER_CLASS, false, classLoader);
                break;
            }
            catch (ClassNotFoundException ignored)
            {
            }
        }
        if (compilerClass == null)
        {
            return PreflightResult.unavailable("InMemoryJavaCompiler is not visible from the repair plugin classloader");
        }
        for (Method method : allMethods(compilerClass))
        {
            if (!method.getName().toLowerCase(Locale.ROOT).contains("compile") || method.getParameterCount() != 1)
            {
                continue;
            }
            Object argument = supportedArgument(method.getParameterTypes()[0], sources, repository);
            if (argument == Unsupported.INSTANCE)
            {
                continue;
            }
            try
            {
                Object target = null;
                if (!Modifier.isStatic(method.getModifiers()))
                {
                    Constructor<?> constructor = compilerClass.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    target = constructor.newInstance();
                }
                method.setAccessible(true);
                return interpretResult(method.invoke(target, argument),
                        compilerClass.getSimpleName() + "." + method.getName());
            }
            catch (NoSuchMethodException ignored)
            {
            }
            catch (Throwable t)
            {
                Throwable cause = t.getCause() == null ? t : t.getCause();
                return PreflightResult.result(false,
                        compilerClass.getSimpleName() + "." + method.getName(), cause.toString());
            }
        }
        return PreflightResult.unavailable("InMemoryJavaCompiler found but no safe one-argument compile method matched");
    }

    private Object supportedArgument(Class<?> type, Map<String, String> sources, Path repository)
    {
        if (Map.class.isAssignableFrom(type)) return sources;
        if (Path.class.isAssignableFrom(type)) return repository;
        if (String.class == type) return repository.toAbsolutePath().toString();
        return Unsupported.INSTANCE;
    }

    private PreflightResult interpretResult(Object result, String mechanism)
    {
        if (result instanceof Boolean)
        {
            return PreflightResult.result((Boolean) result, mechanism, String.valueOf(result));
        }
        if (result == null)
        {
            return PreflightResult.result(true, mechanism, "Validation method completed without exception");
        }
        if (result instanceof Map)
        {
            return PreflightResult.result(!((Map<?, ?>) result).isEmpty(), mechanism,
                    "compiled entries=" + ((Map<?, ?>) result).size());
        }
        if (result instanceof Collection)
        {
            return PreflightResult.result(!((Collection<?>) result).isEmpty(), mechanism,
                    "compiled entries=" + ((Collection<?>) result).size());
        }
        return PreflightResult.result(true, mechanism, String.valueOf(result));
    }

    private boolean tryAttachLifecycleListener(Plugin current)
    {
        String[] names = {"addListener", "registerListener", "setListener",
                "addLifecycleListener", "registerLifecycleListener"};
        for (String name : names)
        {
            for (Method method : allMethods(current.getClass()))
            {
                if (!method.getName().equals(name) || method.getParameterCount() != 1)
                {
                    continue;
                }
                Class<?> listenerType = method.getParameterTypes()[0];
                if (!listenerType.isInterface())
                {
                    continue;
                }
                try
                {
                    Object proxy = Proxy.newProxyInstance(listenerType.getClassLoader(),
                            new Class<?>[]{listenerType}, (object, callback, args) -> {
                                if (callback.getDeclaringClass() == Object.class)
                                {
                                    if ("toString".equals(callback.getName())) return "KspAutoRepairSourceLoaderListener";
                                    if ("hashCode".equals(callback.getName())) return System.identityHashCode(object);
                                    if ("equals".equals(callback.getName()))
                                    {
                                        return args != null && args.length == 1 && object == args[0];
                                    }
                                }
                                Map<String, String> details = new LinkedHashMap<>();
                                details.put("callback", callback.getName());
                                if (args != null)
                                {
                                    for (int i = 0; i < args.length; i++)
                                    {
                                        details.put("arg" + i, String.valueOf(args[i]));
                                    }
                                }
                                emit("LOADER_CALLBACK", details);
                                return defaultValue(callback.getReturnType());
                            });
                    method.setAccessible(true);
                    method.invoke(current, proxy);
                    installedListenerProxy = proxy;
                    hookDescription = "direct listener via " + current.getClass().getSimpleName()
                            + "." + method.getName();
                    emit("BRIDGE_ATTACHED", detail("mechanism", hookDescription));
                    return true;
                }
                catch (Throwable t)
                {
                    log.debug("KSP source-loader listener hook {} failed", method, t);
                }
            }
        }
        return false;
    }

    private Plugin ensureLoader()
    {
        Plugin current = loader;
        if (current != null && pluginManager.getPlugins().contains(current))
        {
            return current;
        }
        loader = findLoader();
        return loader;
    }

    private Plugin findLoader()
    {
        for (Plugin plugin : pluginManager.getPlugins())
        {
            if (plugin == null)
            {
                continue;
            }
            String className = plugin.getClass().getName();
            if (className.endsWith(LOADER_CLASS_SUFFIX) || className.endsWith(".KspSourceLoaderPlugin"))
            {
                return plugin;
            }
        }
        return null;
    }

    private Map<String, String> readSources(Path repository) throws Exception
    {
        try (Stream<Path> stream = Files.walk(repository))
        {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains(".git"))
                    .collect(Collectors.toList());
            Map<String, String> result = new LinkedHashMap<>();
            for (Path file : files)
            {
                String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                String fileName = file.getFileName().toString();
                String simpleName = fileName.substring(0, fileName.length() - ".java".length());
                Matcher matcher = PACKAGE_PATTERN.matcher(source);
                String sourceName = matcher.find() ? matcher.group(1) + "." + simpleName : simpleName;
                result.put(sourceName, source);
            }
            return result;
        }
    }

    private Method findNoArgMethod(Class<?> type, String name)
    {
        for (Method method : allMethods(type))
        {
            if (method.getName().equals(name) && method.getParameterCount() == 0)
            {
                return method;
            }
        }
        return null;
    }

    private List<Method> allMethods(Class<?> type)
    {
        List<Method> methods = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class)
        {
            for (Method method : current.getDeclaredMethods()) methods.add(method);
            current = current.getSuperclass();
        }
        return methods;
    }

    private List<Field> allFields(Class<?> type)
    {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class)
        {
            for (Field field : current.getDeclaredFields()) fields.add(field);
            current = current.getSuperclass();
        }
        return fields;
    }

    private String normalizeRevision(Object value)
    {
        if (value == null) return "";
        String text = String.valueOf(value).trim();
        return text.matches("[0-9a-fA-F]{7,40}") ? text.toLowerCase(Locale.ROOT) : "";
    }

    private Object defaultValue(Class<?> type)
    {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }

    private Map<String, String> detail(String key, String value)
    {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    private void emit(String event, Map<String, String> details)
    {
        if (listener != null)
        {
            listener.onLoaderEvent(event, details);
        }
    }

    private enum Unsupported
    {
        INSTANCE
    }
}
