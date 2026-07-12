package org.takesome.frozenlands.engine.core;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.lua.LuaProviderBridge;
import org.takesome.frozenlands.engine.core.console.CoreConsole;
import org.takesome.frozenlands.engine.modules.EngineModule;
import org.takesome.frozenlands.engine.modules.ModuleCommand;
import org.takesome.frozenlands.engine.operations.EngineOperationCoordinator;
import org.takesome.frozenlands.engine.runtime.RuntimeMaps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CoreModule implements EngineModule {
    public static final String ID = "engine.core";

    private final EngineContext context;
    private final ScriptRuntime scripts;
    private final CoreConsole console;
    private final Map<String, ModuleCommand> commands = new LinkedHashMap<>();

    public CoreModule(EngineContext context) {
        this.context = context;
        this.scripts = new ScriptRuntime(context);
        this.console = new CoreConsole(context);
        registerCommands();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Core Lua bridge, scripting support and console command routing";
    }

    @Override
    public Map<String, ModuleCommand> commands() {
        return Collections.unmodifiableMap(commands);
    }

    public List<Map<String, Object>> runConfiguredAutoRunScripts() {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> response : scripts.runAutoRunScripts()) {
            publishScriptOutcome(response, true);
            results.add(response);
        }
        return results;
    }

    private void registerCommands() {
        commands.put("status", ModuleCommand.of("status", "Return core runtime status", args -> status()));
        commands.put("manifest", ModuleCommand.of("manifest", "Return full Lua runtime manifest", args -> bridge().exportRuntimeManifest()));
        commands.put("modules", ModuleCommand.of("modules", "List registered module ids", args -> RuntimeMaps.result("modules", moduleIds())));
        commands.put("providers", ModuleCommand.of("providers", "List registered provider ids", args -> RuntimeMaps.result("providers", providerIds())));
        commands.put("call.module", ModuleCommand.of("call.module", "Call another module command", this::callModule));
        commands.put("call.provider", ModuleCommand.of("call.provider", "Call a provider command", this::callProvider));
        commands.put("event.publish", ModuleCommand.of("event.publish", "Publish a module event", this::publishEvent));
        commands.put("events.status", ModuleCommand.of("events.status", "Return event bus diagnostics", this::eventsStatus));
        commands.put("events.topics", ModuleCommand.of("events.topics", "Return known module/provider event topics", this::eventsTopics));
        commands.put("events.latest", ModuleCommand.of("events.latest", "Return latest events by topic", this::eventsLatest));
        commands.put("events.recent", ModuleCommand.of("events.recent", "Return recent Java-side module/provider events", this::eventsRecent));
        commands.put("events.snapshot", ModuleCommand.of("events.snapshot", "Snapshot Java-side module/provider events", args -> RuntimeMaps.result("events", bridge().snapshotJavaEvents())));
        commands.put("events.drain", ModuleCommand.of("events.drain", "Drain Java-side module/provider events", args -> RuntimeMaps.result("events", bridge().drainJavaEvents())));
        commands.put("script.manifest", ModuleCommand.of("script.manifest", "Return script runtime manifest", args -> scripts.manifest()));
        commands.put("script.list", ModuleCommand.of("script.list", "List indexed Lua scripts", args -> scripts.list()));
        commands.put("script.read", ModuleCommand.of("script.read", "Read one indexed Lua script", scripts::read));
        commands.put("script.run", ModuleCommand.of("script.run", "Execute one indexed Lua script through the core bridge", this::runScript));
        commands.put("script.autorun", ModuleCommand.of("script.autorun", "Execute configured Lua autorun scripts", args -> RuntimeMaps.result("results", runConfiguredAutoRunScripts())));
        commands.put("console.execute", ModuleCommand.of("console.execute", "Execute a slash-prefixed console command line", this::executeConsole));
        commands.put("console.help", ModuleCommand.of("console.help", "Return core-owned console help", this::consoleHelp));
        commands.put("console.version", ModuleCommand.of("console.version", "Return core-owned console version", args -> console.version()));
        commands.put("console.commandsList", ModuleCommand.of("console.commandsList", "Return base and dynamic console commands", args -> console.commandsList()));
        commands.put("console.complete", ModuleCommand.of("console.complete", "Autocomplete console command names", this::consoleComplete));
        commands.put("task.pool.status", ModuleCommand.of("task.pool.status", "Return engine task pool status", args -> context.getTaskPool().status()));
        commands.put("task.list", ModuleCommand.of("task.list", "List registered engine tasks", args -> RuntimeMaps.result("tasks", context.getTaskPool().snapshot())));
        commands.put("task.get", ModuleCommand.of("task.get", "Return one engine task snapshot", this::taskGet));
        commands.put("task.cancel", ModuleCommand.of("task.cancel", "Cancel one engine task", this::taskCancel));
        commands.put("service.pool.status", ModuleCommand.of("service.pool.status", "Return engine service pool status", args -> context.getServicePool().status()));
        commands.put("service.list", ModuleCommand.of("service.list", "List registered engine services", args -> RuntimeMaps.result("services", context.getServicePool().snapshot())));
        new CoreOperationCommands(context).register(commands);
        new CoreGameRuntimeCommands(context).register(commands);
        new CoreUiScreenCommands(context).register(commands);
    }

    private Map<String, Object> status() {
        Map<String, Object> status = RuntimeMaps.result("module", ID);
        status.put("modules", moduleIds());
        status.put("providers", providerIds());
        status.put("scripting", scripts.manifest());
        status.put("services", context.getServicePool().status());
        status.put("operations", context.requireService(EngineOperationCoordinator.class).status());
        return status;
    }

    private Map<String, Object> callModule(Map<String, Object> args) {
        return context.getModuleRegistry().call(RuntimeMaps.string(args, "module", ""), RuntimeMaps.string(args, "command", "status"), RuntimeMaps.map(args, "args"));
    }

    private Map<String, Object> callProvider(Map<String, Object> args) {
        return context.getProviderRegistry().call(RuntimeMaps.string(args, "provider", ""), RuntimeMaps.string(args, "command", "status"), RuntimeMaps.map(args, "args"));
    }

    private Map<String, Object> publishEvent(Map<String, Object> args) {
        String topic = RuntimeMaps.string(args, "topic", "core.event");
        Map<String, Object> payload = RuntimeMaps.map(args, "payload");
        String emitter = RuntimeMaps.string(args, "emitter", "engine.core");
        Map<String, Object> metadata = RuntimeMaps.map(args, "metadata");
        boolean live = RuntimeMaps.bool(args, "live", false) || !RuntimeMaps.bool(args, "record", true);
        return live
                ? context.getModuleRegistry().publishLiveEvent(topic, payload, emitter, metadata)
                : context.getModuleRegistry().publishEvent(topic, payload, emitter, metadata);
    }

    private Map<String, Object> eventsStatus(Map<String, Object> args) {
        Map<String, Object> events = new LinkedHashMap<>();
        events.put("module", context.getModuleRegistry().getEventBus().status());
        events.put("provider", context.getProviderRegistry().getEventBus().status());
        return RuntimeMaps.result("events", events);
    }

    private Map<String, Object> eventsTopics(Map<String, Object> args) {
        Map<String, Object> topics = new LinkedHashMap<>();
        topics.put("module", context.getModuleRegistry().getEventBus().topics());
        topics.put("provider", context.getProviderRegistry().getEventBus().topics());
        return RuntimeMaps.result("topics", topics);
    }

    private Map<String, Object> eventsLatest(Map<String, Object> args) {
        String source = RuntimeMaps.string(args, "source", "all");
        String topic = RuntimeMaps.string(args, "topic", "");
        Map<String, Object> latest = new LinkedHashMap<>();
        if (includeModuleEvents(source)) {
            latest.put("module", topic.isBlank()
                    ? context.getModuleRegistry().getEventBus().latestByTopic()
                    : context.getModuleRegistry().getEventBus().latest(topic));
        }
        if (includeProviderEvents(source)) {
            latest.put("provider", topic.isBlank()
                    ? context.getProviderRegistry().getEventBus().latestByTopic()
                    : context.getProviderRegistry().getEventBus().latest(topic));
        }
        return RuntimeMaps.result("latest", latest);
    }

    private Map<String, Object> eventsRecent(Map<String, Object> args) {
        int limit = RuntimeMaps.integer(args, "limit", 50);
        String source = RuntimeMaps.string(args, "source", "all");
        List<Map<String, Object>> events = new ArrayList<>();
        if (includeModuleEvents(source)) {
            addTaggedEvents(events, "module", context.getModuleRegistry().getEventBus().recent(limit));
        }
        if (includeProviderEvents(source)) {
            addTaggedEvents(events, "provider", context.getProviderRegistry().getEventBus().recent(limit));
        }
        return RuntimeMaps.result("events", events);
    }

    private Map<String, Object> runScript(Map<String, Object> args) {
        String script = RuntimeMaps.string(args, "script", "startup.lua");
        context.getModuleRegistry().publishEvent("core.script.run.requested", Map.of("script", script));
        Map<String, Object> response = scripts.run(args);
        publishScriptOutcome(response, false);
        return response;
    }

    private void publishScriptOutcome(Map<String, Object> response, boolean autoRun) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("script", response.get("script"));
        payload.put("path", response.get("path"));
        payload.put("ok", response.get("ok"));
        payload.put("executed", response.get("executed"));
        payload.put("autoRun", autoRun);
        if (response.containsKey("error")) {
            payload.put("error", response.get("error"));
        }
        String topic = Boolean.TRUE.equals(response.get("ok")) ? "core.script.executed" : "core.script.failed";
        context.getModuleRegistry().publishEvent(topic, payload);
    }

    private Map<String, Object> executeConsole(Map<String, Object> args) {
        return console.execute(RuntimeMaps.string(args, "line", ""));
    }

    private Map<String, Object> consoleHelp(Map<String, Object> args) {
        return console.help(RuntimeMaps.string(args, "command", ""));
    }

    private Map<String, Object> consoleComplete(Map<String, Object> args) {
        return console.complete(RuntimeMaps.string(args, "prefix", ""));
    }

    private Map<String, Object> taskGet(Map<String, Object> args) {
        String id = RuntimeMaps.string(args, "id", "");
        return context.getTaskPool()
                .find(id)
                .map(handle -> RuntimeMaps.result("task", handle.snapshot()))
                .orElseGet(() -> RuntimeMaps.error("Task not found: " + id));
    }

    private Map<String, Object> taskCancel(Map<String, Object> args) {
        String id = RuntimeMaps.string(args, "id", "");
        boolean interrupt = RuntimeMaps.bool(args, "interrupt", true);
        Map<String, Object> result = RuntimeMaps.result("cancelled", context.getTaskPool().cancel(id, interrupt));
        result.put("id", id);
        return result;
    }

    private LuaProviderBridge bridge() {
        return new LuaProviderBridge(context);
    }

    private List<String> moduleIds() {
        return List.copyOf(context.getModuleRegistry().snapshot().keySet());
    }

    private List<String> providerIds() {
        return List.copyOf(context.getProviderRegistry().snapshot().keySet());
    }

    private boolean includeModuleEvents(String source) {
        return source == null || source.isBlank() || "all".equalsIgnoreCase(source) || "module".equalsIgnoreCase(source);
    }

    private boolean includeProviderEvents(String source) {
        return source == null || source.isBlank() || "all".equalsIgnoreCase(source) || "provider".equalsIgnoreCase(source);
    }

    private void addTaggedEvents(List<Map<String, Object>> target, String source, List<Map<String, Object>> events) {
        for (Map<String, Object> event : events) {
            Map<String, Object> tagged = new LinkedHashMap<>(event);
            tagged.put("source", source);
            target.add(tagged);
        }
    }


}
