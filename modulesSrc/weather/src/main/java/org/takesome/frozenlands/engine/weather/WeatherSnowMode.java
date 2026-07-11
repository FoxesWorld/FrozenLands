package org.takesome.frozenlands.engine.weather;

import org.takesome.frozenlands.engine.weather.snow.SnowPrecipitationSystem;

import java.util.Locale;

/** Compatibility snow modes expressed through the canonical intensity scale. */
public enum WeatherSnowMode {
    LIGHT(0.30f),
    DEFAULT(0.65f),
    BLIZZARD(1.00f);

    private final WeatherIntensity intensity;

    WeatherSnowMode(float intensity) {
        this.intensity = WeatherIntensity.of(intensity);
    }

    public WeatherIntensity intensity() {
        return intensity;
    }

    /** Retained for older callers that still require a concrete snow config. */
    public SnowPrecipitationSystem.Config config(SnowPrecipitationSystem.Config current) {
        SnowPrecipitationSystem.Config target = switch (this) {
            case LIGHT -> SnowPrecipitationSystem.Config.light();
            case DEFAULT -> SnowPrecipitationSystem.Config.defaultConfig();
            case BLIZZARD -> SnowPrecipitationSystem.Config.blizzard();
        };
        if (current != null && current.texture() != null && !current.texture().isBlank()) {
            return target.withTexture(current.texture());
        }
        return target;
    }

    public static WeatherSnowMode from(String mode) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "light" -> LIGHT;
            case "blizzard", "storm" -> BLIZZARD;
            case "default", "normal", "snow" -> DEFAULT;
            default -> null;
        };
    }
}
