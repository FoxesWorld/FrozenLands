package org.takesome.frozenlands.engine.ui;

import com.jme3.app.Application;
import com.jme3.audio.Listener;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.Kernel;
import org.takesome.frozenlands.engine.modules.EngineModule;
import org.takesome.frozenlands.engine.modules.ModuleCommand;
import org.takesome.frozenlands.engine.ui.html.FrozenLandsHtmlUiRuntime;
import org.takesome.frozenlands.engine.ui.html.HtmlUiState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class UiRuntimeModule implements EngineModule {
    public static final String ID = "frozenlands.ui";

    private final EngineContext context;
    private final Map<String, ModuleCommand> commands = new LinkedHashMap<>();
    private String selectedGraphicsPreset = "HIGH";
    private float selectedMouseSensitivity = 1f;
    private float selectedMasterVolume = 1f;

    public UiRuntimeModule(EngineContext context) {
        this.context = context;
        commands.put("status", ModuleCommand.of("status", "Return HTML UI runtime status.", args -> status()));
        commands.put("status.set", ModuleCommand.of("status.set", "Set visible HTML UI status text.", this::setStatus));
        commands.put("screen", ModuleCommand.of("screen", "Return active HTML UI screen.", args -> screen()));
        commands.put("screen.set", ModuleCommand.of("screen.set", "Set active HTML UI screen.", this::setScreen));
        commands.put("settings.open", ModuleCommand.of("settings.open", "Open settings and remember the originating menu.", this::openSettings));
        commands.put("settings.close", ModuleCommand.of("settings.close", "Close settings and return to the originating menu.", this::closeSettings));
        commands.put("settings.section", ModuleCommand.of("settings.section", "Show one settings category panel.", this::setSettingsSection));
        commands.put("settings.preferences", ModuleCommand.of("settings.preferences", "Return selected settings preferences.", args -> settingsPreferences()));
        commands.put("settings.graphics.select", ModuleCommand.of("settings.graphics.select", "Select and apply or defer a graphics preset.", this::selectGraphicsPreset));
        commands.put("settings.graphics.applyPending", ModuleCommand.of("settings.graphics.applyPending", "Apply the selected graphics preset when quality runtime is available.", args -> applySelectedGraphicsPreset()));
        commands.put("settings.controls.sensitivity.select", ModuleCommand.of("settings.controls.sensitivity.select", "Select and apply or defer mouse sensitivity.", this::selectMouseSensitivity));
        commands.put("settings.controls.applyPending", ModuleCommand.of("settings.controls.applyPending", "Apply selected control preferences when player runtime is available.", args -> applySelectedMouseSensitivity()));
        commands.put("audio.volume.status", ModuleCommand.of("audio.volume.status", "Return the active listener volume.", args -> audioVolumeStatus()));
        commands.put("audio.volume.set", ModuleCommand.of("audio.volume.set", "Set the active listener volume.", this::setAudioVolume));
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
        FrozenLandsHtmlUiRuntime runtime = runtime();
        Map<String, Object> result = base(runtime != null);
        result.put("screen", runtime == null ? "" : runtime.screen());
        result.put(
                "settingsReturnScreen",
                runtime == null ? "" : runtime.settingsReturnScreen()
        );
        result.put(
                "settingsSection",
                runtime == null ? "" : runtime.settingsSection()
        );
        Listener listener = listener();
        result.put("audioAvailable", listener != null);
        if (listener != null) {
            result.put("masterVolume", listener.getVolume());
        }
        result.put("selectedGraphicsPreset", selectedGraphicsPreset);
        result.put("selectedMouseSensitivity", selectedMouseSensitivity);
        result.put("selectedMasterVolume", selectedMasterVolume);
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

    private Map<String, Object> openSettings(Map<String, Object> args) {
        FrozenLandsHtmlUiRuntime runtime = runtime();
        String returnScreen = string(
                args,
                "returnScreen",
                runtime == null ? "main-menu" : runtime.screen()
        );
        String reason = string(
                args,
                "reason",
                "lua-ui-settings-open"
        );
        if (runtime != null) {
            synchronizeSettingsControls(runtime);
            runtime.openSettings(returnScreen, reason);
        }
        Map<String, Object> result = base(runtime != null);
        result.put("screen", runtime == null ? "" : runtime.screen());
        result.put(
                "returnScreen",
                runtime == null ? "" : runtime.settingsReturnScreen()
        );
        return result;
    }

    private Map<String, Object> closeSettings(Map<String, Object> args) {
        FrozenLandsHtmlUiRuntime runtime = runtime();
        String reason = string(
                args,
                "reason",
                "lua-ui-settings-close"
        );
        if (runtime != null) {
            runtime.closeSettings(reason);
        }
        Map<String, Object> result = base(runtime != null);
        result.put("screen", runtime == null ? "" : runtime.screen());
        return result;
    }

    private Map<String, Object> setSettingsSection(Map<String, Object> args) {
        String section = string(args, "section", "overview");
        FrozenLandsHtmlUiRuntime runtime = runtime();
        if (runtime != null) {
            runtime.setSettingsSection(section);
        }
        Map<String, Object> result = base(runtime != null);
        result.put(
                "section",
                runtime == null ? "" : runtime.settingsSection()
        );
        return result;
    }

    private Map<String, Object> settingsPreferences() {
        Map<String, Object> result = base(true);
        result.put("graphicsPreset", selectedGraphicsPreset);
        result.put("mouseSensitivity", selectedMouseSensitivity);
        Listener listener = listener();
        result.put("audioAvailable", listener != null);
        result.put(
                "masterVolume",
                listener == null ? selectedMasterVolume : listener.getVolume()
        );
        return result;
    }

    private Map<String, Object> selectGraphicsPreset(
            Map<String, Object> args
    ) {
        String requested = string(args, "preset", selectedGraphicsPreset)
                .trim()
                .toUpperCase(Locale.ROOT);
        if (!"LOW".equals(requested)
                && !"MEDIUM".equals(requested)
                && !"HIGH".equals(requested)
                && !"ULTRA".equals(requested)) {
            requested = "HIGH";
        }
        selectedGraphicsPreset = requested;
        FrozenLandsHtmlUiRuntime runtime = runtime();
        if (runtime != null) {
            runtime.setSelectedGraphicsPreset(selectedGraphicsPreset);
        }
        return applySelectedGraphicsPreset();
    }

    private Map<String, Object> applySelectedGraphicsPreset() {
        Map<String, Object> result = base(true);
        result.put("preset", selectedGraphicsPreset);
        try {
            Map<String, Object> response = context.getModuleRegistry().call(
                    "engine.quality",
                    "preset.apply",
                    Map.of("preset", selectedGraphicsPreset)
            );
            boolean applied = !Boolean.FALSE.equals(response.get("ok"));
            result.put("applied", applied);
            result.put("deferred", !applied);
            result.put("response", response);
        } catch (RuntimeException exception) {
            result.put("applied", false);
            result.put("deferred", true);
            result.put("reason", "quality-runtime-unavailable");
        }
        return result;
    }

    private Map<String, Object> selectMouseSensitivity(
            Map<String, Object> args
    ) {
        selectedMouseSensitivity = Math.max(
                0.2f,
                Math.min(
                        3f,
                        floating(
                                args,
                                "sensitivity",
                                selectedMouseSensitivity
                        )
                )
        );
        FrozenLandsHtmlUiRuntime runtime = runtime();
        if (runtime != null) {
            runtime.setRangeControlValue(
                    "settings-sensitivity-range",
                    selectedMouseSensitivity,
                    "settings-sensitivity-value",
                    sensitivityLabel(selectedMouseSensitivity)
            );
        }
        return applySelectedMouseSensitivity();
    }

    private Map<String, Object> applySelectedMouseSensitivity() {
        Map<String, Object> result = base(true);
        result.put("sensitivity", selectedMouseSensitivity);
        try {
            Map<String, Object> response = context.getModuleRegistry().call(
                    "engine.player",
                    "input.sensitivity.set",
                    Map.of("sensitivity", selectedMouseSensitivity)
            );
            boolean applied = Boolean.TRUE.equals(response.get("available"));
            if (response.get("sensitivity") instanceof Number number) {
                selectedMouseSensitivity = number.floatValue();
            }
            result.put("sensitivity", selectedMouseSensitivity);
            result.put("applied", applied);
            result.put("deferred", !applied);
            result.put("response", response);
        } catch (RuntimeException exception) {
            result.put("applied", false);
            result.put("deferred", true);
            result.put("reason", "player-runtime-unavailable");
        }
        return result;
    }

    private Map<String, Object> audioVolumeStatus() {
        Listener listener = listener();
        if (listener != null) {
            selectedMasterVolume = listener.getVolume();
        }
        Map<String, Object> result = base(listener != null);
        result.put("available", listener != null);
        result.put("volume", selectedMasterVolume);
        return result;
    }

    private Map<String, Object> setAudioVolume(Map<String, Object> args) {
        Listener listener = listener();
        float requested = floating(args, "volume", selectedMasterVolume);
        selectedMasterVolume = Math.max(0f, Math.min(1f, requested));
        if (listener != null) {
            listener.setVolume(selectedMasterVolume);
            selectedMasterVolume = listener.getVolume();
        }
        FrozenLandsHtmlUiRuntime runtime = runtime();
        if (runtime != null) {
            runtime.setRangeControlValue(
                    "settings-volume-range",
                    selectedMasterVolume,
                    "settings-volume-value",
                    volumeLabel(selectedMasterVolume)
            );
        }
        Map<String, Object> result = base(listener != null);
        result.put("available", listener != null);
        result.put("volume", selectedMasterVolume);
        return result;
    }

    private void synchronizeSettingsControls(
            FrozenLandsHtmlUiRuntime runtime
    ) {
        Listener listener = listener();
        if (listener != null) {
            selectedMasterVolume = listener.getVolume();
        }
        runtime.setSelectedGraphicsPreset(selectedGraphicsPreset);
        runtime.setRangeControlValue(
                "settings-volume-range",
                selectedMasterVolume,
                "settings-volume-value",
                volumeLabel(selectedMasterVolume)
        );
        runtime.setRangeControlValue(
                "settings-sensitivity-range",
                selectedMouseSensitivity,
                "settings-sensitivity-value",
                sensitivityLabel(selectedMouseSensitivity)
        );
    }

    private String volumeLabel(float volume) {
        return Math.round(Math.max(0f, Math.min(1f, volume)) * 100f) + "%";
    }

    private String sensitivityLabel(float sensitivity) {
        return String.format(
                Locale.ROOT,
                "%.2fx",
                Math.max(0.2f, Math.min(3f, sensitivity))
        );
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

    private float floating(
            Map<String, Object> args,
            String key,
            float fallback
    ) {
        if (args == null || key == null || key.isBlank()) {
            return fallback;
        }
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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

    private Listener listener() {
        if (context instanceof Kernel kernel
                && kernel.applicationHost() instanceof Application application) {
            return application.getListener();
        }
        return null;
    }

    private HtmlUiState state() {
        return context.appStateManager() == null ? null : context.appStateManager().getState(HtmlUiState.class);
    }

    private FrozenLandsHtmlUiRuntime runtime() {
        HtmlUiState state = state();
        return state == null ? null : state.runtime();
    }
}
