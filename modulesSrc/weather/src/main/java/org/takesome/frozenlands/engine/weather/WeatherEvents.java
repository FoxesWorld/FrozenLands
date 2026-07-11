package org.takesome.frozenlands.engine.weather;

/** Canonical request/state/effect topics owned by {@code engine.weather}. */
public final class WeatherEvents {
    public static final String WEATHER_CHANGE_REQUESTED = "weather.change.requested";
    public static final String WEATHER_PRESET_REQUESTED = "weather.preset.requested";
    public static final String WEATHER_INTENSITY_REQUESTED = "weather.intensity.requested";
    public static final String WEATHER_PRECIPITATION_REQUESTED = "weather.precipitation.requested";
    public static final String WEATHER_WIND_REQUESTED = "weather.wind.requested";

    public static final String WEATHER_STATE_CHANGED = "weather.state.changed";
    public static final String WEATHER_ENVIRONMENT_CHANGED = "weather.environment.changed";
    public static final String WEATHER_PRESET_CHANGED = "weather.preset.changed";
    public static final String WEATHER_INTENSITY_CHANGED = "weather.intensity.changed";
    public static final String WEATHER_PRECIPITATION_CHANGED = "weather.precipitation.changed";
    public static final String WIND_CHANGED = "weather.wind.changed";

    public static final String WEATHER_EFFECTS_APPLIED = "weather.effects.applied";
    public static final String WEATHER_SKY_APPLIED = "weather.sky.applied";
    public static final String WEATHER_SKY_FAILED = "weather.sky.failed";

    /** Compatibility topics retained for older scripts and save snapshots. */
    public static final String SNOW_STARTED = "weather.snow.started";
    public static final String SNOW_STOPPED = "weather.snow.stopped";
    public static final String SNOW_DENSITY_CHANGED = "weather.snow.density.changed";

    private WeatherEvents() {
    }
}
