package org.takesome.frozenlands.engine.weather;

import org.takesome.frozenlands.engine.EngineContext;

import java.util.LinkedHashMap;
import java.util.Map;

/** Applies canonical weather preset changes through the low-level sky module. */
public final class WeatherSkyBridge implements AutoCloseable {
    private static final String SKY_MODULE = "engine.sky";

    private final EngineContext context;
    private final AutoCloseable presetSubscription;

    public WeatherSkyBridge(EngineContext context) {
        this.context = context;
        this.presetSubscription = context.getModuleRegistry().getEventBus().subscribe(
                WeatherEvents.WEATHER_PRESET_CHANGED,
                this::onPresetChanged,
                false
        );
    }

    public Map<String, Object> listPresets() {
        return context.getModuleRegistry().call(SKY_MODULE, "preset.list", Map.of());
    }

    @Override
    public void close() {
        try {
            presetSubscription.close();
        } catch (Exception ignored) {
        }
    }

    private void onPresetChanged(Map<String, Object> event) {
        Map<String, Object> payload = WeatherPayloads.payload(event);
        String preset = WeatherPayloads.string(payload, "preset", "").trim();
        if (preset.isBlank()) {
            return;
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("preset", preset);
        request.put("seconds", WeatherPayloads.number(payload, "transitionSeconds", 0f));
        request.put("source", WeatherModule.ID);
        try {
            Map<String, Object> response = context.getModuleRegistry().call(SKY_MODULE, "preset.set", request);
            Map<String, Object> applied = new LinkedHashMap<>(payload);
            applied.put("sky", response);
            context.getModuleRegistry().publishEvent(
                    WeatherEvents.WEATHER_SKY_APPLIED,
                    applied,
                    WeatherModule.ID,
                    Map.of("adapter", "engine.sky")
            );
        } catch (RuntimeException exception) {
            Map<String, Object> failed = new LinkedHashMap<>(payload);
            failed.put("message", exception.toString());
            context.getModuleRegistry().publishEvent(
                    WeatherEvents.WEATHER_SKY_FAILED,
                    failed,
                    WeatherModule.ID,
                    Map.of("adapter", "engine.sky")
            );
            context.getLogger().error(
                    "Weather sky preset apply failed preset={} seconds={} message={}",
                    preset,
                    request.get("seconds"),
                    exception.toString(),
                    exception
            );
        }
    }
}
