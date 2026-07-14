package org.takesome.frozenlands.engine.lua;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import org.takesome.frozenlands.engine.EngineContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CoreLuaRuntimeState extends BaseAppState {
    public static final String SERVICE_ID = "core.lua.runtime";

    private final EngineContext context;
    private final LuaScriptExecutor executor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<AutoCloseable> subscriptions = new ArrayList<>();
    private final List<LuaModuleDescriptor> modules = new ArrayList<>();

    public CoreLuaRuntimeState(EngineContext context) {
        this.context = context;
        this.executor = new LuaScriptExecutor(context);
    }

    @Override
    protected void initialize(Application application) {
        modules.clear();
        modules.addAll(scanModules(runtimeRoot()));
        int initCount = runInitScripts();
        int handlerCount = registerHandlers();
        context.registerService(SERVICE_ID, CoreLuaRuntimeState.class, this);
        context.getLogger().info(
                "Core Lua runtime mounted root={} modules={} initScripts={} handlers={}",
                runtimeRoot(),
                modules.size(),
                initCount,
                handlerCount
        );
    }

    @Override
    protected void cleanup(Application application) {
        for (AutoCloseable subscription : subscriptions) {
            try {
                subscription.close();
            } catch (Exception ignored) {
            }
        }
        subscriptions.clear();
        modules.clear();
    }

    @Override protected void onEnable() { }
    @Override protected void onDisable() { }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("service", SERVICE_ID);
        result.put("modules", modules.size());
        result.put("subscriptions", subscriptions.size());
        List<Map<String, Object>> moduleSnapshots = new ArrayList<>();
        for (LuaModuleDescriptor module : modules) {
            moduleSnapshots.add(module.snapshot());
        }
        result.put("moduleDescriptors", moduleSnapshots);
        return result;
    }

    private int runInitScripts() {
        int executed = 0;
        for (LuaModuleDescriptor module : modules) {
            for (String script : module.initScripts()) {
                executed++;
                Path scriptPath = module.resolveScript(script);
                Map<String, Object> response = executor.execute(script, scriptPath, readSource(scriptPath), baseArgs(module, script, "init"));
                if (Boolean.TRUE.equals(response.get("ok"))) {
                    context.getLogger().info("Lua module initialized module={} script={}", module.id(), script);
                } else {
                    context.getLogger().error(
                            "Lua module initialization failed module={} script={} error={} message={}",
                            module.id(),
                            script,
                            response.get("error"),
                            response.get("message")
                    );
                }
            }
        }
        return executed;
    }

    private int registerHandlers() {
        int registered = 0;
        for (LuaModuleDescriptor module : modules) {
            for (LuaHandlerDescriptor handler : module.handlers()) {
                for (String topic : handler.topics()) {
                    if (topic.isBlank()) {
                        continue;
                    }
                    module.resolveScript(handler.script());
                    subscriptions.add(context.getModuleRegistry().getEventBus().subscribe(
                            topic,
                            event -> runHandler(module, handler, topic, event),
                            handler.replayLatest()
                    ));
                    registered++;
                    context.getLogger().info(
                            "Lua handler registered module={} topic={} script={} replayLatest={}",
                            module.id(),
                            topic,
                            handler.script(),
                            handler.replayLatest()
                    );
                }
            }
        }
        return registered;
    }

    private void runHandler(LuaModuleDescriptor module, LuaHandlerDescriptor handler, String topicPattern, Map<String, Object> event) {
        Path scriptPath = module.resolveScript(handler.script());
        Map<String, Object> args = baseArgs(module, handler.script(), "event");
        args.put("topicPattern", topicPattern);
        args.put("topic", event == null ? "" : event.getOrDefault("topic", ""));
        args.put("event", event == null ? Map.of() : event);
        args.put("payload", payload(event));
        args.put("handler", handler.snapshot());

        Map<String, Object> response = executor.execute(handler.script(), scriptPath, readSource(scriptPath), args);
        if (Boolean.TRUE.equals(response.get("ok"))) {
            context.getLogger().info(
                    "Lua handler executed topic={} module={} script={}",
                    args.get("topic"),
                    module.id(),
                    handler.script()
            );
            return;
        }
        context.getLogger().error(
                "Lua handler failed topic={} module={} script={} error={} message={}",
                args.get("topic"),
                module.id(),
                handler.script(),
                response.get("error"),
                response.get("message")
        );
    }

    private Map<String, Object> baseArgs(LuaModuleDescriptor module, String script, String phase) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("phase", phase);
        args.put("module", module.id());
        args.put("moduleRoot", module.root().toString());
        args.put("script", script);
        return args;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Map<String, Object> event) {
        Object payload = event == null ? null : event.get("payload");
        return payload instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String readSource(Path scriptPath) {
        try {
            return Files.readString(scriptPath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read Lua script: " + scriptPath, exception);
        }
    }

    private List<LuaModuleDescriptor> scanModules(Path root) {
        List<LuaModuleDescriptor> result = new ArrayList<>();
        loadModule(root.resolve("core"), result);
        loadModule(root.resolve("assets"), result);
        Path modulesRoot = root.resolve("modulesSrc");
        if (Files.isDirectory(modulesRoot)) {
            try (var stream = Files.list(modulesRoot)) {
                stream.filter(Files::isDirectory).forEach(path -> loadModule(path, result));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to scan Lua module indexes under " + modulesRoot, exception);
            }
        }
        return List.copyOf(result);
    }

    private void loadModule(Path moduleRoot, List<LuaModuleDescriptor> result) {
        Path indexPath = moduleRoot.resolve("module.index.json");
        if (!Files.isRegularFile(indexPath)) {
            return;
        }
        Map<String, Object> index = readJsonMap(indexPath);
        String id = string(index.get("id"));
        if (id.isBlank()) {
            return;
        }
        Map<String, Object> lua = map(index.get("lua"));
        if (lua.isEmpty()) {
            return;
        }
        List<String> initScripts = strings(lua.get("init"));
        List<LuaHandlerDescriptor> handlers = handlers(lua.get("handlers"));
        if (initScripts.isEmpty() && handlers.isEmpty()) {
            return;
        }
        LuaModuleDescriptor descriptor = new LuaModuleDescriptor(id, moduleRoot.toAbsolutePath().normalize(), initScripts, handlers);
        result.add(descriptor);
        context.getLogger().info(
                "Lua module descriptor discovered module={} initScripts={} handlers={}",
                id,
                initScripts.size(),
                handlers.size()
        );
    }

    private Map<String, Object> readJsonMap(Path indexPath) {
        try {
            return mapper.readValue(indexPath.toFile(), new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read module index: " + indexPath, exception);
        }
    }

    private List<LuaHandlerDescriptor> handlers(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<LuaHandlerDescriptor> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> handler = map(item);
            if (handler.isEmpty() || !booleanValue(handler.get("enabled"), true)) {
                continue;
            }
            String script = string(handler.get("script"));
            List<String> topics = strings(handler.get("topics"));
            String topic = string(handler.get("topic"));
            if (!topic.isBlank()) {
                topics = append(topics, topic);
            }
            if (script.isBlank() || topics.isEmpty()) {
                continue;
            }
            result.add(new LuaHandlerDescriptor(script, topics, booleanValue(handler.get("replayLatest"), false), Map.copyOf(handler)));
        }
        return List.copyOf(result);
    }

    private List<String> append(List<String> source, String item) {
        List<String> result = new ArrayList<>(source);
        result.add(item);
        return List.copyOf(result);
    }

    private List<String> strings(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof String text) {
            return text.isBlank() ? List.of() : List.of(text.trim());
        }
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = string(item);
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }

    private Path runtimeRoot() {
        String configuredRoot = System.getProperty("frozenlands.runtimeRoot");
        if (configuredRoot == null || configuredRoot.isBlank()) {
            configuredRoot = System.getenv("FROZENLANDS_RUNTIME_ROOT");
        }
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            return Path.of(configuredRoot).toAbsolutePath().normalize();
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, val) -> result.put(String.valueOf(key), val));
        return result;
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = string(value);
        if (text.isBlank()) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text) || "on".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text) || "off".equalsIgnoreCase(text)) {
            return false;
        }
        return fallback;
    }

    private record LuaModuleDescriptor(String id, Path root, List<String> initScripts, List<LuaHandlerDescriptor> handlers) {
        Path resolveScript(String script) {
            Path resolved = root.resolve(script.replace('\\', '/')).toAbsolutePath().normalize();
            if (!resolved.startsWith(root)) {
                throw new IllegalArgumentException("Lua script escapes module root: module=" + id + " script=" + script);
            }
            if (!Files.isRegularFile(resolved)) {
                throw new IllegalArgumentException("Lua script is missing: module=" + id + " script=" + resolved);
            }
            return resolved;
        }

        Map<String, Object> snapshot() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("root", root.toString());
            result.put("initScripts", initScripts);
            List<Map<String, Object>> handlerSnapshots = new ArrayList<>();
            for (LuaHandlerDescriptor handler : handlers) {
                handlerSnapshots.add(handler.snapshot());
            }
            result.put("handlers", handlerSnapshots);
            return Map.copyOf(result);
        }
    }

    private record LuaHandlerDescriptor(String script, List<String> topics, boolean replayLatest, Map<String, Object> declaration) {
        Map<String, Object> snapshot() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("script", script);
            result.put("topics", topics);
            result.put("replayLatest", replayLatest);
            result.put("declaration", declaration);
            return Map.copyOf(result);
        }
    }
}
