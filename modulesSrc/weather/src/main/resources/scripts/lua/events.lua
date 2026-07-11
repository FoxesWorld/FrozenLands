local M = {
  id = "engine.weather",

  WEATHER_CHANGE_REQUESTED = "weather.change.requested",
  WEATHER_PRESET_REQUESTED = "weather.preset.requested",
  WEATHER_INTENSITY_REQUESTED = "weather.intensity.requested",
  WEATHER_PRECIPITATION_REQUESTED = "weather.precipitation.requested",
  WEATHER_WIND_REQUESTED = "weather.wind.requested",

  WEATHER_STATE_CHANGED = "weather.state.changed",
  WEATHER_ENVIRONMENT_CHANGED = "weather.environment.changed",
  WEATHER_PRESET_CHANGED = "weather.preset.changed",
  WEATHER_INTENSITY_CHANGED = "weather.intensity.changed",
  WEATHER_PRECIPITATION_CHANGED = "weather.precipitation.changed",
  WIND_CHANGED = "weather.wind.changed",

  WEATHER_EFFECTS_APPLIED = "weather.effects.applied",
  WEATHER_SKY_APPLIED = "weather.sky.applied",
  WEATHER_SKY_FAILED = "weather.sky.failed",

  SNOW_STARTED = "weather.snow.started",
  SNOW_STOPPED = "weather.snow.stopped",
  SNOW_DENSITY_CHANGED = "weather.snow.density.changed"
}

function M.on_event(event)
  return event
end

return M
