package org.takesome.frozenlands.engine.ui;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.modules.EngineModule;
import org.takesome.frozenlands.engine.modules.ModuleCommand;
import org.takesome.frozenlands.engine.ui.html.FrozenLandsHtmlUiRuntime;
import org.takesome.frozenlands.engine.ui.html.HtmlUiState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UiRuntimeModule implements EngineModule {
    public static final String ID = "frozenlands.ui";

    private final EngineContext context;
    private final Map<String, ModuleCommand> commands = new LinkedHashMap<>();

    public UiRuntimeModule(EngineContext context) {
        this.context = context;
        commands.put("status", ModuleCommand.of("status", "Return HTML UI runtime status.", args -> status()));
        commands.put("status.set", ModuleCommand.of("status.set", "Set visible HTML UI status text.", this::setStatus));
        commands.put("screen", ModuleCommand.of("screen", "Return active HTML UI screen.", args -> screen()));
        commands.put("screen.set", ModuleCommand.of("screen.set", "Set active HTML UI screen.", this::setScreen));
        commands.put("gameplay.enter", ModuleCommand.of("gameplay.enter", "Switch UI to gameplay/HUD and release gameplay input.", this::enterGameplay));
        commands.put("gameplay.gate", ModuleCommand.of("gameplay.gate", "Apply menu/gameplay input gate without hard-coded button routing.", this::gateGameplay));
        commands.put("saves.refresh", ModuleCommand.of("saves.refresh", "Refresh the HTML save list.", args -> refreshSaves()));
        commands.put("devtools.open", ModuleCommand.of("devtools.open", "Open HtmlDom DevTools.", args -> openDevTools()));
        commands.put("event.dispatch", ModuleCommand.of("event.dispatch", "Publish a declarative UI event through the module event bus.", this::dispatchEvent));
        commands.put("action.dispatch", ModuleCommand.of("action.dispatch", "Backward-compatible alias for event.dispatch.", this::dispatchEvent));
        commands.put("runtime.exit", ModuleCommand.of("runtime.exit", "Request runtime shutdown from Lua-driven UI flow.", this::requestExit));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "FrozenLands JME HtmlDom UI bridge";
    }

    @Override
    public Map<String, ModuleCommand> commands() {
        return Collections.unmodifiableMap(commands);
    }

    private Map<String, Object> status() {
        Map<String, Object> result = base(true);
        HtmlUiState state = state();
        result.put("stateAttached", state != null);
        if (state != null) {
            result.putAll(state.status());
        } else {
            result.put("available", false);
        }
        return result;
    }

    private Map<String, Object> screen() {
        Map<String, Object> result = base(runtime() != null);
        result.put("screen", runtime() == null ? "" : runtime().screen());
        return result;
    }

    private Map<String, Object> setScreen(Map<String, Object> args) {
        String screen = string(args, "screen", "hud");
        FrozenLandsHtmlUiRuntime runtime = runtime();
        if (runtime != null) {
            runtime.setScreen(screen);
        }
        Map<String, Object> result = base(runtime != null);
        result.put("screen", runtime == null ? "" : runtime.screen());
        return result;
    }

    private Map<String, Object> setStatus(Map<String, Object> args) {
        String message = string(args, "message", string(args, "status", ""));
        FrozenLandsHtmlUiRuntime runtime = runtime();
        if (runtime != null) {
            runtime.showStatus(message);
        }
        Map<String, Object> result = base(runtime != null);
        result.put("message", message);
        return result;
    }

    private Map<String, Object> enterGameplay(Map<String, Object> args) {
        String reason = string(args, "reason", "lua-ui-gameplay-enter");
        FrozenLandsHtmlUiRuntime runtime = runtime();
        if (runtime != null) {
            runtime.enterGameplay(reason);
        }
        Map<String, Object> result = base(runtime != null);
        result.put("screen", runtime == null ? "" : runtime.screen());
        result.put("reason", reason);
        return result;
    }

    private Map<String, Object> gateGameplay(Map<String, Object> args) {
        boolean menuVisible = booleanArg(args, "menuVisible", booleanArg(args, "visible", true));
        String reason = string(args, "reason", "lua-ui-gameplay-gate");
        FrozenLandsHtmlUiRuntime runtime = runtime();
        if (runtime != null) {
            runtime.applyGameplayGate(menuVisible, reason);
        }
        Map<String, Object> result = base(runtime != null);
        result.put("menuVisible", menuVisible);
        result.put("reason", reason);
        result.put("screen", runtime == null ? "" : runtime.screen());
        return result;
    }

    private Map<String, Object> refreshSaves() {
        FrozenLandsHtmlUiRuntime runtime = runtime();
        if (runtime != null) {
            runtime.refreshSaveSlots();
        }
        return base(runtime != null);
    }

    private Map<String, Object> openDevTools() {
        FrozenLandsHtmlUiRuntime runtime = runtime();
        if (runtime != null) {
            runtime.openDevTools();
        }
        return base(runtime != null);
    }

    private Map<String, Object> dispatchEvent(Map<String, Object> args) {
        FrozenLandsHtmlUiRuntime runtime = runtime();
        if (runtime == null) {
            return base(false);
        }
        Map<String, Object> payload = args == null ? new LinkedHashMap<>() : new LinkedHashMap<>(args);
        String topic = string(args, "event", string(args, "topic", string(args, "action", "")));
        payload.putIfAbsent("event", topic);
        payload.putIfAbsent("topic", topic);
        payload.putIfAbsent("source", "module-command");
        return runtime.dispatchUiEvent(topic, payload, "frozenlands.ui.event.dispatch");
    }

    private Map<String, Object> requestExit(Map<String, Object> args) {
        String reason = string(args, "reason", "lua-ui-exit-to-desktop");
        FrozenLandsHtmlUiRuntime runtime = runtime();
        if (runtime != null) {
            runtime.requestExitToDesktop(reason);
        }
        Map<String, Object> result = base(runtime != null);
        result.put("reason", reason);
        return result;
    }

    private Map<String, Object> base(boolean ok) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", ok);
        result.put("module", ID);
        return result;
    }

    private String string(Map<String, Object> args, String key, String fallback) {
        if (args == null || key == null || key.isBlank()) {
            return fallback;
        }
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private boolean booleanArg(Map<String, Object> args, String key, boolean fallback) {
        if (args == null || key == null || key.isBlank()) {
            return fallback;
        }
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
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

    private HtmlUiState state() {
        return context.appStateManager() == null ? null : context.appStateManager().getState(HtmlUiState.class);
    }

    private FrozenLandsHtmlUiRuntime runtime() {
        HtmlUiState state = state();
        return state == null ? null : state.runtime();
    }
}
