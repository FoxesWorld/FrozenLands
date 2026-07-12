-- FrozenLands Lua API: engine.icoParser
-- Java is the host executor. This file is the Lua-side module facade.

local M = {
  id = "engine.icoParser",
  commands = {
    "status",
    "inspect",
    "best",
    "load",
    "cache.clear"
  }
}

function M.call(command, args)
  args = args or {}
  return java.callModule(M.id, command, args)
end

function M.inspect(path)
  return M.call("inspect", { path = path })
end

function M.best(path, width, height)
  return M.call("best", { path = path, width = width or 256, height = height or width or 256 })
end

function M.load(reference, assetsDirectory)
  return M.call("load", { reference = reference, assetsDirectory = assetsDirectory })
end

function M.clearCache()
  return M.call("cache.clear", {})
end

return M
