local ui = require("frozenlands.ui")

local DEFAULT_SAVE_SLOT = "quick"
local event = args.event or {}
local payload = args.payload or {}
local topic = args.topic or payload.topic or payload.event or event.topic or ""

local function status(message)
  return ui.call("status.set", { message = message or "" })
end

local function safe_module_call(moduleId, commandId, request)
  local ok, response = pcall(
    java.callModule,
    moduleId,
    commandId,
    request or {}
  )
  if not ok then
    return false, { ok = false, error = tostring(response) }
  end
  return response.ok ~= false, response
end

local function title_case(value)
  local text = tostring(value or "")
  if text == "" then
    return ""
  end
  return string.upper(string.sub(text, 1, 1))
      .. string.lower(string.sub(text, 2))
end

local function refresh_saves()
  return ui.call("saves.refresh", {})
end

local function show_loading(stage, progress)
  return java.callModule("engine.core", "ui.screen.show", {
    id = "loading",
    stage = stage or "Preparing expedition runtime",
    progress = progress or 0
  })
end

local function update_loading(stage, progress)
  return java.callModule("engine.core", "ui.screen.update", {
    id = "loading",
    stage = stage or "Preparing expedition runtime",
    progress = progress or 0
  })
end

local function hide_loading()
  return java.callModule("engine.core", "ui.screen.hide", { id = "loading" })
end

local function enter_gameplay(reason)
  hide_loading()
  return ui.call("gameplay.enter", { reason = reason or "lua-ui-enter-gameplay" })
end

local function start_game_runtime(reason, operation, slot)
  show_loading("Preparing expedition runtime", 0.02)
  local request = {
    reason = reason or "lua-ui-start-game-runtime",
    operation = operation or "newGame",
    source = "frozenlands.ui"
  }
  if slot ~= nil and slot ~= "" then
    request.slot = slot
  end
  local response = java.callModule("engine.core", "runtime.game.start", request)
  if response.ok == false then
    update_loading("Game runtime failed to start", 1.0)
    status("Game runtime failed to start.")
    return false, response
  end
  if response.accepted == false and response.busy then
    update_loading("Expedition start already in progress", response.progress or 0.04)
    status("Expedition is already starting. Please wait.")
    return true, response
  end
  if response.alreadyStarted then
    update_loading("Preparing expedition scene", 0.96)
  else
    update_loading("Starting expedition runtime", 0.04)
  end
  return true, response
end

local function gate_menu(menuVisible, reason)
  return ui.call("gameplay.gate", {
    menuVisible = menuVisible and true or false,
    reason = reason or "lua-ui-gate"
  })
end

local function save_list()
  local response = java.callModule("engine.save", "list", {})
  return response.saves or {}
end

local function latest_save(saves)
  local latest = nil
  for _, save in ipairs(saves or {}) do
    if latest == nil then
      latest = save
    else
      local modified = tostring(save.modifiedAt or "")
      local latestModified = tostring(latest.modifiedAt or "")
      if modified > latestModified then
        latest = save
      end
    end
  end
  return latest
end

local function do_load_slot(slot, runtimeStart)
  local safeSlot = slot or DEFAULT_SAVE_SLOT
  if safeSlot == "" then
    safeSlot = DEFAULT_SAVE_SLOT
  end
  update_loading("Loading checkpoint " .. safeSlot, 0.98)
  local loaded = java.callModule("engine.save", "load", { slot = safeSlot })
  status("Loaded checkpoint: " .. safeSlot .. ".")
  enter_gameplay("lua-ui-load-save")
  return {
    ok = loaded.ok ~= false,
    handled = true,
    operation = "loadSlot",
    slot = safeSlot,
    runtime = runtimeStart,
    load = loaded
  }
end

local function load_slot(slot)
  local safeSlot = slot or DEFAULT_SAVE_SLOT
  if safeSlot == "" then
    safeSlot = DEFAULT_SAVE_SLOT
  end
  local runtimeOk, runtimeStart = start_game_runtime("lua-ui-load-save", "loadSlot", safeSlot)
  if not runtimeOk then
    gate_menu(true, "lua-ui-load-save-runtime-failed")
    return { ok = false, handled = true, operation = "loadSlot", slot = safeSlot, runtime = runtimeStart }
  end
  if runtimeStart.duplicate and runtimeStart.busy then
    return {
      ok = true,
      handled = true,
      operation = "loadSlot",
      slot = safeSlot,
      runtime = runtimeStart,
      duplicate = true,
      pending = true
    }
  end
  if runtimeStart.alreadyStarted then
    return do_load_slot(safeSlot, runtimeStart)
  end
  status("Loading runtime before checkpoint: " .. safeSlot .. ".")
  return { ok = true, handled = true, operation = "loadSlot", slot = safeSlot, runtime = runtimeStart, pending = true }
end

local handlers = {}

handlers["ui.menu.newGame"] = function(_)
  status("Starting expedition runtime...")
  local runtimeOk, runtimeStart = start_game_runtime("lua-ui-new-game", "newGame", nil)
  if not runtimeOk then
    gate_menu(true, "lua-ui-new-game-runtime-failed")
    return { ok = false, handled = true, operation = "newGame", runtime = runtimeStart }
  end
  if runtimeStart.duplicate and runtimeStart.busy then
    return {
      ok = true,
      handled = true,
      operation = "newGame",
      runtime = runtimeStart,
      duplicate = true,
      pending = true
    }
  end
  if runtimeStart.alreadyStarted then
    local spawn = java.callModule("engine.world", "spawnPlayer", {})
    if spawn.spawned then
      status("Expedition started.")
      enter_gameplay("lua-ui-new-game")
      return { ok = true, handled = true, operation = "newGame", runtime = runtimeStart, spawn = spawn }
    end
    update_loading("Waiting for terrain collision", 0.98)
    status("Terrain preparing. Expedition will enter when spawn is ready.")
    return { ok = false, handled = true, operation = "newGame", runtime = runtimeStart, spawn = spawn, waiting = "terrain" }
  end
  status("Runtime loading. Expedition will start automatically.")
  return { ok = true, handled = true, operation = "newGame", runtime = runtimeStart, pending = true }
end

handlers["ui.menu.continue"] = function(_)
  local saves = save_list()
  local latest = latest_save(saves)
  if latest == nil then
    status("No saved progress. Start a new expedition.")
    gate_menu(true, "lua-ui-continue-no-saves")
    return { ok = false, handled = true, operation = "continue", message = "no saves" }
  end
  return load_slot(latest.slot)
end

handlers["ui.menu.refreshSaves"] = function(_)
  refresh_saves()
  status("Save list refreshed.")
  return { ok = true, handled = true, operation = "refreshSaves" }
end

handlers["ui.menu.loadSlot"] = function(payload)
  return load_slot(payload.slot)
end

handlers["ui.menu.save"] = function(payload)
  local safeSlot = payload.slot or DEFAULT_SAVE_SLOT
  if safeSlot == "" then
    safeSlot = DEFAULT_SAVE_SLOT
  end
  local saved = java.callModule("engine.save", "save", { slot = safeSlot })
  refresh_saves()
  status("Saved checkpoint: " .. safeSlot .. ".")
  return { ok = saved.ok ~= false, handled = true, operation = "save", slot = safeSlot, save = saved }
end

handlers["ui.menu.overwriteSave"] = function(payload)
  local safeSlot = payload.slot or DEFAULT_SAVE_SLOT
  if safeSlot == "" then
    safeSlot = DEFAULT_SAVE_SLOT
  end
  local overwritten = java.callModule("engine.save", "overwrite", { slot = safeSlot })
  refresh_saves()
  if overwritten.ok == false then
    status("Cannot overwrite missing checkpoint: " .. safeSlot .. ".")
  else
    status("Checkpoint overwritten: " .. safeSlot .. ".")
  end
  return {
    ok = overwritten.ok ~= false,
    handled = true,
    operation = "overwriteSave",
    slot = safeSlot,
    overwrite = overwritten
  }
end

handlers["ui.menu.deleteSave"] = function(payload)
  local safeSlot = payload.slot or DEFAULT_SAVE_SLOT
  if safeSlot == "" then
    safeSlot = DEFAULT_SAVE_SLOT
  end
  local deleted = java.callModule("engine.save", "delete", { slot = safeSlot })
  refresh_saves()
  if deleted.ok == false then
    status("Checkpoint was already missing: " .. safeSlot .. ".")
  else
    status("Checkpoint deleted: " .. safeSlot .. ".")
  end
  return {
    ok = deleted.ok ~= false,
    handled = true,
    operation = "deleteSave",
    slot = safeSlot,
    delete = deleted
  }
end

handlers["ui.menu.resume"] = function(_)
  enter_gameplay("lua-ui-resume")
  return { ok = true, handled = true, operation = "resume" }
end

handlers["ui.menu.settings"] = function(payload)
  local returnScreen = payload.screen or "main-menu"
  local opened = ui.call("settings.open", {
    returnScreen = returnScreen,
    reason = "lua-ui-settings-open"
  })
  status("Select a category.")
  return {
    ok = opened.ok ~= false,
    handled = true,
    operation = "settings",
    returnScreen = returnScreen,
    screen = opened.screen
  }
end

handlers["ui.menu.settingsBack"] = function(_)
  local closed = ui.call("settings.close", {
    reason = "lua-ui-settings-back"
  })
  status("Returned to the previous menu.")
  return {
    ok = closed.ok ~= false,
    handled = true,
    operation = "settingsBack",
    screen = closed.screen
  }
end

handlers["ui.menu.settings.category"] = function(payload)
  local category = payload.category or "overview"
  local changed = ui.call("settings.section", { section = category })
  status(title_case(category) .. " settings.")
  return {
    ok = changed.ok ~= false,
    handled = true,
    operation = "settingsCategory",
    category = changed.section or category
  }
end

handlers["ui.menu.settings.overview"] = function(_)
  local changed = ui.call("settings.section", { section = "overview" })
  status("Select a category.")
  return {
    ok = changed.ok ~= false,
    handled = true,
    operation = "settingsOverview"
  }
end

handlers["ui.menu.settings.graphicsPreset"] = function(payload)
  local preset = string.upper(tostring(payload.preset or "HIGH"))
  local response = ui.call("settings.graphics.select", {
    preset = preset
  })
  if response.applied then
    status("Graphics preset applied: " .. title_case(response.preset or preset) .. ".")
  else
    status("Graphics preset selected: " .. title_case(response.preset or preset)
      .. ". It will apply when the expedition starts.")
  end
  return {
    ok = response.ok ~= false,
    handled = true,
    operation = "graphicsPreset",
    preset = response.preset or preset,
    applied = response.applied,
    deferred = response.deferred,
    response = response
  }
end

handlers["ui.menu.settings.audioVolume"] = function(payload)
  local volume = tonumber(payload.volume or 1.0) or 1.0
  local response = ui.call("audio.volume.set", { volume = volume })
  local applied = response.ok ~= false and response.available ~= false
  if applied then
    local percent = math.floor((response.volume or volume) * 100 + 0.5)
    status("Master volume: " .. tostring(percent) .. "%.")
  else
    status("Audio output is not available.")
  end
  return {
    ok = applied,
    handled = true,
    operation = "audioVolume",
    volume = response.volume or volume
  }
end

handlers["ui.menu.settings.mouseSensitivity"] = function(payload)
  local sensitivity = tonumber(payload.sensitivity or 1.0) or 1.0
  local response = ui.call("settings.controls.sensitivity.select", {
    sensitivity = sensitivity
  })
  if response.applied then
    status("Mouse sensitivity: " .. tostring(response.sensitivity or sensitivity) .. ".")
  else
    status("Mouse sensitivity selected: "
      .. tostring(response.sensitivity or sensitivity)
      .. ". It will apply when the player is ready.")
  end
  return {
    ok = response.ok ~= false,
    handled = true,
    operation = "mouseSensitivity",
    sensitivity = response.sensitivity or sensitivity,
    applied = response.applied,
    deferred = response.deferred,
    response = response
  }
end

handlers["ui.menu.mainMenu"] = function(_)
  ui.call("screen.set", { screen = "main-menu" })
  refresh_saves()
  gate_menu(true, "lua-ui-main-menu")
  status("Main menu opened. Gameplay input is paused.")
  return { ok = true, handled = true, operation = "mainMenu" }
end

handlers["runtime.game.starting"] = function(payload)
  show_loading("Preparing expedition runtime", 0.02)
  return { ok = true, handled = true, operation = "runtimeGameStarting", runtime = payload }
end

handlers["runtime.game.module.loading"] = function(payload)
  update_loading(payload.stage or ("Loading " .. tostring(payload.module or "runtime module")), payload.progress or 0.05)
  return { ok = true, handled = true, operation = "runtimeGameModuleLoading", runtime = payload }
end

handlers["runtime.game.module.installing"] = function(payload)
  update_loading(payload.stage or ("Installing " .. tostring(payload.module or "runtime module")), payload.progress or 0.05)
  return { ok = true, handled = true, operation = "runtimeGameModuleInstalling", runtime = payload }
end

handlers["runtime.game.module.loaded"] = function(payload)
  update_loading("Loaded " .. tostring(payload.module or "runtime module"), payload.progress or 0.9)
  return { ok = true, handled = true, operation = "runtimeGameModuleLoaded", runtime = payload }
end

handlers["runtime.game.ready"] = function(payload)
  ui.call("settings.graphics.applyPending", {})
  local request = payload.request or {}
  local operation = request.operation or "newGame"
  update_loading("Preparing expedition scene", 0.96)
  if operation == "loadSlot" then
    return do_load_slot(request.slot or DEFAULT_SAVE_SLOT, payload)
  end
  local spawn = java.callModule("engine.world", "spawnPlayer", {})
  if spawn.spawned then
    status("Expedition started.")
    enter_gameplay("lua-ui-runtime-ready-new-game")
    return { ok = true, handled = true, operation = "runtimeReadyNewGame", runtime = payload, spawn = spawn }
  end
  update_loading("Waiting for terrain collision", 0.98)
  status("Terrain preparing. Expedition will enter when spawn is ready.")
  return { ok = false, handled = true, operation = "runtimeReadyNewGame", runtime = payload, spawn = spawn, waiting = "terrain" }
end

handlers["runtime.game.failed"] = function(payload)
  update_loading("Game runtime failed to start", 1.0)
  status("Game runtime failed to start.")
  gate_menu(true, "lua-ui-runtime-game-failed")
  return { ok = false, handled = true, operation = "runtimeGameFailed", runtime = payload }
end

handlers["player.spawned"] = function(payload)
  ui.call("settings.controls.applyPending", {})
  update_loading("Expedition ready", 1.0)
  status("Expedition started.")
  enter_gameplay("lua-ui-player-spawned")
  return { ok = true, handled = true, operation = "playerSpawned", payload = payload }
end

handlers["ui.menu.exitToDesktop"] = function(_)
  status("Stopping runtime.")
  return ui.call("runtime.exit", { reason = "lua-ui-exit-to-desktop" })
end

local handler = handlers[topic]
if handler == nil then
  return { ok = true, handled = false, topic = topic }
end

return handler(payload)
