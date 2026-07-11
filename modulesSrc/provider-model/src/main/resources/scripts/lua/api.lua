-- FrozenLands Lua API: engine.model
-- Models may be addressed by stable catalogue IDs or direct virtual asset paths.

local M = {
  id = "engine.model",
  commands = {
    "load",
    "list",
    "catalog",
    "reloadCatalog",
    "resolve",
    "detach"
  }
}

function M.call(command, args)
  return java.callModule(M.id, command, args or {})
end

function M.load(asset, options)
  options = options or {}
  options.asset = asset
  return M.call("load", options)
end

function M.resolve(asset)
  return M.call("resolve", { asset = asset })
end

return M
