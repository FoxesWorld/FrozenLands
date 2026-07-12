-- FrozenLands Lua API: engine.save
-- Java is the host executor. This file is the Lua-side module facade.

local M = {
  id = "engine.save",
  commands = {
    "snapshot",
    "save",
    "overwrite",
    "delete",
    "load",
    "list"
  }
}

function M.call(command, args)
  args = args or {}
  return java.callModule(M.id, command, args)
end

return M
