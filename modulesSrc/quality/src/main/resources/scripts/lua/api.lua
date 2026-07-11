-- FrozenLands Lua API: engine.quality
local M = {
  id = "engine.quality",
  commands = {
    "status",
    "preset.list",
    "preset.apply",
    "custom.apply"
  }
}

function M.call(command, args)
  return java.callModule(M.id, command, args or {})
end

function M.status()
  return M.call("status")
end

function M.presets()
  return M.call("preset.list")
end

function M.apply(preset)
  return M.call("preset.apply", { preset = preset })
end

function M.custom(overrides)
  return M.call("custom.apply", overrides or {})
end

return M
