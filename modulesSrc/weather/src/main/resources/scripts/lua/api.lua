local M = {
  id = "engine.weather",
  commands = {
    "status",
    "set",
    "preset.set",
    "preset.list",
    "intensity.set",
    "intensity.get",
    "precipitation.start",
    "precipitation.stop",
    "wind.set",
    "snow_start",
    "snow_stop",
    "snow_light",
    "snow_default",
    "snow_blizzard",
    "wind"
  },
  events = {
    change_requested = "weather.change.requested",
    preset_requested = "weather.preset.requested",
    intensity_requested = "weather.intensity.requested",
    precipitation_requested = "weather.precipitation.requested",
    wind_requested = "weather.wind.requested",
    state_changed = "weather.state.changed",
    environment_changed = "weather.environment.changed",
    preset_changed = "weather.preset.changed",
    intensity_changed = "weather.intensity.changed",
    precipitation_changed = "weather.precipitation.changed",
    wind_changed = "weather.wind.changed",
    effects_applied = "weather.effects.applied",
    sky_applied = "weather.sky.applied",
    sky_failed = "weather.sky.failed"
  }
}

function M.call(command, args)
  args = args or {}
  return java.callModule(M.id, command, args)
end

function M.status()
  return M.call("status", {})
end

function M.set(args)
  return M.call("set", args or {})
end

M.preset = {}

function M.preset.set(id, seconds)
  return M.call("preset.set", { preset = id, seconds = seconds })
end

function M.preset.list()
  return M.call("preset.list", {})
end

M.intensity = {}

function M.intensity.set(value)
  return M.call("intensity.set", { intensity = value })
end

function M.intensity.set_percent(percent)
  return M.call("intensity.set", { percent = percent })
end

function M.intensity.get()
  return M.call("intensity.get", {})
end

M.precipitation = {}

function M.precipitation.start(intensity)
  local args = {}
  if intensity ~= nil then
    args.intensity = intensity
  end
  return M.call("precipitation.start", args)
end

function M.precipitation.stop()
  return M.call("precipitation.stop", {})
end

function M.wind(x, z, y)
  return M.call("wind.set", { x = x or 0, y = y or 0, z = z or 0 })
end

-- Compatibility helpers.
M.start_snow = M.precipitation.start
M.stop_snow = M.precipitation.stop

function M.light_snow()
  return M.call("snow_light", {})
end

function M.default_snow()
  return M.call("snow_default", {})
end

function M.blizzard()
  return M.call("snow_blizzard", {})
end

return M
