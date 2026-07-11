package org.takesome.frozenlands.engine.weather;

import java.util.LinkedHashMap;
import java.util.Map;

/** Normalized weather intensity value in the inclusive range {@code [0, 1]}. */
public record WeatherIntensity(float value) {
    public static final WeatherIntensity NONE = new WeatherIntensity(0f);
    public static final WeatherIntensity FULL = new WeatherIntensity(1f);

    public WeatherIntensity {
        value = clamp(value);
    }

    public int percent() {
        return Math.round(value * 100f);
    }

    public boolean active() {
        return value > 0f;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", value);
        result.put("percent", percent());
        return result;
    }

    public static WeatherIntensity from(Map<String, Object> values, WeatherIntensity fallback) {
        WeatherIntensity base = fallback == null ? FULL : fallback;
        if (values == null || values.isEmpty()) {
            return base;
        }
        Object percent = first(values, "percent", "intensityPercent");
        if (percent != null) {
            return new WeatherIntensity(number(percent, base.percent()) / 100f);
        }
        Object normalized = first(values, "intensity", "value", "amount", "strength");
        return normalized == null ? base : new WeatherIntensity(number(normalized, base.value()));
    }

    public static WeatherIntensity of(float value) {
        return new WeatherIntensity(value);
    }

    private static Object first(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    private static float number(Object value, float fallback) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return Float.parseFloat(String.valueOf(value));
    }

    private static float clamp(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }
}
