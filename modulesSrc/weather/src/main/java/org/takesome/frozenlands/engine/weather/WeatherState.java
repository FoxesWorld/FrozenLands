package org.takesome.frozenlands.engine.weather;

import com.jme3.math.Vector3f;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable canonical weather state owned by {@code engine.weather}. */
public record WeatherState(
        boolean initialized,
        boolean precipitationActive,
        String preset,
        WeatherIntensity intensity,
        Vector3f wind,
        float transitionSeconds,
        long revision,
        String reason,
        String source,
        Instant changedAt
) {
    public WeatherState {
        preset = preset == null ? "" : preset.trim();
        intensity = intensity == null ? WeatherIntensity.FULL : intensity;
        wind = wind == null ? new Vector3f() : wind.clone();
        transitionSeconds = sanitizeDuration(transitionSeconds);
        reason = reason == null ? "" : reason;
        source = source == null || source.isBlank() ? "engine.weather" : source;
        changedAt = changedAt == null ? Instant.now() : changedAt;
    }

    @Override
    public Vector3f wind() {
        return wind.clone();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("initialized", initialized);
        state.put("precipitationActive", precipitationActive);
        state.put("preset", preset);
        state.put("intensity", intensity.value());
        state.put("intensityPercent", intensity.percent());
        state.put("wind", WeatherPayloads.vectorMap(wind));
        state.put("transitionSeconds", transitionSeconds);
        state.put("revision", revision);
        state.put("reason", reason);
        state.put("source", source);
        state.put("changedAt", changedAt.toString());
        return state;
    }

    public WeatherState with(
            Boolean nextPrecipitationActive,
            String nextPreset,
            WeatherIntensity nextIntensity,
            Vector3f nextWind,
            Float nextTransitionSeconds,
            String nextReason,
            String nextSource
    ) {
        return new WeatherState(
                true,
                nextPrecipitationActive == null ? precipitationActive : nextPrecipitationActive,
                nextPreset == null ? preset : nextPreset,
                nextIntensity == null ? intensity : nextIntensity,
                nextWind == null ? wind : nextWind,
                nextTransitionSeconds == null ? transitionSeconds : nextTransitionSeconds,
                revision + 1,
                nextReason,
                nextSource,
                Instant.now()
        );
    }

    private static float sanitizeDuration(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(0f, value);
    }
}
