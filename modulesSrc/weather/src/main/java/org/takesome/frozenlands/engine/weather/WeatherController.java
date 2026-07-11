package org.takesome.frozenlands.engine.weather;

import com.jme3.math.Vector3f;
import org.takesome.frozenlands.engine.EngineContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical weather-domain controller.
 *
 * <p>Commands and scripts publish request events. This controller is the only
 * component allowed to mutate weather state; visual systems subscribe to the
 * resulting state events. That keeps precipitation, sky rendering and future
 * audio/temperature consumers independent.</p>
 */
public final class WeatherController implements AutoCloseable {
    private final EngineContext context;
    private final List<AutoCloseable> subscriptions = new ArrayList<>();
    private WeatherState state;

    public WeatherController(EngineContext context, WeatherRuntimeConfig config) {
        this.context = context;
        this.state = (config == null ? WeatherRuntimeConfig.defaultConfig() : config).initialState();
        subscribeRequests();
    }

    public synchronized WeatherState snapshot() {
        return state;
    }

    public synchronized Map<String, Object> status() {
        Map<String, Object> result = state.toMap();
        result.put("module", WeatherModule.ID);
        return result;
    }

    public void publishInitialState() {
        WeatherState snapshot = snapshot();
        publishState(snapshot, null, "initialized");
    }

    @Override
    public void close() {
        for (AutoCloseable subscription : List.copyOf(subscriptions)) {
            closeQuietly(subscription);
        }
        subscriptions.clear();
    }

    private void subscribeRequests() {
        subscriptions.add(context.getModuleRegistry().getEventBus().subscribe(
                WeatherEvents.WEATHER_CHANGE_REQUESTED,
                this::onChangeRequested,
                false
        ));
        subscriptions.add(context.getModuleRegistry().getEventBus().subscribe(
                WeatherEvents.WEATHER_PRESET_REQUESTED,
                this::onPresetRequested,
                false
        ));
        subscriptions.add(context.getModuleRegistry().getEventBus().subscribe(
                WeatherEvents.WEATHER_INTENSITY_REQUESTED,
                this::onIntensityRequested,
                false
        ));
        subscriptions.add(context.getModuleRegistry().getEventBus().subscribe(
                WeatherEvents.WEATHER_PRECIPITATION_REQUESTED,
                this::onPrecipitationRequested,
                false
        ));
        subscriptions.add(context.getModuleRegistry().getEventBus().subscribe(
                WeatherEvents.WEATHER_WIND_REQUESTED,
                this::onWindRequested,
                false
        ));
        subscriptions.add(context.getModuleRegistry().getEventBus().subscribe(
                WeatherEvents.SNOW_STARTED,
                this::onLegacySnowStarted,
                false
        ));
        subscriptions.add(context.getModuleRegistry().getEventBus().subscribe(
                WeatherEvents.SNOW_STOPPED,
                this::onLegacySnowStopped,
                false
        ));
        subscriptions.add(context.getModuleRegistry().getEventBus().subscribe(
                WeatherEvents.SNOW_DENSITY_CHANGED,
                this::onLegacySnowDensityChanged,
                false
        ));
        subscriptions.add(context.getModuleRegistry().getEventBus().subscribe(
                WeatherEvents.WIND_CHANGED,
                this::onLegacyWindChanged,
                false
        ));
    }

    private void onChangeRequested(Map<String, Object> event) {
        Map<String, Object> payload = WeatherPayloads.payload(event);
        WeatherState current = snapshot();

        Boolean active = firstBoolean(payload, "precipitationActive", "active", "running", "enabled");
        String preset = firstString(payload, "preset", "weather", "weatherId");
        WeatherIntensity intensity = containsAny(payload, "intensity", "value", "amount", "strength", "percent", "intensityPercent")
                ? WeatherIntensity.from(payload, current.intensity())
                : null;
        Vector3f wind = resolveWind(payload, current.wind());
        Float transitionSeconds = firstNumber(payload, "transitionSeconds", "seconds", "duration");

        applyChange(
                active,
                preset,
                intensity,
                wind,
                transitionSeconds,
                WeatherPayloads.string(payload, "reason", "weather-change-request"),
                WeatherPayloads.string(payload, "source", "engine.weather")
        );
    }

    private void onPresetRequested(Map<String, Object> event) {
        Map<String, Object> payload = WeatherPayloads.payload(event);
        String preset = firstString(payload, "preset", "weather", "id", "style");
        if (preset == null || preset.isBlank()) {
            context.getLogger().warn("Weather preset request ignored because preset is blank: {}", payload);
            return;
        }
        applyChange(
                null,
                preset,
                null,
                null,
                firstNumber(payload, "transitionSeconds", "seconds", "duration"),
                WeatherPayloads.string(payload, "reason", "weather-preset-request"),
                WeatherPayloads.string(payload, "source", "engine.weather")
        );
    }

    private void onIntensityRequested(Map<String, Object> event) {
        Map<String, Object> payload = WeatherPayloads.payload(event);
        applyChange(
                null,
                null,
                WeatherIntensity.from(payload, snapshot().intensity()),
                null,
                null,
                WeatherPayloads.string(payload, "reason", "weather-intensity-request"),
                WeatherPayloads.string(payload, "source", "engine.weather")
        );
    }

    private void onPrecipitationRequested(Map<String, Object> event) {
        Map<String, Object> payload = WeatherPayloads.payload(event);
        Boolean active = firstBoolean(payload, "precipitationActive", "active", "running", "enabled");
        if (active == null) {
            context.getLogger().warn("Weather precipitation request ignored because active/running is missing: {}", payload);
            return;
        }
        WeatherIntensity intensity = containsAny(payload, "intensity", "value", "percent", "intensityPercent")
                ? WeatherIntensity.from(payload, snapshot().intensity())
                : null;
        applyChange(
                active,
                null,
                intensity,
                null,
                null,
                WeatherPayloads.string(payload, "reason", "weather-precipitation-request"),
                WeatherPayloads.string(payload, "source", "engine.weather")
        );
    }

    private void onWindRequested(Map<String, Object> event) {
        Map<String, Object> payload = WeatherPayloads.payload(event);
        Vector3f wind = resolveWind(payload, snapshot().wind());
        if (wind == null) {
            context.getLogger().warn("Weather wind request ignored because no wind vector was supplied: {}", payload);
            return;
        }
        applyChange(
                null,
                null,
                null,
                wind,
                null,
                WeatherPayloads.string(payload, "reason", "weather-wind-request"),
                WeatherPayloads.string(payload, "source", "engine.weather")
        );
    }

    private void onLegacySnowStarted(Map<String, Object> event) {
        if (ownEvent(event)) {
            return;
        }
        Map<String, Object> payload = WeatherPayloads.payload(event);
        WeatherSnowMode mode = WeatherSnowMode.from(WeatherPayloads.string(payload, "mode", ""));
        applyChange(
                true,
                null,
                mode == null ? null : mode.intensity(),
                null,
                null,
                "legacy-weather.snow.started",
                emitter(event)
        );
    }

    private void onLegacySnowStopped(Map<String, Object> event) {
        if (ownEvent(event)) {
            return;
        }
        applyChange(false, null, null, null, null, "legacy-weather.snow.stopped", emitter(event));
    }

    private void onLegacySnowDensityChanged(Map<String, Object> event) {
        if (ownEvent(event)) {
            return;
        }
        Map<String, Object> payload = WeatherPayloads.payload(event);
        WeatherSnowMode mode = WeatherSnowMode.from(WeatherPayloads.string(payload, "mode", ""));
        WeatherIntensity intensity = mode == null
                ? WeatherIntensity.from(payload, snapshot().intensity())
                : mode.intensity();
        Boolean active = firstBoolean(payload, "precipitationActive", "active", "running", "enabled");
        applyChange(
                active,
                null,
                intensity,
                null,
                null,
                "legacy-weather.snow.density.changed",
                emitter(event)
        );
    }

    private void onLegacyWindChanged(Map<String, Object> event) {
        if (ownEvent(event)) {
            return;
        }
        Map<String, Object> payload = WeatherPayloads.payload(event);
        Vector3f wind = resolveWind(payload, snapshot().wind());
        if (wind != null) {
            applyChange(null, null, null, wind, null, "legacy-weather.wind.changed", emitter(event));
        }
    }

    private void applyChange(
            Boolean active,
            String preset,
            WeatherIntensity intensity,
            Vector3f wind,
            Float transitionSeconds,
            String reason,
            String source
    ) {
        WeatherState previous;
        WeatherState next;
        synchronized (this) {
            previous = state;
            next = previous.with(
                    active,
                    preset,
                    intensity,
                    wind,
                    transitionSeconds,
                    reason,
                    source
            );
            if (sameEnvironment(previous, next)) {
                return;
            }
            state = next;
        }
        publishState(next, previous, reason);
    }

    private void publishState(WeatherState next, WeatherState previous, String reason) {
        Map<String, Object> payload = next.toMap();
        payload.put("changeId", "weather:" + next.revision());
        payload.put("reason", reason == null ? next.reason() : reason);

        context.getModuleRegistry().publishEvent(
                WeatherEvents.WEATHER_STATE_CHANGED,
                payload,
                WeatherModule.ID,
                Map.of("revision", next.revision())
        );
        context.getModuleRegistry().publishEvent(
                WeatherEvents.WEATHER_ENVIRONMENT_CHANGED,
                payload,
                WeatherModule.ID,
                Map.of("revision", next.revision(), "compatibility", true)
        );

        if (previous == null || !previous.preset().equals(next.preset())
                || previous.transitionSeconds() != next.transitionSeconds()) {
            Map<String, Object> changed = changedPayload(payload, "previousPreset", previous == null ? "" : previous.preset());
            context.getModuleRegistry().publishEvent(WeatherEvents.WEATHER_PRESET_CHANGED, changed, WeatherModule.ID, Map.of());
        }
        if (previous == null || previous.intensity().value() != next.intensity().value()) {
            Map<String, Object> changed = changedPayload(
                    payload,
                    "previousIntensity",
                    previous == null ? 0f : previous.intensity().value()
            );
            context.getModuleRegistry().publishEvent(WeatherEvents.WEATHER_INTENSITY_CHANGED, changed, WeatherModule.ID, Map.of());
            context.getModuleRegistry().publishEvent(WeatherEvents.SNOW_DENSITY_CHANGED, changed, WeatherModule.ID, Map.of("compatibility", true));
        }
        if (previous == null || previous.precipitationActive() != next.precipitationActive()) {
            Map<String, Object> changed = changedPayload(
                    payload,
                    "previousActive",
                    previous != null && previous.precipitationActive()
            );
            context.getModuleRegistry().publishEvent(WeatherEvents.WEATHER_PRECIPITATION_CHANGED, changed, WeatherModule.ID, Map.of());
            context.getModuleRegistry().publishEvent(
                    next.precipitationActive() ? WeatherEvents.SNOW_STARTED : WeatherEvents.SNOW_STOPPED,
                    changed,
                    WeatherModule.ID,
                    Map.of("compatibility", true)
            );
        }
        if (previous == null || !previous.wind().equals(next.wind())) {
            Map<String, Object> changed = changedPayload(
                    payload,
                    "previousWind",
                    previous == null ? WeatherPayloads.vectorMap(Vector3f.ZERO) : WeatherPayloads.vectorMap(previous.wind())
            );
            context.getModuleRegistry().publishEvent(WeatherEvents.WIND_CHANGED, changed, WeatherModule.ID, Map.of());
        }
    }

    private Map<String, Object> changedPayload(Map<String, Object> statePayload, String key, Object previous) {
        Map<String, Object> changed = new LinkedHashMap<>(statePayload);
        changed.put(key, previous);
        return changed;
    }

    private boolean sameEnvironment(WeatherState left, WeatherState right) {
        return left.precipitationActive() == right.precipitationActive()
                && left.preset().equals(right.preset())
                && left.intensity().value() == right.intensity().value()
                && left.wind().equals(right.wind())
                && left.transitionSeconds() == right.transitionSeconds();
    }

    private Vector3f resolveWind(Map<String, Object> payload, Vector3f fallback) {
        Map<String, Object> nested = WeatherPayloads.map(payload.get("wind"));
        if (!nested.isEmpty()) {
            return WeatherPayloads.vector(nested, fallback);
        }
        if (containsAny(payload, "x", "y", "z")) {
            return WeatherPayloads.vector(payload, fallback);
        }
        return null;
    }

    private Boolean firstBoolean(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            if (!payload.containsKey(key)) {
                continue;
            }
            Object value = payload.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return null;
    }

    private String firstString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private Float firstNumber(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof Number number) {
                return number.floatValue();
            }
            if (value != null && !String.valueOf(value).isBlank()) {
                return Float.parseFloat(String.valueOf(value));
            }
        }
        return null;
    }

    private boolean containsAny(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean ownEvent(Map<String, Object> event) {
        return WeatherModule.ID.equals(emitter(event));
    }

    private String emitter(Map<String, Object> event) {
        Object value = event == null ? null : event.get("emitter");
        return value == null || String.valueOf(value).isBlank()
                ? "legacy-weather-event"
                : String.valueOf(value);
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
