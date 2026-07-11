-- FrozenLands Lua API: frozenlands.ui
local M = {
  id = "frozenlands.ui",
  commands = {
    "status",
    "status.set",
    "screen",
    "screen.set",
    "gameplay.enter",
    "gameplay.gate",
    "saves.refresh",
    "devtools.open",
    "event.dispatch",
    "action.dispatch",
    "runtime.exit"
  }
}

local function call_module(command, args)
  args = args or {}
  if java and java.callModule then
    return java.callModule(M.id, command, args)
  end
  if engine and engine.call then
    return engine.call(M.id, command, args)
  end
  error("frozenlands.ui Lua API has no Java bridge")
end

function M.call(command, args)
  return call_module(command, args)
end

function M.emit(topic, payload)
  payload = payload or {}
  payload.event = payload.event or topic
  payload.topic = payload.topic or topic
  if java and java.publishModuleEvent then
    return java.publishModuleEvent(topic, payload)
  end
  return call_module("event.dispatch", payload)
end

return M
