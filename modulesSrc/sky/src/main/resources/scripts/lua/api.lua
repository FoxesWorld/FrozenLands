-- FrozenLands Lua API: engine.sky
-- Weather semantics belong to engine.weather; engine.sky exposes only the
-- low-level preset adapter retained for renderer integration.

local M = {
  id = "engine.sky",
  commands = {
    "status",
    "command.execute",
    "atmosphere.setGradient",
    "preset.set",
    "preset.list",
    "weather.set",
    "weather.list",
    "clock.setTime",
    "environment.snapshot"
  },
  events = {
    command_queued = "sky.command.queued",
    transition_started = "sky.transition.started"
  }
}

function M.call(command, args)
  args = args or {}
  return java.callModule(M.id, command, args)
end

function M.status()
  return M.call("status", {})
end

function M.command(command_id, args)
  return M.call("command.execute", { commandId = command_id, args = args or {} })
end

M.atmosphere = {}

function M.atmosphere.setGradient(style, seconds)
  return M.call("atmosphere.setGradient", { style = style, seconds = seconds or 0 })
end

M.preset = {}

function M.preset.set(id, seconds)
  local args = { preset = id }
  if seconds ~= nil then
    args.seconds = seconds
  end
  return M.call("preset.set", args)
end

function M.preset.list()
  return M.call("preset.list", {})
end

-- Compatibility aliases. New gameplay scripts should require engine.weather.
M.weather = {}
M.weather.set = M.preset.set
M.weather.list = M.preset.list

M.clock = {}

function M.clock.setTime(hour)
  return M.call("clock.setTime", { hour = hour })
end

M.environment = {}

function M.environment.snapshot()
  return M.call("environment.snapshot", {})
end

M.atmosphere.set_gradient = M.atmosphere.setGradient
M.clock.set_time = M.clock.setTime

return M
