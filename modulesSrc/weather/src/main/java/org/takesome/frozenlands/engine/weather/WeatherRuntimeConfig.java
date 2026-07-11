package org.takesome.frozenlands.engine.weather;

import com.jme3.math.Vector3f;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.resources.ModuleIndexCatalog;
import org.takesome.frozenlands.engine.weather.snow.SnowPrecipitationSystem;

import java.util.Map;

public record WeatherRuntimeConfig(
        boolean autoStartSnow,
        WeatherIntensity initialIntensity,
        String initialPreset,
        float transitionSeconds,
        Vector3f initialWind,
        SnowPrecipitationSystem.Config snowConfig
) {
    public WeatherRuntimeConfig {
        initialIntensity = initialIntensity == null ? WeatherIntensity.FULL : initialIntensity;
        initialPreset = initialPreset == null ? "" : initialPreset.trim();
        transitionSeconds = sanitizeDuration(transitionSeconds);
        initialWind = initialWind == null ? defaultWind() : initialWind.clone();
        snowConfig = snowConfig == null ? SnowPrecipitationSystem.Config.defaultConfig() : snowConfig;
    }

    @Override
    public Vector3f initialWind() {
        return initialWind.clone();
    }

    public WeatherState initialState() {
        return new WeatherState(
                true,
                autoStartSnow && initialIntensity.active(),
                initialPreset,
                initialIntensity,
                initialWind,
                transitionSeconds,
                1L,
                "runtime-config",
                "engine.weather",
                null
        );
    }

    public static WeatherRuntimeConfig defaultConfig() {
        return new WeatherRuntimeConfig(
                true,
                WeatherIntensity.FULL,
                "",
                5f,
                defaultWind(),
                SnowPrecipitationSystem.Config.defaultConfig()
        );
    }

    public static WeatherRuntimeConfig load(EngineContext context) {
        try {
            Map<String, Object> root = ModuleIndexCatalog.defaultCatalog()
                    .readConfigMap(WeatherModule.ID, "weather");
            Map<String, Object> weather = WeatherPayloads.map(root.get("weather"));
            boolean autoStartSnow = WeatherPayloads.bool(weather, "autoStartSnow", true);
            WeatherIntensity initialIntensity = WeatherIntensity.from(
                    Map.of("intensity", WeatherPayloads.number(weather, "initialIntensity", 1f)),
                    WeatherIntensity.FULL
            );
            String initialPreset = WeatherPayloads.string(weather, "initialPreset", "");
            float transitionSeconds = WeatherPayloads.number(weather, "transitionSeconds", 5f);
            Vector3f initialWind = WeatherPayloads.vector(
                    WeatherPayloads.map(weather.get("initialWind")),
                    defaultWind()
            );
            SnowPrecipitationSystem.Config snowConfig = SnowPrecipitationSystem.Config.from(
                    WeatherPayloads.map(weather.get("snow")),
                    SnowPrecipitationSystem.Config.defaultConfig()
            );
            return new WeatherRuntimeConfig(
                    autoStartSnow,
                    initialIntensity,
                    initialPreset,
                    transitionSeconds,
                    initialWind,
                    snowConfig
            );
        } catch (RuntimeException exception) {
            if (context != null) {
                context.getLogger().warn("Weather config load failed; using defaults", exception);
            }
            return defaultConfig();
        }
    }

    private static Vector3f defaultWind() {
        return new Vector3f(0.75f, 0f, -0.25f);
    }

    private static float sanitizeDuration(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(0f, value);
    }
}
