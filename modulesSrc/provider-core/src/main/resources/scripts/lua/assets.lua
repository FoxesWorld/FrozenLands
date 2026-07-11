-- FrozenLands Lua API: engine.assets
-- Stable IDs are preferred; direct virtual asset paths remain supported.

local M = {
  id = "engine.assets",
  commands = {
    "status",
    "catalog",
    "resolve",
    "preload",
    "evict",
    "clear"
  }
}

function M.call(command, args)
  return java.callModule(M.id, command, args or {})
end

function M.catalog(assetType)
  return M.call("catalog", { type = assetType })
end

function M.resolve(assetType, asset)
  return M.call("resolve", { type = assetType, asset = asset })
end

function M.preload(assetType, asset)
  return M.call("preload", { type = assetType, asset = asset })
end

return M
