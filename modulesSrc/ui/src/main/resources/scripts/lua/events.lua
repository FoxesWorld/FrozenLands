local M = { id = "frozenlands.ui" }

M.topics = {
  prefix = "ui.*",
  menuPrefix = "ui.menu.*",
  newGame = "ui.menu.newGame",
  continueGame = "ui.menu.continue",
  refreshSaves = "ui.menu.refreshSaves",
  loadSlot = "ui.menu.loadSlot",
  save = "ui.menu.save",
  overwriteSave = "ui.menu.overwriteSave",
  deleteSave = "ui.menu.deleteSave",
  resume = "ui.menu.resume",
  settings = "ui.menu.settings",
  settingsBack = "ui.menu.settingsBack",
  settingsCategory = "ui.menu.settings.category",
  settingsOverview = "ui.menu.settings.overview",
  settingsGraphicsPreset = "ui.menu.settings.graphicsPreset",
  settingsAudioVolume = "ui.menu.settings.audioVolume",
  settingsMouseSensitivity = "ui.menu.settings.mouseSensitivity",
  mainMenu = "ui.menu.mainMenu",
  exitToDesktop = "ui.menu.exitToDesktop",
  mounted = "ui.html.mounted",
  screenChanged = "ui.html.screen.changed",
  actionRequested = "ui.action.requested",
  actionDispatched = "ui.action.dispatched",
  actionRejected = "ui.action.rejected",
  operationRequested = "engine.operation.requested",
  operationStarted = "engine.operation.started",
  operationProgress = "engine.operation.progress",
  operationCompleted = "engine.operation.completed",
  operationFailed = "engine.operation.failed",
  operationCancelled = "engine.operation.cancelled",
  operationRejected = "engine.operation.rejected"
}

return M
