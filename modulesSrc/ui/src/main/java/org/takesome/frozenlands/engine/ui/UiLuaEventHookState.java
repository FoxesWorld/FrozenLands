package org.takesome.frozenlands.engine.ui;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.lua.LuaScriptExecutor;
import org.takesome.frozenlands.engine.resources.ModuleIndexCatalog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UiLuaEventHookState extends BaseAppState {
    private static final String LUA_EVENTS_ROOT = "src/main/resources/scripts/lua";
    private static final String UI_EVENT_TOPIC_PATTERN = "ui.*";
    private static final String UI_MENU_EVENT_SCRIPT = "events/ui_menu_event.lua";

    private final EngineContext context;
    private final LuaScriptExecutor executor;
    private final ModuleIndexCatalog moduleIndex = ModuleIndexCatalog.defaultCatalog();
    private final List<AutoCloseable> subscriptions = new ArrayList<>();

    public UiLuaEventHookState(EngineContext context) {
        this.context = context;
        this.executor = new LuaScriptExecutor(context);
    }

    @Override
    protected void initialize(Application application) {
        subscribeLuaHook(UI_EVENT_TOPIC_PATTERN, UI_MENU_EVENT_SCRIPT);
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
    }

    @Override protected void onEnable() { }
    @Override protected void onDisable() { }

    private void subscribeLuaHook(String topicPattern, String script) {
        subscriptions.add(context.getModuleRegistry().getEventBus().subscribe(
                topicPattern,
                event -> runLuaHook(topicPattern, script, event),
                false
        ));
        context.getLogger().info("UI Lua event hook installed: topicPattern={} script={}", topicPattern, script);
    }

    private void runLuaHook(String topicPattern, String script, Map<String, Object> event) {
        Path path = scriptPath(script);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("script", script);
        args.put("topicPattern", topicPattern);
        args.put("topic", event == null ? "" : event.getOrDefault("topic", ""));
        args.put("event", event == null ? Map.of() : event);
        args.put("payload", payload(event));

        Map<String, Object> response = executor.execute(script, path, readSource(path), args);
        if (Boolean.TRUE.equals(response.get("ok"))) {
            context.getLogger().debug("UI Lua event hook executed: topic={} script={} return={}", args.get("topic"), script, response.get("return"));
        } else {
            context.getLogger().error(
                    "UI Lua event hook failed: topic={} script={} error={} message={}",
                    args.get("topic"),
                    script,
                    response.get("error"),
                    response.get("message")
            );
        }
    }

    private Path scriptPath(String script) {
        return Path.of(moduleIndex.resolvePath(UiRuntimeModule.ID, LUA_EVENTS_ROOT + "/" + script));
    }

    private String readSource(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read UI Lua event hook: " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Map<String, Object> event) {
        Object payload = event == null ? null : event.get("payload");
        return payload instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
