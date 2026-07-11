package org.takesome.frozenlands.engine.weather;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.modules.EngineModule;
import org.takesome.frozenlands.engine.modules.ModuleCommand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Canonical weather-domain module and event-driven command facade. */
public final class WeatherModule implements EngineModule {
    public static final String ID = "engine.weather";

    private final Map<String, ModuleCommand> commands = new LinkedHashMap<>();
    private WeatherRuntimeConfig runtimeConfig;
    private WeatherController controller;
    private WeatherService effects;
    private WeatherSkyBridge skyBridge;
    private EngineContext context;

    public WeatherModule() {
        registerCommands();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Canonical event-driven weather module: preset, precipitation, intensity and wind.";
    }

    @Override
    public List<String> dependencies() {
        return List.of("engine.core", "engine.sky", "engine.world", "engine.player");
    }

    @Override
    public Map<String, ModuleCommand> commands() {
        return Collections.unmodifiableMap(commands);
    }

    @Override
    public void onRegister(EngineContext context) {
        this.context = context;
        runtimeConfig = WeatherRuntimeConfig.load(context);
        controller = new WeatherController(context, runtimeConfig);
        skyBridge = new WeatherSkyBridge(context);
        effects = new WeatherService(context, controller, runtimeConfig);

        context.registerService(WeatherController.class, controller);
        context.registerService(WeatherService.class, effects);
        context.registerService(WeatherSkyBridge.class, skyBridge);
        context.appStateManager().attach(effects);
        controller.publishInitialState();
    }

    @Override
    public void onUnregister(EngineContext context) {
        if (effects != null) {
            context.appStateManager().detach(effects);
        }
        close(skyBridge);
        close(controller);
        effects = null;
        skyBridge = null;
        controller = null;
        runtimeConfig = null;
        this.context = null;
    }

    public WeatherService service() {
        return effects;
    }

    public WeatherController controller() {
        return controller;
    }

    private void registerCommands() {
        commands.put("status", ModuleCommand.of("status", "Return canonical weather and effects status.", this::status));
        commands.put("set", ModuleCommand.of("set", "Atomically request preset, intensity, precipitation and/or wind changes.", this::set));
        commands.put("preset.set", ModuleCommand.of("preset.set", "Set the canonical weather/sky preset.", this::presetSet));
        commands.put("preset.list", ModuleCommand.of("preset.list", "List available weather presets from the sky adapter.", this::presetList));
        commands.put("intensity.set", ModuleCommand.of("intensity.set", "Set normalized weather intensity (0..1) or percent (0..100).", this::intensitySet));
        commands.put("intensity.get", ModuleCommand.of("intensity.get", "Return current normalized weather intensity.", this::intensityGet));
        commands.put("precipitation.start", ModuleCommand.of("precipitation.start", "Enable precipitation with optional intensity.", this::precipitationStart));
        commands.put("precipitation.stop", ModuleCommand.of("precipitation.stop", "Disable precipitation.", this::precipitationStop));
        commands.put("wind.set", ModuleCommand.of("wind.set", "Set weather wind vector: {x,y,z} or {wind={x,y,z}}.", this::windSet));

        // Compatibility aliases retained for existing scripts and snapshots.
        commands.put("snow_start", ModuleCommand.of("snow_start", "Compatibility alias for precipitation.start.", this::precipitationStart));
        commands.put("snow_stop", ModuleCommand.of("snow_stop", "Compatibility alias for precipitation.stop.", this::precipitationStop));
        commands.put("snow_light", ModuleCommand.of("snow_light", "Compatibility light-snow preset.", args -> snowMode(WeatherSnowMode.LIGHT)));
        commands.put("snow_default", ModuleCommand.of("snow_default", "Compatibility default-snow preset.", args -> snowMode(WeatherSnowMode.DEFAULT)));
        commands.put("snow_blizzard", ModuleCommand.of("snow_blizzard", "Compatibility blizzard preset.", args -> snowMode(WeatherSnowMode.BLIZZARD)));
        commands.put("wind", ModuleCommand.of("wind", "Compatibility alias for wind.set.", this::windSet));
    }

    private Map<String, Object> status(Map<String, Object> args) {
        if (controller == null) {
            return Map.of("ok", false, "initialized", false, "module", ID);
        }
        Map<String, Object> result = new LinkedHashMap<>(controller.status());
        result.put("ok", true);
        result.put("initialized", true);
        if (effects != null) {
            result.put("effects", effects.status());
        }
        return result;
    }

    private Map<String, Object> set(Map<String, Object> args) {
        Map<String, Object> payload = mutable(args);
        payload.putIfAbsent("source", ID + ".set");
        payload.putIfAbsent("reason", "module-command");
        return request(WeatherEvents.WEATHER_CHANGE_REQUESTED, payload);
    }

    private Map<String, Object> presetSet(Map<String, Object> args) {
        Map<String, Object> payload = mutable(args);
        String preset = firstString(payload, "preset", "weather", "id", "style");
        if (preset.isBlank()) {
            throw new IllegalArgumentException("Weather preset is blank; use preset/weather/id/style.");
        }
        payload.put("preset", preset);
        payload.putIfAbsent("source", ID + ".preset.set");
        payload.putIfAbsent("reason", "module-command");
        return request(WeatherEvents.WEATHER_PRESET_REQUESTED, payload);
    }

    private Map<String, Object> presetList(Map<String, Object> args) {
        if (skyBridge == null) {
            return Map.of("ok", false, "initialized", false, "module", ID);
        }
        Map<String, Object> result = new LinkedHashMap<>(skyBridge.listPresets());
        result.put("module", ID);
        result.put("owner", ID);
        return result;
    }

    private Map<String, Object> intensitySet(Map<String, Object> args) {
        WeatherIntensity intensity = WeatherIntensity.from(args, controller.snapshot().intensity());
        Map<String, Object> payload = new LinkedHashMap<>(intensity.toMap());
        payload.put("intensity", intensity.value());
        payload.put("source", ID + ".intensity.set");
        payload.put("reason", "module-command");
        return request(WeatherEvents.WEATHER_INTENSITY_REQUESTED, payload);
    }

    private Map<String, Object> intensityGet(Map<String, Object> args) {
        WeatherState state = controller.snapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("module", ID);
        result.put("intensity", state.intensity().value());
        result.put("percent", state.intensity().percent());
        result.put("active", state.precipitationActive());
        result.put("revision", state.revision());
        return result;
    }

    private Map<String, Object> precipitationStart(Map<String, Object> args) {
        Map<String, Object> payload = mutable(args);
        payload.put("active", true);
        if (!containsIntensity(payload) && !controller.snapshot().intensity().active()) {
            payload.put("intensity", runtimeConfig.initialIntensity().value());
        }
        payload.putIfAbsent("source", ID + ".precipitation.start");
        payload.putIfAbsent("reason", "module-command");
        return request(WeatherEvents.WEATHER_PRECIPITATION_REQUESTED, payload);
    }

    private Map<String, Object> precipitationStop(Map<String, Object> args) {
        Map<String, Object> payload = mutable(args);
        payload.put("active", false);
        payload.putIfAbsent("source", ID + ".precipitation.stop");
        payload.putIfAbsent("reason", "module-command");
        return request(WeatherEvents.WEATHER_PRECIPITATION_REQUESTED, payload);
    }

    private Map<String, Object> windSet(Map<String, Object> args) {
        Map<String, Object> payload = mutable(args);
        payload.putIfAbsent("source", ID + ".wind.set");
        payload.putIfAbsent("reason", "module-command");
        return request(WeatherEvents.WEATHER_WIND_REQUESTED, payload);
    }

    private Map<String, Object> snowMode(WeatherSnowMode mode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("active", true);
        payload.put("intensity", mode.intensity().value());
        payload.put("mode", mode.name().toLowerCase());
        payload.put("source", ID + ".compatibility");
        payload.put("reason", "legacy-snow-mode");
        return request(WeatherEvents.WEATHER_CHANGE_REQUESTED, payload);
    }

    private Map<String, Object> request(String topic, Map<String, Object> payload) {
        if (context == null || controller == null) {
            return Map.of("ok", false, "initialized", false, "module", ID);
        }
        Map<String, Object> event = context.getModuleRegistry().publishEvent(
                topic,
                payload,
                ID,
                Map.of("command", true)
        );
        Map<String, Object> result = new LinkedHashMap<>(status(Map.of()));
        result.put("requestTopic", topic);
        result.put("requestEventId", event.get("id"));
        return result;
    }

    private Map<String, Object> mutable(Map<String, Object> args) {
        return args == null ? new LinkedHashMap<>() : new LinkedHashMap<>(args);
    }

    private boolean containsIntensity(Map<String, Object> args) {
        return args.containsKey("intensity")
                || args.containsKey("value")
                || args.containsKey("amount")
                || args.containsKey("strength")
                || args.containsKey("percent")
                || args.containsKey("intensityPercent");
    }

    private String firstString(Map<String, Object> args, String... keys) {
        for (String key : keys) {
            Object value = args.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private void close(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
