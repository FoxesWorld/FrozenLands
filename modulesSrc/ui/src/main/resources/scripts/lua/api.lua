-- FrozenLands Lua API: frozenlands.ui
local M = {
  id = "frozenlands.ui",
  commands = {
    "status",
    "status.set",
    "screen",
    "screen.set",
    "settings.open",
    "settings.close",
    "settings.section",
    "settings.preferences",
    "settings.graphics.select",
    "settings.graphics.applyPending",
    "settings.controls.sensitivity.select",
    "settings.controls.applyPending",
    "audio.volume.status",
    "audio.volume.set",
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

function M.open_settings(return_screen)
  return call_module("settings.open", {
    returnScreen = return_screen or "main-menu"
  })
end

function M.close_settings()
  return call_module("settings.close", {})
end

function M.show_settings_section(section)
  return call_module("settings.section", {
    section = section or "overview"
  })
end

function M.select_graphics_preset(preset)
  return call_module("settings.graphics.select", {
    preset = preset or "HIGH"
  })
end

function M.select_mouse_sensitivity(sensitivity)
  return call_module("settings.controls.sensitivity.select", {
    sensitivity = sensitivity or 1.0
  })
end

function M.set_master_volume(volume)
  return call_module("audio.volume.set", {
    volume = volume or 1.0
  })
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
