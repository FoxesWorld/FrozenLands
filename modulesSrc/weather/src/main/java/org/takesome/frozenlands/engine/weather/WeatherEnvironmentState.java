package org.takesome.frozenlands.engine.weather;

import com.jme3.math.Vector3f;
import org.takesome.frozenlands.engine.weather.snow.SnowPrecipitationSystem;

import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime effects snapshot layered on top of the canonical weather state. */
public record WeatherEnvironmentState(
        boolean initialized,
        boolean autoStartSnow,
        boolean snowRunning,
        int flakes,
        int allocatedFlakes,
        String preset,
        WeatherIntensity intensity,
        Vector3f wind,
        Vector3f anchor,
        long revision,
        SnowPrecipitationSystem.Config snowConfig
) {
    public WeatherEnvironmentState {
        preset = preset == null ? "" : preset;
        intensity = intensity == null ? WeatherIntensity.NONE : intensity;
        wind = wind == null ? new Vector3f() : wind.clone();
        anchor = anchor == null ? new Vector3f() : anchor.clone();
    }

    @Override
    public Vector3f wind() {
        return wind.clone();
    }

    @Override
    public Vector3f anchor() {
        return anchor.clone();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("initialized", initialized);
        status.put("autoStartSnow", autoStartSnow);
        status.put("precipitationActive", snowRunning);
        status.put("snowRunning", snowRunning);
        status.put("flakes", flakes);
        status.put("allocatedFlakes", allocatedFlakes);
        status.put("preset", preset);
        status.put("intensity", intensity.value());
        status.put("intensityPercent", intensity.percent());
        status.put("wind", WeatherPayloads.vectorMap(wind));
        status.put("anchor", WeatherPayloads.vectorMap(anchor));
        status.put("revision", revision);
        if (snowConfig != null) {
            status.put("snowConfig", configMap(snowConfig));
        }
        return status;
    }

    public static WeatherEnvironmentState from(
            boolean initialized,
            WeatherRuntimeConfig runtimeConfig,
            WeatherState weatherState,
            SnowPrecipitationSystem snow,
            Vector3f anchor
    ) {
        WeatherRuntimeConfig config = runtimeConfig == null
                ? WeatherRuntimeConfig.defaultConfig()
                : runtimeConfig;
        WeatherState state = weatherState == null ? config.initialState() : weatherState;
        return new WeatherEnvironmentState(
                initialized,
                config.autoStartSnow(),
                snow != null && snow.running() && snow.flakeCount() > 0,
                snow == null ? 0 : snow.flakeCount(),
                snow == null ? 0 : snow.allocatedFlakeCount(),
                state.preset(),
                state.intensity(),
                snow == null ? state.wind() : snow.wind(),
                anchor,
                state.revision(),
                snow == null ? config.snowConfig() : snow.config()
        );
    }

    private static Map<String, Object> configMap(SnowPrecipitationSystem.Config config) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maxFlakes", config.maxFlakes());
        result.put("spawnWidth", config.spawnWidth());
        result.put("spawnDepth", config.spawnDepth());
        result.put("spawnMinHeight", config.spawnMinHeight());
        result.put("spawnMaxHeight", config.spawnMaxHeight());
        result.put("killBelowHeight", config.killBelowHeight());
        result.put("minFallSpeed", config.minFallSpeed());
        result.put("maxFallSpeed", config.maxFallSpeed());
        result.put("drift", config.drift());
        result.put("minSize", config.minSize());
        result.put("maxSize", config.maxSize());
        result.put("minAlpha", config.minAlpha());
        result.put("maxAlpha", config.maxAlpha());
        result.put("minLife", config.minLife());
        result.put("maxLife", config.maxLife());
        result.put("maxAngularVelocity", config.maxAngularVelocity());
        result.put("texture", config.texture());
        return result;
    }
}
