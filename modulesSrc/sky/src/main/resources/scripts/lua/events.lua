-- FrozenLands Lua Events: engine.sky
-- Canonical weather events are exported by engine.weather.

local PRESET_CHANGED = "sky.weather.changed"

return {
  command_queued = "sky.command.queued",
  transition_started = "sky.transition.started",
  preset_changed = PRESET_CHANGED,
  weather_changed = PRESET_CHANGED,
  clock_changed = "sky.clock.changed"
}
